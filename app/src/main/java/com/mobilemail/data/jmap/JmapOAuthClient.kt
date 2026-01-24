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
import com.mobilemail.data.oauth.OAuthException
import com.mobilemail.data.oauth.OAuthServerMetadata
import com.mobilemail.data.oauth.OAuthTokenRefresh
import com.mobilemail.data.oauth.StoredToken
import com.mobilemail.data.oauth.TokenStore
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
import java.util.concurrent.TimeUnit

class JmapOAuthClient(
    private val baseUrl: String,
    private val email: String,
    private val accountId: String,
    private val tokenStore: TokenStore,
    private val metadata: OAuthServerMetadata,
    private val clientId: String
) : JmapApi {
    companion object {
        private val clientCache = mutableMapOf<String, JmapOAuthClient>()
        private val lock = Any()

        fun getOrCreate(
            serverUrl: String,
            email: String,
            accountId: String,
            tokenStore: TokenStore,
            metadata: OAuthServerMetadata,
            clientId: String
        ): JmapOAuthClient {
            val key = "$serverUrl:$email"
            synchronized(lock) {
                return clientCache.getOrPut(key) {
                    JmapOAuthClient(serverUrl, email, accountId, tokenStore, metadata, clientId)
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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(2, 2, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    
    private val tokenRefresh = OAuthTokenRefresh(metadata, clientId, client)
    
    private var session: JmapSession? = null
    private val sessionCache = mutableMapOf<String, Pair<JmapSession, Long>>()
    private val sessionCacheTtl = 5 * 60 * 1000L
    
    private val sessionMutex = Mutex()
    private val tokenMutex = Mutex()
    private val requestSemaphore = Semaphore(permits = 2)
    private var isFirstLaunch = true

    private val serverUrl = baseUrl.trimEnd('/')
    
    private suspend fun getAccessToken(): String = tokenMutex.withLock {
        val stored = tokenStore.getTokens(serverUrl, email)

        if (stored != null && stored.isValid()) return@withLock stored.accessToken

        if (stored?.refreshToken != null) {
            try {
                val newToken = tokenRefresh.refreshToken(stored.refreshToken)
                tokenStore.saveTokens(serverUrl, email, newToken)
                return@withLock newToken.accessToken
            } catch (e: Exception) {
                tokenStore.clearTokens(serverUrl, email)
                throw OAuthTokenExpiredException("Не удалось обновить токен: ${e.message}")
            }
        }

        throw OAuthTokenExpiredException("Токен отсутствует или истёк. Требуется авторизация.")
    }

    private suspend fun forceRefreshAccessToken(): String = tokenMutex.withLock {
        val stored = tokenStore.getTokens(serverUrl, email)

        if (stored?.refreshToken != null) {
            try {
                val newToken = tokenRefresh.refreshToken(stored.refreshToken)
                tokenStore.saveTokens(serverUrl, email, newToken)
                return@withLock newToken.accessToken
            } catch (e: Exception) {
                tokenStore.clearTokens(serverUrl, email)
                throw OAuthTokenExpiredException("Не удалось обновить токен: ${e.message}")
            }
        }

        throw OAuthTokenExpiredException("Токен отсутствует. Требуется авторизация.")
    }
    
    private fun getAuthHeader(accessToken: String): String {
        return "Bearer $accessToken"
    }
    
    override suspend fun getSession(): JmapSession {
        val accessToken = getAccessToken()

        return sessionMutex.withLock {
            val cacheKey = "$accountId:$accessToken"
            val cached = sessionCache[cacheKey]
            if (cached != null && cached.second > System.currentTimeMillis()) {
                return@withLock cached.first
            }

            session?.let {
                sessionCache[cacheKey] = Pair(it, System.currentTimeMillis() + sessionCacheTtl)
                return@withLock it
            }

            val sessionUrl = "$serverUrl/.well-known/jmap"

            fun buildReq(token: String) = Request.Builder()
                .url(sessionUrl)
                .header("Accept", "application/json")
                .header("Authorization", getAuthHeader(token))
                .get()
                .build()

            suspend fun doCall(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                resp.code to body
            }

            val (code1, body1) = doCall(buildReq(accessToken))

            val finalBody = if (code1 == 401 || code1 == 403) {
                val refreshed = forceRefreshAccessToken() // гарантированно новый токен
                val (code2, body2) = doCall(buildReq(refreshed))
                if (code2 != 200) {
                    throw Exception("JMAP session failed после refresh: код $code2, ответ: ${body2.take(200)}")
                }
                body2
            } else {
                if (code1 != 200) {
                    throw Exception("JMAP session failed: код $code1, ответ: ${body1.take(200)}")
                }
                body1
            }

            val created = parseSession(JSONObject(finalBody))
            session = created
            sessionCache[cacheKey] = Pair(created, System.currentTimeMillis() + sessionCacheTtl)
            return@withLock created
        }
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
                    username = accountJson.optString("username", null),
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
    
    private suspend fun getApiUrl(): String {
        val session = getSession()
        return session.apiUrl
    }
    
    override suspend fun getMailboxes(accountId: String?): List<JmapMailbox> {
        Log.d("JmapOAuthClient", "getMailboxes вызван для accountId: $accountId")
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId
        
        Log.d("JmapOAuthClient", "Запрос Mailbox/get для accountId: $targetAccountId")
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
        Log.d("JmapOAuthClient", "Отправка запроса Mailbox/get на $apiUrl")
        val response = makeRequest(apiUrl, requestJson)
        Log.d("JmapOAuthClient", "Получен ответ Mailbox/get")
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
            Log.d("JmapOAuthClient", "Первый запрос после запуска, добавляем задержку для оптимизации")
            delay(150)
            isFirstLaunch = false
        }
        
        val accessToken = getAccessToken()
        val requestBody = requestJson.toString().replace("\\/", "/")
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", getAuthHeader(accessToken))
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
                        Log.w("JmapOAuthClient", "EOFException при чтении ответа makeRequest, попытка $attempt")
                        if (attempt < 2) {
                            delay((attempt + 1) * 1000L)
                            throw e
                        } else {
                            "{}"
                        }
                    }
                    return@withContext Pair(resp, body)
                } catch (e: java.io.EOFException) {
                    Log.w("JmapOAuthClient", "EOFException в makeRequest, попытка ${attempt + 1}/3")
                    lastException = e
                    if (attempt < 2) {
                        delay((attempt + 1) * 1000L)
                    }
                } catch (e: java.io.IOException) {
                    Log.w("JmapOAuthClient", "IOException в makeRequest, попытка ${attempt + 1}/3: ${e.message}")
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
            if (response.code == 401 || response.code == 403) {
                try {
                    Log.d("JmapOAuthClient", "Получен ${response.code}, попытка обновить токен и повторить запрос")
                    val newToken = getAccessToken()
                    val retryRequest = request.newBuilder()
                        .header("Authorization", getAuthHeader(newToken))
                        .build()
                    val retryResponse = client.newCall(retryRequest).execute()
                    val retryBody = retryResponse.body?.string() ?: "{}"
                    if (!retryResponse.isSuccessful) {
                        Log.e("JmapOAuthClient", "Запрос не удался после обновления токена: код ${retryResponse.code}")
                        throw Exception("JMAP request failed после обновления токена: код ${retryResponse.code}, ответ: ${retryBody.take(200)}")
                    }
                    Log.d("JmapOAuthClient", "Запрос успешен после обновления токена")
                    return JSONObject(retryBody)
                } catch (e: OAuthTokenExpiredException) {
                    Log.e("JmapOAuthClient", "Токен истёк и не может быть обновлён", e)
                    throw e
                } catch (e: Exception) {
                    Log.e("JmapOAuthClient", "Ошибка при повторной попытке запроса", e)
                    throw Exception("JMAP request failed: код ${response.code}, сообщение: ${response.message}, ответ: ${responseBody.take(200)}")
                }
            } else {
                throw Exception("JMAP request failed: код ${response.code}, сообщение: ${response.message}, ответ: ${responseBody.take(200)}")
            }
        }
        
        return JSONObject(responseBody)
    }
    
    override suspend fun updateEmailKeywords(
        emailId: String,
        keywords: Map<String, Boolean>,
        accountId: String?
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
    
    override suspend fun deleteEmail(
        emailId: String,
        accountId: String?
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
    
    override suspend fun moveEmail(
        emailId: String,
        fromMailboxId: String,
        toMailboxId: String,
        accountId: String?
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
        return "$serverUrl/jmap/download/$accountId/$blobId/attachment?accept=application/octet-stream"
    }
    
    override suspend fun downloadAttachment(
        blobId: String,
        accountId: String?
    ): ByteArray = requestSemaphore.withPermit {
        if (blobId.isBlank() || blobId == "null") {
            Log.e("JmapOAuthClient", "blobId пустой или null: '$blobId'")
            throw IllegalArgumentException("blobId не может быть пустым")
        }
        
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId
        
        val downloadUrl = getDownloadUrl(targetAccountId, blobId)
        Log.d("JmapOAuthClient", "Загрузка вложения с URL: $downloadUrl")
        
        val accessToken = getAccessToken()
        val request = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", getAuthHeader(accessToken))
            .get()
            .build()
        
        val (response, responseBody) = withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(request).execute()
                val body = resp.body?.bytes() ?: ByteArray(0)
                Pair(resp, body)
            } catch (e: java.net.UnknownHostException) {
                Log.e("JmapOAuthClient", "Ошибка подключения к серверу", e)
                throw Exception("Сервер не найден. Проверьте адрес сервера.", e)
            } catch (e: java.net.ConnectException) {
                Log.e("JmapOAuthClient", "Ошибка подключения", e)
                throw Exception("Не удалось подключиться к серверу. Проверьте адрес сервера.", e)
            } catch (e: Exception) {
                Log.e("JmapOAuthClient", "Ошибка загрузки вложения", e)
                throw e
            }
        }
        
        if (!response.isSuccessful) {
            if (response.code == 401 || response.code == 403) {
                try {
                    val newToken = getAccessToken()
                    val retryRequest = request.newBuilder()
                        .header("Authorization", getAuthHeader(newToken))
                        .build()
                    val retryResponse = client.newCall(retryRequest).execute()
                    val retryBody = retryResponse.body?.bytes() ?: ByteArray(0)
                    if (!retryResponse.isSuccessful) {
                        val errorMessage = when (retryResponse.code) {
                            404 -> "Вложение не найдено"
                            403 -> "Доступ запрещён"
                            401 -> "Ошибка аутентификации"
                            else -> "Ошибка загрузки вложения: код ${retryResponse.code}"
                        }
                        throw Exception(errorMessage)
                    }
                    return@withPermit retryBody
                } catch (e: OAuthTokenExpiredException) {
                    throw Exception("Токен истёк. Требуется повторная авторизация.", e)
                } catch (e: Exception) {
                    throw e
                }
            } else {
                val errorMessage = when (response.code) {
                    404 -> "Вложение не найдено"
                    403 -> "Доступ запрещён"
                    401 -> "Ошибка аутентификации"
                    else -> "Ошибка загрузки вложения: код ${response.code}"
                }
                Log.e("JmapOAuthClient", errorMessage)
                throw Exception(errorMessage)
            }
        }
        
        return@withPermit responseBody
    }
}

class OAuthTokenExpiredException(message: String) : Exception(message)
