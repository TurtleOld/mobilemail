package com.mobilemail.data.jmap

import android.util.Log
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.model.BodyPart
import com.mobilemail.data.model.BodyValue
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.EmailQueryResult
import com.mobilemail.data.model.EmailSubmissionStatus
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
import com.mobilemail.util.LogRedactor
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

@Suppress("TooManyFunctions", "LargeClass")
class JmapOAuthClient(
    private val baseUrl: String,
    private val email: String,
    private val accountId: String,
    private val tokenStoreAccess: TokenStoreAccess,
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
                    JmapOAuthClient(
                        serverUrl,
                        email,
                        accountId,
                        AndroidTokenStoreAccess(tokenStore),
                        metadata,
                        clientId
                    )
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
        val stored = tokenStoreAccess.getTokens(serverUrl, email)

        if (stored != null && stored.isValid()) return@withLock stored.accessToken

        if (stored?.refreshToken != null) {
            try {
                val newToken = tokenRefresh.refreshToken(stored.refreshToken)
                tokenStoreAccess.saveTokens(serverUrl, email, newToken)
                return@withLock newToken.accessToken
            } catch (e: Exception) {
                tokenStoreAccess.clearTokens(serverUrl, email)
                throw OAuthTokenExpiredException("Не удалось обновить токен: ${e.message}")
            }
        }

        throw OAuthTokenExpiredException("Токен отсутствует или истёк. Требуется авторизация.")
    }

    private suspend fun forceRefreshAccessToken(): String = tokenMutex.withLock {
        val stored = tokenStoreAccess.getTokens(serverUrl, email)

        if (stored?.refreshToken != null) {
            try {
                val newToken = tokenRefresh.refreshToken(stored.refreshToken)
                tokenStoreAccess.saveTokens(serverUrl, email, newToken)
                return@withLock newToken.accessToken
            } catch (e: Exception) {
                tokenStoreAccess.clearTokens(serverUrl, email)
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
                val refreshed = forceRefreshAccessToken()
                val (code2, body2) = doCall(buildReq(refreshed))
                if (code2 != 200) {
                    error("JMAP session failed после refresh: код $code2, ответ: ${body2.take(200)}")
                }
                body2
            } else {
                if (code1 != 200) {
                    error("JMAP session failed: код $code1, ответ: ${body1.take(200)}")
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
                    username = accountJson.optStringOrNull("username"),
                    isPersonal = accountJson.optBoolean("isPersonal", true),
                    isReadOnly = accountJson.optBoolean("isReadOnly", false),
                    accountCapabilities = null
                )
            }
        }

        val primaryAccountsJson = json.optJSONObject("primaryAccounts")
        val primaryAccounts = primaryAccountsJson?.let {
            PrimaryAccounts(mail = it.optStringOrNull("mail"))
        }

        return JmapSession(
            apiUrl = json.getString("apiUrl"),
            downloadUrl = json.getString("downloadUrl"),
            uploadUrl = json.getString("uploadUrl"),
            eventSourceUrl = json.optStringOrNull("eventSourceUrl"),
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

        val requestJson = buildJmapRequest(methodCallArray)

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
                parentId = mailboxJson.optStringOrNull("parentId"),
                role = mailboxJson.optStringOrNull("role"),
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

        val requestJson = buildJmapRequest(methodCallArray)

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
            queryState = queryData.optStringOrNull("queryState")
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

        val requestJson = buildJmapRequest(methodCallArray)

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
                val name = addrJson.optStringOrNull("name")
                val email = addrJson.optStringOrNull("email") ?: "unknown"
                addresses.add(EmailAddress(name = name, email = email))
            }
            return addresses
        }

        val subject = json.optStringOrNull("subject")

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
            preview = json.optStringOrNull("preview"),
            subject = subject,
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

    /** Execute request with retry on EOF/IO and return raw response pair (response, body). */
    private suspend fun executeWithRetry(request: Request): Pair<okhttp3.Response, String> =
        withContext(Dispatchers.IO) {
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
                    if (attempt < 2) delay((attempt + 1) * 1000L)
                } catch (e: java.io.IOException) {
                    Log.w("JmapOAuthClient", "IOException в makeRequest, попытка ${attempt + 1}/3: ${e.message}")
                    lastException = e
                    if (attempt < 2) delay((attempt + 1) * 1000L)
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < 2) delay((attempt + 1) * 1000L) else throw e
                }
            }
            throw IllegalStateException("Не удалось выполнить запрос после 3 попыток: ${lastException?.message}", lastException)
        }

    /** Retry request after refreshing the access token and return parsed response. */
    private suspend fun retryWithRefreshedToken(request: Request, originalCode: Int, originalBody: String): JSONObject {
        try {
            Log.d("JmapOAuthClient", "Получен $originalCode, попытка обновить токен и повторить запрос")
            val newToken = forceRefreshAccessToken()
            val retryRequest = request.newBuilder()
                .header("Authorization", getAuthHeader(newToken))
                .build()
            val (retryResponse, retryBody) = withContext(Dispatchers.IO) {
                val resp = client.newCall(retryRequest).execute()
                val body = resp.body?.string() ?: "{}"
                resp to body
            }
            if (!retryResponse.isSuccessful) {
                Log.e("JmapOAuthClient", "Запрос не удался после обновления токена: код ${retryResponse.code}")
                val retryErr = LogRedactor.redact(retryBody.take(200))
                error("JMAP request failed после обновления токена: код ${retryResponse.code}, ответ: $retryErr")
            }
            Log.d("JmapOAuthClient", "Запрос успешен после обновления токена")
            return JmapResponseValidator.ensureNoMethodError(JSONObject(retryBody))
        } catch (e: OAuthTokenExpiredException) {
            Log.e("JmapOAuthClient", "Токен истёк и не может быть обновлён", e)
            throw e
        } catch (e: Exception) {
            Log.e("JmapOAuthClient", "Ошибка при повторной попытке запроса", e)
            val errBody = LogRedactor.redact(originalBody.take(200))
            error("JMAP request failed: код $originalCode, ответ: $errBody")
        }
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

        val (response, responseBody) = executeWithRetry(request)

        if (!response.isSuccessful) {
            return if (response.code == 401 || response.code == 403) {
                retryWithRefreshedToken(request, response.code, responseBody)
            } else {
                val errBody = LogRedactor.redact(responseBody.take(200))
                error("JMAP request failed: код ${response.code}, сообщение: ${response.message}, ответ: $errBody")
            }
        }

        return JmapResponseValidator.ensureNoMethodError(JSONObject(responseBody))
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "").trim()
        return if (v.isBlank() || v == "null") null else v
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

        val requestJson = buildJmapRequest(methodCallArray)

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

        val requestJson = buildJmapRequest(methodCallArray)

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

        val mailboxIds = try {
            val current = getEmails(
                ids = listOf(emailId),
                accountId = targetAccountId,
                properties = listOf("id", "mailboxIds")
            ).firstOrNull()
            JSONObject().apply {
                current?.mailboxIds?.forEach { (k, v) -> if (v) put(k, true) }
                remove(fromMailboxId)
                put(toMailboxId, true)
            }
        } catch (e: Exception) {
            Log.w("JmapOAuthClient", "moveEmail: не удалось прочитать текущие mailboxIds, fallback", e)
            JSONObject().apply { put(toMailboxId, true) }
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

        val requestJson = buildJmapRequest(methodCallArray)

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

    private fun mapDownloadErrorCode(code: Int): String = when (code) {
        404 -> "Вложение не найдено"
        403 -> "Доступ запрещён"
        401 -> "Ошибка аутентификации"
        else -> "Ошибка загрузки вложения: код $code"
    }

    private suspend fun executeDownloadRequest(request: Request): Pair<okhttp3.Response, ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(request).execute()
                val body = resp.body?.bytes() ?: ByteArray(0)
                Pair(resp, body)
            } catch (e: java.net.UnknownHostException) {
                Log.e("JmapOAuthClient", "Ошибка подключения к серверу", e)
                throw IllegalStateException("Сервер не найден. Проверьте адрес сервера.", e)
            } catch (e: java.net.ConnectException) {
                Log.e("JmapOAuthClient", "Ошибка подключения", e)
                throw IllegalStateException("Не удалось подключиться к серверу. Проверьте адрес сервера.", e)
            } catch (e: Exception) {
                Log.e("JmapOAuthClient", "Ошибка загрузки вложения", e)
                throw e
            }
        }

    private suspend fun retryDownloadWithRefresh(request: Request): ByteArray {
        return try {
            val newToken = forceRefreshAccessToken()
            val retryRequest = request.newBuilder()
                .header("Authorization", getAuthHeader(newToken))
                .build()
            val retryResponse = client.newCall(retryRequest).execute()
            val retryBody = retryResponse.body?.bytes() ?: ByteArray(0)
            if (!retryResponse.isSuccessful) {
                throw IllegalStateException(mapDownloadErrorCode(retryResponse.code))
            }
            retryBody
        } catch (e: OAuthTokenExpiredException) {
            throw IllegalStateException("Токен истёк. Требуется повторная авторизация.", e)
        }
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

        val (response, responseBody) = executeDownloadRequest(request)

        if (!response.isSuccessful) {
            return@withPermit if (response.code == 401 || response.code == 403) {
                retryDownloadWithRefresh(request)
            } else {
                val errorMessage = mapDownloadErrorCode(response.code)
                Log.e("JmapOAuthClient", errorMessage)
                throw IllegalStateException(errorMessage)
            }
        }

        return@withPermit responseBody
    }

    override suspend fun uploadAttachment(
        data: ByteArray,
        mimeType: String,
        filename: String,
        accountId: String?
    ): Attachment {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val uploadUrl = session.uploadUrl
            .replace("{accountId}", targetAccountId)
            .replace("{name}", filename)
            .replace("{type}", mimeType)

        val accessToken = getAccessToken()
        val request = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", getAuthHeader(accessToken))
            .post(data.toRequestBody(mimeType.toMediaType()))
            .build()

        val (response, responseBody) = withContext(Dispatchers.IO) {
            val resp = client.newCall(request).execute()
            val body = resp.body?.string().orEmpty()
            Pair(resp, body)
        }

        if (!response.isSuccessful) {
            error("Upload failed: код ${response.code}, ответ: ${LogRedactor.redact(responseBody.take(200))}")
        }

        val json = JSONObject(responseBody)
        val blobId = json.optString("blobId")
        val size = json.optLong("size", data.size.toLong())
        val type = json.optString("type", mimeType)

        if (blobId.isBlank()) {
            error("Upload failed: blobId отсутствует")
        }

        return Attachment(
            id = blobId,
            filename = filename,
            mime = type,
            size = size
        )
    }

    private fun buildDraftEmailJson(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        draftsMailboxId: String
    ): JSONObject {
        val toArray = JSONArray().apply {
            to.filter { it.isNotBlank() }.forEach { put(JSONObject().apply { put("email", it.trim()) }) }
        }
        val fromArray = JSONArray().apply {
            put(JSONObject().apply { put("email", from.trim()) })
        }

        val bodyParts = buildBodyParts(attachments)
        val bodyValues = JSONObject().apply {
            put("text", JSONObject().apply { put("value", body) })
        }

        val bodyStructure = if (bodyParts.length() == 1) {
            bodyParts.getJSONObject(0)
        } else {
            JSONObject().apply {
                put("type", "multipart/mixed")
                put("subParts", bodyParts)
            }
        }

        return JSONObject().apply {
            put("mailboxIds", JSONObject().apply { put(draftsMailboxId, true) })
            put("keywords", JSONObject().apply { put("\$seen", true) })
            put("from", fromArray)
            put("to", toArray)
            put("subject", subject)
            put("bodyStructure", bodyStructure)
            put("bodyValues", bodyValues)
        }
    }

    private fun buildBodyParts(attachments: List<Attachment>): JSONArray {
        val bodyParts = JSONArray().apply {
            put(JSONObject().apply {
                put("partId", "text")
                put("type", "text/plain")
            })
        }
        attachments.forEach { attachment ->
            bodyParts.put(JSONObject().apply {
                put("type", attachment.mime)
                put("name", attachment.filename)
                put("disposition", "attachment")
                put("blobId", attachment.id)
                put("size", attachment.size)
            })
        }
        return bodyParts
    }

    override suspend fun saveDraft(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        draftId: String?,
        accountId: String?
    ): String {
        Log.d(
            "JmapOAuthClient",
            "saveDraft: from=$from, toCount=${to.size}, subject=${subject.length}ch, " +
                "body=${body.length}ch, attachments=${attachments.size}, draftId=$draftId"
        )
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val mailboxes = getMailboxes(targetAccountId)
        val draftsMailbox = mailboxes.firstOrNull { it.role == "drafts" } ?: mailboxes.firstOrNull()
            ?: error("Drafts mailbox not found")

        val createEmailObject = buildDraftEmailJson(from, to, subject, body, attachments, draftsMailbox.id)

        val setParams = JSONObject().apply {
            put("accountId", targetAccountId)
            put("create", JSONObject().apply { put("draft", createEmailObject) })
            if (!draftId.isNullOrBlank()) {
                put("destroy", JSONArray().apply { put(draftId) })
            }
        }

        val methodCallArray = JSONArray().apply {
            put("Email/set")
            put(setParams)
            put("d1")
        }

        val requestJson = buildJmapRequest(methodCallArray)

        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val setResponse = methodResponses.getJSONArray(0).getJSONObject(1)
        Log.d("JmapOAuthClient", "saveDraft response: $setResponse")

        val created = setResponse.optJSONObject("created")?.optJSONObject("draft")
        val createdId = created?.optString("id")
        if (createdId.isNullOrBlank()) {
            error("Не удалось создать черновик")
        }
        return createdId
    }

    private fun buildSubmissionRequest(
        targetAccountId: String,
        resolvedDraftId: String,
        identityId: String,
        from: String,
        to: List<String>
    ): JSONObject {
        val submissionParams = JSONObject().apply {
            put("accountId", targetAccountId)
            put("create", JSONObject().apply {
                put("submit", JSONObject().apply {
                    put("emailId", resolvedDraftId)
                    put("identityId", identityId)
                    put("envelope", JSONObject().apply {
                        put("mailFrom", JSONObject().apply { put("email", from.trim()) })
                        put("rcptTo", JSONArray().apply {
                            to.filter { it.isNotBlank() }
                                .forEach { address -> put(JSONObject().apply { put("email", address.trim()) }) }
                        })
                    })
                })
            })
        }

        return JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
                put("urn:ietf:params:jmap:submission")
            })
            put("methodCalls", JSONArray().apply {
                put(JSONArray().apply {
                    put("EmailSubmission/set")
                    put(submissionParams)
                    put("c2")
                })
            })
        }
    }

    private suspend fun moveEmailToDraftsToSent(targetAccountId: String, resolvedDraftId: String) {
        try {
            val mailboxes = getMailboxes(targetAccountId)
            val draftsMailbox = mailboxes.firstOrNull { it.role == "drafts" }
            val sentMailbox = mailboxes.firstOrNull { it.role == "sent" }
            if (draftsMailbox == null || sentMailbox == null) return

            val updateObject = JSONObject().apply {
                put(resolvedDraftId, JSONObject().apply {
                    put("mailboxIds", JSONObject().apply { put(sentMailbox.id, true) })
                })
            }

            val methodCallArray = JSONArray().apply {
                put("Email/set")
                put(JSONObject().apply {
                    put("accountId", targetAccountId)
                    put("update", updateObject)
                })
                put("m1")
            }

            val requestJson2 = buildJmapRequest(methodCallArray)
            val moveResp = makeRequest(getApiUrl(), requestJson2)
            val mr = moveResp.getJSONArray("methodResponses")
            val first = mr.getJSONArray(0)
            val data = first.optJSONObject(1)
            val updatedOk = data?.optJSONObject("updated")?.has(resolvedDraftId) == true
            if (!updatedOk) {
                Log.w(
                    "JmapOAuthClient",
                    "Перемещение в Sent могло не выполниться: emailId=$resolvedDraftId, response=${data ?: first.toString()}"
                )
            }
        } catch (e: Exception) {
            Log.w("JmapOAuthClient", "Не удалось переместить письмо из Drafts в Sent", e)
        }
    }

    override suspend fun sendEmail(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        draftId: String?,
        accountId: String?
    ): String {
        Log.d(
            "JmapOAuthClient",
            "sendEmail: from=$from, toCount=${to.size}, subject=${subject.length}ch, " +
                "body=${body.length}ch, attachments=${attachments.size}, draftId=$draftId"
        )
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val resolvedDraftId = saveDraft(from, to, subject, body, attachments, draftId, targetAccountId)

        val identityId = getIdentityId(targetAccountId, from)
        if (identityId.isBlank()) error("Не удалось определить identityId для отправки")
        if (resolvedDraftId.isBlank()) error("Не удалось определить emailId для отправки")

        val requestJson = buildSubmissionRequest(targetAccountId, resolvedDraftId, identityId, from, to)

        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")

        val submissionResponse = methodResponses.getJSONArray(0).getJSONObject(1)
        Log.d("JmapOAuthClient", "sendEmail response: $submissionResponse")
        val createdSubmission = submissionResponse.optJSONObject("created")?.optJSONObject("submit")
        val submissionId = createdSubmission?.optString("id")

        if (submissionId.isNullOrBlank()) {
            error("Не удалось отправить письмо: ответ сервера без id")
        }

        Log.d("JmapOAuthClient", "EmailSubmission создан: submissionId=$submissionId, emailId=$resolvedDraftId")

        moveEmailToDraftsToSent(targetAccountId, resolvedDraftId)

        return submissionId
    }

    private fun parseDeliveryStatus(obj: JSONObject): String? {
        val deliveryStatusObj = obj.optJSONObject("deliveryStatus") ?: return null
        val it = deliveryStatusObj.keys()
        while (it.hasNext()) {
            val k = it.next()
            val ds = deliveryStatusObj.optJSONObject(k) ?: continue
            val reply = ds.optStringOrNull("smtpReply")
                ?: ds.optStringOrNull("description")
                ?: ds.optStringOrNull("message")
            if (!reply.isNullOrBlank()) return reply
        }
        return null
    }

    override suspend fun getEmailSubmission(
        submissionId: String,
        accountId: String?
    ): EmailSubmissionStatus {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val methodCallArray = JSONArray().apply {
            put("EmailSubmission/get")
            put(JSONObject().apply {
                put("accountId", targetAccountId)
                put("ids", JSONArray().apply { put(submissionId) })
            })
            put("s1")
        }

        val requestJson = JSONObject().apply {
            put("using", JSONArray().apply {
                put("urn:ietf:params:jmap:core")
                put("urn:ietf:params:jmap:mail")
                put("urn:ietf:params:jmap:submission")
            })
            put("methodCalls", JSONArray().apply { put(methodCallArray) })
        }

        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val data = response.getJSONArray("methodResponses").getJSONArray(0).getJSONObject(1)

        val list = data.optJSONArray("list") ?: JSONArray()
        if (list.length() == 0) {
            return EmailSubmissionStatus(id = submissionId, raw = emptyMap())
        }

        return parseEmailSubmissionObject(list.getJSONObject(0), submissionId)
    }

    private fun parseEmailSubmissionObject(obj: JSONObject, fallbackId: String): EmailSubmissionStatus {
        val emailId = obj.optStringOrNull("emailId")
        val lastStatusText = parseDeliveryStatus(obj)

        val delivered = when (obj.optString("undoStatus", "")) {
            "final" -> true
            else -> null
        }
        val failed = obj.optString("undoStatus", "").equals("failed", ignoreCase = true)
            .takeIf { it }

        return EmailSubmissionStatus(
            id = obj.optString("id", fallbackId),
            emailId = emailId,
            delivered = delivered,
            failed = failed,
            lastStatusText = lastStatusText,
            raw = obj.toMap()
        )
    }

    private fun buildJmapRequest(
        methodCallArray: JSONArray,
        extraCapabilities: List<String> = emptyList()
    ): JSONObject = JSONObject().apply {
        put("using", JSONArray().apply {
            put("urn:ietf:params:jmap:core")
            put("urn:ietf:params:jmap:mail")
            extraCapabilities.forEach { put(it) }
        })
        put("methodCalls", JSONArray().apply { put(methodCallArray) })
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val it = keys()
        while (it.hasNext()) {
            val k = it.next()
            result[k] = when (val v = opt(k)) {
                is JSONObject -> v.toMap()
                is JSONArray -> v.toList()
                JSONObject.NULL -> null
                else -> v
            }
        }
        return result
    }

    private fun JSONArray.toList(): List<Any?> {
        val result = mutableListOf<Any?>()
        for (i in 0 until length()) {
            result += when (val v = opt(i)) {
                is JSONObject -> v.toMap()
                is JSONArray -> v.toList()
                JSONObject.NULL -> null
                else -> v
            }
        }
        return result
    }

    private suspend fun getIdentityId(accountId: String, from: String): String {
        val methodCallArray = JSONArray().apply {
            put("Identity/get")
            put(JSONObject().apply { put("accountId", accountId) })
            put("i1")
        }

        val requestJson = buildJmapRequest(methodCallArray)

        val apiUrl = getApiUrl()
        val response = makeRequest(apiUrl, requestJson)
        val methodResponses = response.getJSONArray("methodResponses")
        val identityResponse = methodResponses.getJSONArray(0).getJSONObject(1)
        val list = identityResponse.optJSONArray("list") ?: JSONArray()
        if (list.length() == 0) return ""

        val normalizedFrom = from.trim().lowercase()
        var fallbackId = ""
        for (i in 0 until list.length()) {
            val identity = list.getJSONObject(i)
            val id = identity.optString("id")
            if (fallbackId.isBlank()) {
                fallbackId = id
            }
            val email = identity.optString("email", "").trim().lowercase()
            if (email == normalizedFrom) {
                return id
            }
        }
        return fallbackId
    }
}

class OAuthTokenExpiredException(message: String) : Exception(message)
