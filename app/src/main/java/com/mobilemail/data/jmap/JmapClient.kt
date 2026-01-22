package com.mobilemail.data.jmap

import android.util.Log
import com.mobilemail.data.model.BodyPart
import com.mobilemail.data.model.BodyValue
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.EmailQueryResult
import com.mobilemail.data.model.JmapAccount
import com.mobilemail.data.model.JmapEmail
import com.mobilemail.data.model.JmapMailbox
import com.mobilemail.data.model.JmapSession
import com.mobilemail.data.model.PrimaryAccounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class TwoFactorRequiredException(message: String) : Exception(message)

class JmapClient(
    private val baseUrl: String,
    private val email: String,
    private val password: String,
    private val accountId: String
) : JmapApi {
    companion object {
        private val clientCache = mutableMapOf<String, JmapClient>()
        private val lock = Any()
        
        fun getOrCreate(baseUrl: String, email: String, password: String, accountId: String): JmapClient {
            val key = "$baseUrl:$email"
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
        .connectionPool(ConnectionPool(2, 2, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private var session: JmapSession? = null
    private val sessionCache = mutableMapOf<String, Pair<JmapSession, Long>>()
    private val sessionCacheTtl = 5 * 60 * 1000L
    
    private val sessionMutex = Mutex()
    private val requestSemaphore = Semaphore(permits = 2)
    private var isFirstLaunch = true
    @Volatile private var totpCode: String? = null

    fun setTotpCode(code: String) {
        Log.d("JmapClient", "Установка TOTP кода для последующих запросов")
        totpCode = code
        session = null
        sessionCache.clear()
    }
    
    fun clearTotpCode() {
        totpCode = null
        session = null
        sessionCache.clear()
    }

    private fun getAuthHeader(): String {
        val code = totpCode
        val credentials = if (code != null) {
            Log.d("JmapClient", "Использование TOTP кода в заголовке авторизации")
            "$email:$password$$code"
        } else {
            "$email:$password"
        }
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }
    
    private fun getAuthHeaderWithTotp(totpCode: String): String {
        val credentials = "$email:$password$$totpCode"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }
    
    private fun checkTwoFactorRequired(response: okhttp3.Response, responseBody: String): Boolean {
        if (response.code == 401 || response.code == 402) {
            val wwwAuthenticate = response.header("WWW-Authenticate", "")
            if (wwwAuthenticate?.contains("two-factor", ignoreCase = true) == true ||
                wwwAuthenticate?.contains("totp", ignoreCase = true) == true ||
                wwwAuthenticate?.contains("2fa", ignoreCase = true) == true) {
                return true
            }
            
            try {
                val json = JSONObject(responseBody)
                if (json.has("requiresTwoFactor") && json.getBoolean("requiresTwoFactor")) {
                    return true
                }
                if (json.has("twoFactorRequired") && json.getBoolean("twoFactorRequired")) {
                    return true
                }
                val title = json.optString("title", "")
                if (title.contains("TOTP code required", ignoreCase = true) ||
                    title.contains("totp", ignoreCase = true)) {
                    return true
                }
                val detail = json.optString("detail", "")
                if (detail.contains("TOTP code", ignoreCase = true) ||
                    detail.contains("totp", ignoreCase = true)) {
                    return true
                }
                if (json.has("error") && json.getString("error").contains("two-factor", ignoreCase = true)) {
                    return true
                }
            } catch (e: Exception) {
            }
        }
        return false
    }

    suspend fun getSessionWithTotp(totpCode: String): JmapSession = sessionMutex.withLock {
        Log.d("JmapClient", "getSessionWithTotp вызван для accountId: $accountId")
        val cacheKey = "$accountId:${getAuthHeaderWithTotp(totpCode)}"
        val cached = sessionCache[cacheKey]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            Log.d("JmapClient", "Используется кэшированная сессия с TOTP")
            return cached.first
        }
        
        Log.d("JmapClient", "Запрос новой сессии с TOTP кодом с URL: $baseUrl")
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val sessionUrl = "$normalizedBaseUrl/jmap/session"
        Log.d("JmapClient", "Session URL: $sessionUrl")
        
        val request = Request.Builder()
            .url(sessionUrl)
            .header("Accept", "application/json")
            .header("Authorization", getAuthHeaderWithTotp(totpCode))
            .get()
            .build()

        val (response, responseBody) = withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            repeat(3) { attempt ->
                try {
                    val resp = client.newCall(request).execute()
                    val body = try {
                        resp.body?.string() ?: ""
                    } catch (e: java.io.EOFException) {
                        Log.w("JmapClient", "EOFException при чтении ответа, попытка $attempt")
                        if (attempt < 2) {
                            delay((attempt + 1) * 1000L)
                            throw e
                        } else {
                            ""
                        }
                    }
                    return@withContext Pair(resp, body)
                } catch (e: java.io.EOFException) {
                    Log.w("JmapClient", "EOFException при выполнении запроса, попытка ${attempt + 1}/3")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                } catch (e: java.io.IOException) {
                    Log.w("JmapClient", "IOException при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                } catch (e: Exception) {
                    Log.w("JmapClient", "Ошибка при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                }
            }
            throw Exception("Не удалось подключиться к серверу: ${lastException?.message}", lastException)
        }
        
        if (!response.isSuccessful) {
            val discoveryUrl = "$normalizedBaseUrl/.well-known/jmap"
            val discoveryRequest = Request.Builder()
                .url(discoveryUrl)
                .header("Accept", "application/json")
                .header("Authorization", getAuthHeaderWithTotp(totpCode))
                .get()
                .build()
            
            val (discoveryResponse, discoveryBody) = withContext(Dispatchers.IO) {
                val resp = client.newCall(discoveryRequest).execute()
                val body = resp.body?.string() ?: ""
                Pair(resp, body)
            }
            
            if (!discoveryResponse.isSuccessful) {
                throw Exception("JMAP discovery failed: код ${discoveryResponse.code}, ответ: $discoveryBody")
            }
            
            val discoveryJson = JSONObject(discoveryBody)
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
                .header("Authorization", getAuthHeaderWithTotp(totpCode))
                .post(sessionRequestBody)
                .build()
            
            val (sessionResponse, sessionResponseBody) = withContext(Dispatchers.IO) {
                val resp = client.newCall(sessionRequest).execute()
                val body = resp.body?.string() ?: ""
                Pair(resp, body)
            }
            
            if (!sessionResponse.isSuccessful) {
                throw Exception("JMAP session failed: код ${sessionResponse.code}, ответ: $sessionResponseBody")
            }
            
            val sessionResponseJson = JSONObject(sessionResponseBody)
            val methodResponses = sessionResponseJson.getJSONArray("methodResponses")
            val sessionResponseData = methodResponses.getJSONArray(0)
            val sessionData = sessionResponseData.getJSONObject(1)
            session = parseSession(sessionData)
        } else {
            val sessionJson = JSONObject(responseBody)
            session = parseSession(sessionJson)
        }
        
        val created = session ?: throw Exception("Failed to get session")
        sessionCache[cacheKey] = Pair(created, System.currentTimeMillis() + sessionCacheTtl)
        setTotpCode(totpCode)
        return created
    }

    override suspend fun getSession(): JmapSession = sessionMutex.withLock {
        Log.d("JmapClient", "getSession вызван для accountId: $accountId")
        val cacheKey = "$accountId:${getAuthHeader()}"
        val cached = sessionCache[cacheKey]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            Log.d("JmapClient", "Используется кэшированная сессия")
            return cached.first
        }
        
        if (session != null) {
            Log.d("JmapClient", "Используется существующая сессия")
            sessionCache[cacheKey] = Pair(session!!, System.currentTimeMillis() + sessionCacheTtl)
            return session!!
        }
        
        Log.d("JmapClient", "Запрос новой сессии с URL: $baseUrl")
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val sessionUrl = "$normalizedBaseUrl/jmap/session"
        Log.d("JmapClient", "Session URL: $sessionUrl")
        
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
                    val body = try {
                        resp.body?.string() ?: ""
                    } catch (e: java.io.EOFException) {
                        Log.w("JmapClient", "EOFException при чтении ответа, попытка $attempt")
                        if (attempt < 2) {
                            delay((attempt + 1) * 1000L)
                            throw e
                        } else {
                            ""
                        }
                    }
                    return@withContext Pair(resp, body)
                } catch (e: java.io.EOFException) {
                    Log.w("JmapClient", "EOFException при выполнении запроса, попытка ${attempt + 1}/3")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                } catch (e: java.io.IOException) {
                    Log.w("JmapClient", "IOException при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                } catch (e: Exception) {
                    Log.w("JmapClient", "Ошибка при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                }
            }
            throw Exception("Не удалось подключиться к серверу: ${lastException?.message}", lastException)
        }
        
        if (!response.isSuccessful) {
            if (checkTwoFactorRequired(response, responseBody)) {
                throw TwoFactorRequiredException("Требуется двухфакторная авторизация")
            }
            
            val discoveryUrl = "$normalizedBaseUrl/.well-known/jmap"
            val discoveryRequest = Request.Builder()
                .url(discoveryUrl)
                .header("Accept", "application/json")
                .header("Authorization", getAuthHeader())
                .get()
                .build()
            
            val (discoveryResponse, discoveryBody) = withContext(Dispatchers.IO) {
                val resp = client.newCall(discoveryRequest).execute()
                val body = resp.body?.string() ?: ""
                Pair(resp, body)
            }
            
            if (!discoveryResponse.isSuccessful) {
                if (checkTwoFactorRequired(discoveryResponse, discoveryBody)) {
                    throw TwoFactorRequiredException("Требуется двухфакторная авторизация")
                }
                throw Exception("JMAP discovery failed: код ${discoveryResponse.code}, ответ: $discoveryBody")
            }
            
            val discoveryJson = JSONObject(discoveryBody)
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
                val resp = client.newCall(sessionRequest).execute()
                val body = resp.body?.string() ?: ""
                Pair(resp, body)
            }
            
            if (!sessionResponse.isSuccessful) {
                if (checkTwoFactorRequired(sessionResponse, sessionResponseBody)) {
                    throw TwoFactorRequiredException("Требуется двухфакторная авторизация")
                }
                throw Exception("JMAP session failed: код ${sessionResponse.code}, ответ: $sessionResponseBody")
            }
            
            val sessionResponseJson = JSONObject(sessionResponseBody)
            val methodResponses = sessionResponseJson.getJSONArray("methodResponses")
            val sessionResponseData = methodResponses.getJSONArray(0)
            val sessionData = sessionResponseData.getJSONObject(1)
            session = parseSession(sessionData)
        } else {
            val sessionJson = JSONObject(responseBody)
            session = parseSession(sessionJson)
        }
        
        val created = session ?: throw Exception("Failed to get session")
        sessionCache[cacheKey] = Pair(created, System.currentTimeMillis() + sessionCacheTtl)
        return created
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
            }
        }
        
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

    private fun getApiUrl(): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        return "$normalizedBaseUrl/jmap"
    }

    override suspend fun getMailboxes(accountId: String?): List<JmapMailbox> {
        Log.d("JmapClient", "getMailboxes вызван для accountId: $accountId")
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        Log.d("JmapClient", "Запрос Mailbox/get для accountId: $targetAccountId")
        val methodCallArray = JSONArray().apply {
            put("Mailbox/get")
            put(JSONObject().apply { put("accountId", targetAccountId) })
            put("0")
        }
        
        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
            })
            put("methodCalls", JSONArray().apply { put(methodCallArray) })
        }
        
        val apiUrl = getApiUrl()
        Log.d("JmapClient", "Отправка запроса Mailbox/get на $apiUrl")
        val response = makeRequest(apiUrl, requestJson)
        Log.d("JmapClient", "Получен ответ Mailbox/get")
        val methodResponses = response.getJSONArray("methodResponses")
        val mailboxResponse = methodResponses.getJSONArray(0)
        
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

    override suspend fun queryEmails(
        mailboxId: String?,
        accountId: String?,
        position: Int,
        limit: Int,
        filter: Map<String, Any>?,
        searchText: String?
    ): EmailQueryResult {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val filterJson = filter?.let { JSONObject(it) } ?: JSONObject().apply {
            if (mailboxId != null) {
                put("inMailbox", mailboxId)
            }
            if (!searchText.isNullOrBlank()) {
                put("text", searchText)
            }
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
            put("methodCalls", JSONArray().apply { put(methodCallArray) })
        }
        
        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val queryResponse = methodResponses.getJSONArray(0)
        
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

    override suspend fun getEmails(
        ids: List<String>,
        accountId: String?,
        properties: List<String>?
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

        val idsArray = JSONArray().apply { ids.forEach { put(it) } }
        val propertiesArray = JSONArray().apply { (properties ?: defaultProperties).forEach { put(it) } }
        
        val getParams = JSONObject().apply {
            put("accountId", targetAccountId)
            put("ids", idsArray)
            put("properties", propertiesArray)
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
            put("methodCalls", JSONArray().apply { put(methodCallArray) })
        }
        
        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val getResponse = methodResponses.getJSONArray(0)
        val emailData = getResponse.getJSONObject(1)
        val list = emailData.getJSONArray("list")
        val emails = mutableListOf<JmapEmail>()

        for (i in 0 until list.length()) {
            val emailJson = list.getJSONObject(i)
            emails.add(parseEmail(emailJson))
        }
        
        return emails
    }

    private fun parseEmail(json: JSONObject): JmapEmail {
        fun parseEmailAddresses(array: JSONArray?): List<EmailAddress>? {
            if (array == null) return null
            val addresses = mutableListOf<EmailAddress>()
            for (i in 0 until array.length()) {
                val addrJson = array.getJSONObject(i)
                val nameValue = addrJson.optString("name", null)
                val name = if (nameValue == null || nameValue == "null" || nameValue.isBlank()) null else nameValue
                val emailValue = addrJson.optString("email", null)
                val email = if (emailValue == null || emailValue == "null" || emailValue.isBlank()) "unknown" else emailValue
                addresses.add(EmailAddress(name = name, email = email))
            }
            return addresses
        }

        return JmapEmail(
            id = json.getString("id"),
            threadId = json.getString("threadId"),
            mailboxIds = json.optJSONObject("mailboxIds")?.let { obj ->
                val map = mutableMapOf<String, Boolean>()
                obj.keys().forEach { key -> map[key] = obj.getBoolean(key) }
                map
            } ?: emptyMap(),
            keywords = json.optJSONObject("keywords")?.let { obj ->
                val map = mutableMapOf<String, Boolean>()
                obj.keys().forEach { key -> map[key] = obj.getBoolean(key) }
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

    private suspend fun makeRequest(url: String, requestJson: JSONObject): JSONObject = requestSemaphore.withPermit {
        if (isFirstLaunch) {
            Log.d("JmapClient", "Первый запрос после запуска, добавляем задержку для оптимизации")
            delay(150)
            isFirstLaunch = false
        }
        
        val requestBody = requestJson.toString().replace("\\/", "/")
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", getAuthHeader())
            .post(requestBody)
            .build()

        val (response, responseBody) = withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            repeat(3) { attempt ->
                try {
                    val resp = client.newCall(request).execute()
                    val body = try {
                        resp.body?.string() ?: "{}"
                    } catch (e: java.io.EOFException) {
                        Log.w("JmapClient", "EOFException при чтении ответа makeRequest, попытка $attempt")
                        if (attempt < 2) {
                            delay((attempt + 1) * 1000L)
                            throw e
                        } else {
                            "{}"
                        }
                    }
                    return@withContext Pair(resp, body)
                } catch (e: java.io.EOFException) {
                    Log.w("JmapClient", "EOFException в makeRequest, попытка ${attempt + 1}/3")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                } catch (e: java.io.IOException) {
                    Log.w("JmapClient", "IOException в makeRequest, попытка ${attempt + 1}/3: ${e.message}")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    } else {
                        throw e
                    }
                }
            }
            throw Exception("Не удалось выполнить запрос после 3 попыток: ${lastException?.message}", lastException)
        }
        
        if (!response.isSuccessful) {
            throw Exception("JMAP request failed: код ${response.code}, сообщение: ${response.message}, ответ: ${responseBody.take(200)}")
        }
        
        return JSONObject(responseBody)
    }

    suspend fun updateEmailKeywords(
        emailId: String,
        keywords: Map<String, Boolean>,
        accountId: String? = null
    ): Boolean {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId
        
        val keywordsJson = JSONObject()
        keywords.forEach { (key, value) -> keywordsJson.put(key, value) }
        
        val updateObject = JSONObject().apply {
            put(emailId, JSONObject().apply { put("keywords", keywordsJson) })
        }
        
        val setParams = JSONObject().apply {
            put("accountId", targetAccountId)
            put("update", updateObject)
        }
        
        val methodCallArray = JSONArray().apply {
            put("Email/set")
            put(setParams)
            put("0")
        }
        
        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
            })
            put("methodCalls", JSONArray().apply { put(methodCallArray) })
        }
        
        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val setResponse = methodResponses.getJSONArray(0)
        val setData = setResponse.getJSONObject(1)
        val updated = setData.optJSONObject("updated")
        return updated != null && updated.has(emailId)
    }

    suspend fun deleteEmail(
        emailId: String,
        accountId: String? = null
    ): Boolean {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId
        
        val methodCallArray = JSONArray().apply {
            put("Email/set")
            put(JSONObject().apply {
                put("accountId", targetAccountId)
                put("destroy", JSONArray().apply { put(emailId) })
            })
            put("0")
        }
        
        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
            })
            put("methodCalls", JSONArray().apply { put(methodCallArray) })
        }
        
        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val setResponse = methodResponses.getJSONArray(0)
        val setData = setResponse.getJSONObject(1)
        val destroyed = setData.optJSONArray("destroyed")
        return destroyed != null && (0 until destroyed.length()).any { destroyed.getString(it) == emailId }
    }

    suspend fun moveEmail(
        emailId: String,
        fromMailboxId: String,
        toMailboxId: String,
        accountId: String? = null
    ): Boolean {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId
        
        val mailboxIds = JSONObject().apply {
            put(fromMailboxId, false)
            put(toMailboxId, true)
        }
        
        val updateObject = JSONObject().apply {
            put(emailId, JSONObject().apply { put("mailboxIds", mailboxIds) })
        }
        
        val setParams = JSONObject().apply {
            put("accountId", targetAccountId)
            put("update", updateObject)
        }
        
        val methodCallArray = JSONArray().apply {
            put("Email/set")
            put(setParams)
            put("0")
        }
        
        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
            })
            put("methodCalls", JSONArray().apply { put(methodCallArray) })
        }
        
        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val setResponse = methodResponses.getJSONArray(0)
        val setData = setResponse.getJSONObject(1)
        val updated = setData.optJSONObject("updated")
        return updated != null && updated.has(emailId)
    }

    private fun getDownloadUrl(accountId: String, blobId: String): String {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        return "$normalizedBaseUrl/jmap/download/$accountId/$blobId/attachment?accept=application/octet-stream"
    }

    suspend fun downloadAttachment(
        blobId: String,
        accountId: String? = null
    ): ByteArray = requestSemaphore.withPermit {
        if (blobId.isBlank() || blobId == "null") {
            Log.e("JmapClient", "blobId пустой или null: '$blobId'")
            throw IllegalArgumentException("blobId не может быть пустым")
        }
        
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId
        
        val downloadUrl = getDownloadUrl(targetAccountId, blobId)
        Log.d("JmapClient", "Загрузка вложения с URL: $downloadUrl")
        
        val request = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", getAuthHeader())
            .get()
            .build()
        
        val (response, responseBody) = withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(request).execute()
                val body = resp.body?.bytes() ?: ByteArray(0)
                Pair(resp, body)
            } catch (e: java.net.UnknownHostException) {
                Log.e("JmapClient", "Ошибка подключения к серверу", e)
                throw Exception("Сервер не найден. Проверьте адрес сервера.", e)
            } catch (e: java.net.ConnectException) {
                Log.e("JmapClient", "Ошибка подключения", e)
                throw Exception("Не удалось подключиться к серверу. Проверьте адрес сервера.", e)
            } catch (e: Exception) {
                Log.e("JmapClient", "Ошибка загрузки вложения", e)
                throw e
            }
        }
        
        if (!response.isSuccessful) {
            val errorMessage = when (response.code) {
                404 -> "Вложение не найдено"
                403 -> "Доступ запрещён"
                401 -> "Ошибка аутентификации"
                else -> "Ошибка загрузки вложения: код ${response.code}"
            }
            Log.e("JmapClient", errorMessage)
            throw Exception(errorMessage)
        }
        
        return@withPermit responseBody
    }
}
