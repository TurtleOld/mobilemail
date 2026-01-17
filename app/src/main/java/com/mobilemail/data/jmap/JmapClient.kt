package com.mobilemail.data.jmap

import android.util.Log
import com.mobilemail.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class JmapClient(
    private val baseUrl: String,
    private val email: String,
    private val password: String,
    private val accountId: String
) {
    companion object {
        // Статический кэш клиентов для переиспользования
        private val clientCache = mutableMapOf<String, JmapClient>()
        private val lock = Any()
        
        fun getOrCreate(baseUrl: String, email: String, password: String, accountId: String): JmapClient {
            val key = "$baseUrl:$email:$accountId"
            synchronized(lock) {
                return clientCache.getOrPut(key) {
                    JmapClient(baseUrl, email, password, accountId)
                }
            }
        }
        
        fun clearCache() {
            synchronized(lock) {
                clientCache.clear()
            }
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private var session: JmapSession? = null
    private val sessionCache = mutableMapOf<String, Pair<JmapSession, Long>>()
    private val SESSION_CACHE_TTL = 5 * 60 * 1000L

    private fun getAuthHeader(): String {
        val credentials = "$email:$password"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }

    private fun getEffectiveApiUrl(sessionApiUrl: String): String {
        try {
            val baseUri = java.net.URI(baseUrl.trimEnd('/'))
            val sessionUri = java.net.URI(sessionApiUrl)
            
            if (baseUri.host != sessionUri.host || baseUri.port != sessionUri.port) {
                val normalizedBaseUrl = baseUrl.trimEnd('/')
                val effectiveUrl = "$normalizedBaseUrl/jmap"
                Log.d("JmapClient", "Используется baseUrl вместо apiUrl из сессии: $effectiveUrl (сессия: $sessionApiUrl)")
                return effectiveUrl
            }
            return sessionApiUrl
        } catch (e: Exception) {
            Log.w("JmapClient", "Ошибка парсинга URL, используем baseUrl", e)
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            return "$normalizedBaseUrl/jmap"
        }
    }

    suspend fun getSession(): JmapSession {
        val cacheKey = "$accountId:${getAuthHeader()}"
        val cached = sessionCache[cacheKey]
        
        if (cached != null && cached.second > System.currentTimeMillis()) {
            Log.d("JmapClient", "Использование кэшированной сессии")
            return cached.first
        }

        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val sessionUrl = "$normalizedBaseUrl/jmap/session"
        
        Log.d("JmapClient", "Попытка подключения к: $sessionUrl")
        Log.d("JmapClient", "Email: $email, AccountId: $accountId")
        
        val request = Request.Builder()
            .url(sessionUrl)
            .header("Accept", "application/json")
            .header("Authorization", getAuthHeader())
            .get()
            .build()

        val (response, responseBody) = withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            repeat(3) { attempt ->
                try {
                    val resp = client.newCall(request).execute()
                    val body = resp.body?.string() ?: ""
                    return@withContext Pair(resp, body)
                } catch (e: Exception) {
                    lastException = e
                    val isEofError = e is java.io.EOFException || 
                                    e.message?.contains("unexpected end of stream", ignoreCase = true) == true ||
                                    e.message?.contains("\\n not found", ignoreCase = true) == true
                    
                    if (isEofError && attempt < 2) {
                        Log.w("JmapClient", "Попытка ${attempt + 1}: неожиданное закрытие соединения, повтор через ${(attempt + 1) * 500}ms...")
                        kotlinx.coroutines.delay((attempt + 1) * 500L)
                        return@repeat
                    }
                    Log.e("JmapClient", "Ошибка подключения к $sessionUrl (попытка ${attempt + 1})", e)
                    if (attempt == 2) {
                        throw Exception("Не удалось подключиться к серверу после 3 попыток: ${e.message}", e)
                    }
                }
            }
            throw Exception("Не удалось подключиться к серверу: ${lastException?.message}", lastException)
        }
        
        Log.d("JmapClient", "Ответ от /jmap/session: код ${response.code}, успешно: ${response.isSuccessful}")
        
        if (!response.isSuccessful) {
            Log.w("JmapClient", "Ошибка получения сессии (код ${response.code}): $responseBody")
            Log.d("JmapClient", "Попытка discovery через /.well-known/jmap")
            val discoveryUrl = "$normalizedBaseUrl/.well-known/jmap"
            val discoveryRequest = Request.Builder()
                .url(discoveryUrl)
                .header("Accept", "application/json")
                .header("Authorization", getAuthHeader())
                .get()
                .build()
            
            val (discoveryResponse, discoveryResponseBody) = withContext(Dispatchers.IO) {
                try {
                    val resp = client.newCall(discoveryRequest).execute()
                    val body = resp.body?.string() ?: ""
                    Pair(resp, body)
                } catch (e: Exception) {
                    Log.e("JmapClient", "Ошибка discovery запроса к $discoveryUrl", e)
                    throw Exception("Не удалось выполнить discovery: ${e.message}", e)
                }
            }
            
            Log.d("JmapClient", "Discovery ответ: код ${discoveryResponse.code}")
            
            if (!discoveryResponse.isSuccessful) {
                Log.e("JmapClient", "Discovery failed (код ${discoveryResponse.code}): $discoveryResponseBody")
                throw Exception("JMAP discovery failed: код ${discoveryResponse.code}, ответ: $discoveryResponseBody")
            }
            
            val discoveryJson = JSONObject(discoveryResponseBody)
            val apiUrl = discoveryJson.optString("apiUrl", "$normalizedBaseUrl/jmap")
            
            val sessionRequestJson = JSONObject().apply {
                put("using", JSONArray(listOf(
                    "urn:ietf:params:jmap:core",
                    "urn:ietf:params:jmap:mail"
                )))
                put("methodCalls", JSONArray(listOf(
                    JSONArray(listOf("Session/get", JSONObject(), "0"))
                )))
            }
            
            val sessionRequestBody = sessionRequestJson.toString()
                .toRequestBody("application/json".toMediaType())
            
            val sessionRequest = Request.Builder()
                .url(apiUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthHeader())
                .post(sessionRequestBody)
                .build()
            
            val (sessionResponse, sessionResponseBody) = withContext(Dispatchers.IO) {
                try {
                    val resp = client.newCall(sessionRequest).execute()
                    val body = resp.body?.string() ?: ""
                    Pair(resp, body)
                } catch (e: Exception) {
                    Log.e("JmapClient", "Ошибка запроса сессии к $apiUrl", e)
                    throw Exception("Не удалось получить сессию: ${e.message}", e)
                }
            }
            
            Log.d("JmapClient", "Ответ сессии: код ${sessionResponse.code}")
            
            if (!sessionResponse.isSuccessful) {
                Log.e("JmapClient", "Ошибка получения сессии (код ${sessionResponse.code}): $sessionResponseBody")
                throw Exception("JMAP session failed: код ${sessionResponse.code}, ответ: $sessionResponseBody")
            }
            
            val sessionResponseJson = JSONObject(sessionResponseBody)
            val methodResponses = sessionResponseJson.getJSONArray("methodResponses")
            val sessionResponseData = methodResponses.getJSONArray(0)
            
            if (sessionResponseData.getString(0) != "Session/get") {
                throw Exception("Invalid session response")
            }
            
            val sessionData = sessionResponseData.getJSONObject(1)
            session = parseSession(sessionData)
        } else {
            Log.d("JmapClient", "Получен ответ сессии: ${responseBody.take(500)}...")
            try {
                val sessionJson = JSONObject(responseBody)
                val keys = mutableListOf<String>()
                sessionJson.keys().forEach { keys.add(it) }
                Log.d("JmapClient", "JSON ключи: ${keys.joinToString()}")
                session = parseSession(sessionJson)
                Log.d("JmapClient", "Сессия успешно получена, API URL: ${session?.apiUrl}")
            } catch (e: Exception) {
                Log.e("JmapClient", "Ошибка парсинга сессии. Полный ответ: $responseBody", e)
                throw e
            }
        }

        session?.let {
            sessionCache[cacheKey] = Pair(it, System.currentTimeMillis() + SESSION_CACHE_TTL)
            Log.d("JmapClient", "Сессия сохранена в кэш")
        }

        return session ?: throw Exception("Failed to get session: сессия не была создана")
    }

    private fun parseSession(json: JSONObject): JmapSession {
        val accountsJson = json.optJSONObject("accounts") ?: JSONObject()
        val accounts = mutableMapOf<String, JmapAccount>()
        
        accountsJson.keys().forEach { accountId ->
            val accountJson = accountsJson.optJSONObject(accountId)
            if (accountJson != null) {
                accounts[accountId] = JmapAccount(
                    id = accountId,
                    name = accountJson.optString("name", accountId),
                    isPersonal = accountJson.optBoolean("isPersonal", true),
                    isReadOnly = accountJson.optBoolean("isReadOnly", false),
                    accountCapabilities = null
                )
            } else {
                Log.w("JmapClient", "Аккаунт $accountId не является объектом, пропускаем")
            }
        }
        
        Log.d("JmapClient", "Распарсено аккаунтов: ${accounts.size}")

        val primaryAccountsJson = json.optJSONObject("primaryAccounts")
        val primaryAccounts = primaryAccountsJson?.let {
            PrimaryAccounts(mail = it.optString("mail", null))
        }

        return JmapSession(
            apiUrl = json.getString("apiUrl"),
            downloadUrl = json.getString("downloadUrl"),
            uploadUrl = json.getString("uploadUrl"),
            eventSourceUrl = json.optString("eventSourceUrl", null),
            accounts = accounts,
            primaryAccounts = primaryAccounts,
            capabilities = null
        )
    }

    suspend fun getMailboxes(accountId: String? = null): List<JmapMailbox> {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val methodCallArray = JSONArray().apply {
            put("Mailbox/get")
            put(JSONObject().apply {
                put("accountId", targetAccountId)
            })
            put("0")
        }
        
        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
            })
            put("methodCalls", JSONArray().apply {
                put(methodCallArray)
            })
        }

        val apiUrl = if (session.apiUrl.startsWith("http://pavlovteam.ru") || session.apiUrl.startsWith("https://pavlovteam.ru")) {
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            "$normalizedBaseUrl/jmap"
        } else {
            session.apiUrl
        }
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val mailboxResponse = methodResponses.getJSONArray(0)

        if (mailboxResponse.getString(0) != "Mailbox/get") {
            throw Exception("Invalid mailbox response")
        }

        val mailboxData = mailboxResponse.getJSONObject(1)
        val list = mailboxData.getJSONArray("list")
        val mailboxes = mutableListOf<JmapMailbox>()

        for (i in 0 until list.length()) {
            val mailboxJson = list.getJSONObject(i)
            mailboxes.add(JmapMailbox(
                id = mailboxJson.getString("id"),
                name = mailboxJson.getString("name"),
                parentId = mailboxJson.optString("parentId", null),
                role = mailboxJson.optString("role", null),
                sortOrder = mailboxJson.optInt("sortOrder", 0),
                totalEmails = mailboxJson.optInt("totalEmails", 0),
                unreadEmails = mailboxJson.optInt("unreadEmails", 0),
                totalThreads = mailboxJson.optInt("totalThreads", 0),
                unreadThreads = mailboxJson.optInt("unreadThreads", 0)
            ))
        }

        return mailboxes
    }

    suspend fun queryEmails(
        mailboxId: String,
        accountId: String? = null,
        position: Int = 0,
        limit: Int = 50,
        filter: Map<String, Any>? = null
    ): EmailQueryResult {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val filterJson = filter?.let { JSONObject(it) } ?: JSONObject().apply {
            put("inMailbox", mailboxId)
        }

        val sortArray = JSONArray().apply {
            put(JSONObject().apply {
                put("property", "receivedAt")
                put("isAscending", false)
            })
        }
        
        val queryParams = JSONObject().apply {
            put("accountId", targetAccountId)
            put("filter", filterJson)
            put("sort", sortArray)
            put("position", position)
            put("limit", limit)
        }
        
        val methodCallArray = JSONArray().apply {
            put("Email/query")
            put(queryParams)
            put("0")
        }
        
        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
            })
            put("methodCalls", JSONArray().apply {
                put(methodCallArray)
            })
        }

        val apiUrl = if (session.apiUrl.startsWith("http://pavlovteam.ru") || session.apiUrl.startsWith("https://pavlovteam.ru")) {
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            "$normalizedBaseUrl/jmap"
        } else {
            session.apiUrl
        }
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val queryResponse = methodResponses.getJSONArray(0)

        if (queryResponse.getString(0) != "Email/query") {
            throw Exception("Invalid email query response")
        }

        val queryData = queryResponse.getJSONObject(1)
        val ids = mutableListOf<String>()
        val idsArray = queryData.getJSONArray("ids")
        
        for (i in 0 until idsArray.length()) {
            ids.add(idsArray.getString(i))
        }

        return EmailQueryResult(
            ids = ids,
            position = queryData.getInt("position"),
            total = if (queryData.has("total") && !queryData.isNull("total")) {
                queryData.getInt("total")
            } else {
                null
            },
            queryState = queryData.optString("queryState", null)
        )
    }

    suspend fun getEmails(
        ids: List<String>,
        accountId: String? = null,
        properties: List<String>? = null
    ): List<JmapEmail> {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val defaultProperties = listOf(
            "id", "threadId", "mailboxIds", "keywords", "size",
            "receivedAt", "hasAttachment", "preview", "subject",
            "from", "to", "cc", "bcc", "bodyStructure", "bodyValues",
            "textBody", "htmlBody"
        )

        val idsArray = JSONArray().apply {
            ids.forEach { put(it) }
        }
        
        val propertiesArray = JSONArray().apply {
            (properties ?: defaultProperties).forEach { put(it) }
        }
        
        val getParams = JSONObject().apply {
            put("accountId", targetAccountId)
            put("ids", idsArray)
            put("properties", propertiesArray)
            // Запрашиваем содержимое тела письма
            put("fetchTextBodyValues", true)
            put("fetchHTMLBodyValues", true)
        }
        
        val methodCallArray = JSONArray().apply {
            put("Email/get")
            put(getParams)
            put("0")
        }
        
        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
            })
            put("methodCalls", JSONArray().apply {
                put(methodCallArray)
            })
        }

        val apiUrl = if (session.apiUrl.startsWith("http://pavlovteam.ru") || session.apiUrl.startsWith("https://pavlovteam.ru")) {
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            "$normalizedBaseUrl/jmap"
        } else {
            session.apiUrl
        }
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val getResponse = methodResponses.getJSONArray(0)

        if (getResponse.getString(0) != "Email/get") {
            throw Exception("Invalid email get response")
        }

        val emailData = getResponse.getJSONObject(1)
        val list = emailData.getJSONArray("list")
        Log.d("JmapClient", "Получено писем в ответе: ${list.length()}")
        val emails = mutableListOf<JmapEmail>()

        for (i in 0 until list.length()) {
            try {
                val emailJson = list.getJSONObject(i)
                val emailId = emailJson.optString("id", "unknown")
                val bodyValuesObj = emailJson.optJSONObject("bodyValues")
                val bodyValuesCount = bodyValuesObj?.let { 
                    var count = 0
                    it.keys().forEach { count++ }
                    count
                } ?: 0
                Log.d("JmapClient", "Парсинг письма $emailId: to=${emailJson.optJSONArray("to")?.length() ?: 0}, bodyValues=$bodyValuesCount")
                emails.add(parseEmail(emailJson))
            } catch (e: Exception) {
                Log.e("JmapClient", "Ошибка парсинга письма $i", e)
            }
        }

        Log.d("JmapClient", "Успешно распарсено писем: ${emails.size}")
        return emails
    }

    private fun parseEmail(json: JSONObject): JmapEmail {
        fun parseEmailAddresses(array: JSONArray?): List<EmailAddress>? {
            if (array == null) return null
            val addresses = mutableListOf<EmailAddress>()
            for (i in 0 until array.length()) {
                val addrJson = array.getJSONObject(i)
                // Обрабатываем строку "null" как null значение
                val nameValue = addrJson.optString("name", null)
                val name = if (nameValue == null || nameValue == "null" || nameValue.isBlank()) null else nameValue
                val emailValue = addrJson.optString("email", null)
                val email = if (emailValue == null || emailValue == "null" || emailValue.isBlank()) "unknown" else emailValue
                addresses.add(EmailAddress(
                    name = name,
                    email = email
                ))
            }
            return addresses
        }

        return JmapEmail(
            id = json.getString("id"),
            threadId = json.getString("threadId"),
            mailboxIds = json.optJSONObject("mailboxIds")?.let { obj ->
                val map = mutableMapOf<String, Boolean>()
                obj.keys().forEach { key ->
                    map[key] = obj.getBoolean(key)
                }
                map
            } ?: emptyMap(),
            keywords = json.optJSONObject("keywords")?.let { obj ->
                val map = mutableMapOf<String, Boolean>()
                obj.keys().forEach { key ->
                    map[key] = obj.getBoolean(key)
                }
                map
            },
            size = json.optLong("size", 0),
            receivedAt = json.getString("receivedAt"),
            hasAttachment = json.optBoolean("hasAttachment", false),
            preview = json.optString("preview", null),
            subject = json.optString("subject", null),
            from = parseEmailAddresses(json.optJSONArray("from")),
            to = parseEmailAddresses(json.optJSONArray("to")),
            cc = parseEmailAddresses(json.optJSONArray("cc")),
            bcc = parseEmailAddresses(json.optJSONArray("bcc")),
            bodyStructure = json.opt("bodyStructure"),
            bodyValues = json.optJSONObject("bodyValues")?.let { obj ->
                val map = mutableMapOf<String, BodyValue>()
                obj.keys().forEach { key ->
                    val valueJson = obj.getJSONObject(key)
                    map[key] = BodyValue(
                        value = valueJson.getString("value"),
                        isEncodingProblem = valueJson.optBoolean("isEncodingProblem", false),
                        isTruncated = valueJson.optBoolean("isTruncated", false)
                    )
                }
                map
            },
            textBody = json.optJSONArray("textBody")?.let { array ->
                val list = mutableListOf<BodyPart>()
                for (i in 0 until array.length()) {
                    val partJson = array.getJSONObject(i)
                    list.add(BodyPart(
                        partId = partJson.getString("partId"),
                        type = partJson.getString("type")
                    ))
                }
                list
            },
            htmlBody = json.optJSONArray("htmlBody")?.let { array ->
                val list = mutableListOf<BodyPart>()
                for (i in 0 until array.length()) {
                    val partJson = array.getJSONObject(i)
                    list.add(BodyPart(
                        partId = partJson.getString("partId"),
                        type = partJson.getString("type")
                    ))
                }
                list
            }
        )
    }

    private suspend fun makeRequest(url: String, requestJson: JSONObject): JSONObject {
        var requestBodyString = requestJson.toString()
        requestBodyString = requestBodyString.replace("\\/", "/")
        Log.d("JmapClient", "Запрос к: $url")
        Log.d("JmapClient", "Полное тело запроса: $requestBodyString")
        
        val requestBody = requestBodyString
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", getAuthHeader())
            .post(requestBody)
            .build()

        val (response, responseBody) = withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(request).execute()
                val body = resp.body?.string() ?: "{}"
                Pair(resp, body)
            } catch (e: Exception) {
                Log.e("JmapClient", "Ошибка выполнения запроса к $url", e)
                throw Exception("Ошибка сети: ${e.message}", e)
            }
        }

        Log.d("JmapClient", "Ответ: код ${response.code}")

        if (!response.isSuccessful) {
            Log.e("JmapClient", "Ошибка запроса (код ${response.code}): $responseBody")
            throw Exception("JMAP request failed: код ${response.code}, сообщение: ${response.message}, ответ: ${responseBody.take(200)}")
        }

        Log.d("JmapClient", "Успешный ответ: ${responseBody.take(200)}...")
        return JSONObject(responseBody)
    }
}
