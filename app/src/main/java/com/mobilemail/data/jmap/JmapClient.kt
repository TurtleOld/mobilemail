package com.mobilemail.data.jmap

import com.mobilemail.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var session: JmapSession? = null
    private val sessionCache = mutableMapOf<String, Pair<JmapSession, Long>>()
    private val SESSION_CACHE_TTL = 5 * 60 * 1000L

    private fun getAuthHeader(): String {
        val credentials = '$email:$password'
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return 'Basic $encoded'
    }

    suspend fun getSession(): JmapSession {
        val cacheKey = '$accountId:${getAuthHeader()}'
        val cached = sessionCache[cacheKey]
        
        if (cached != null && cached.second > System.currentTimeMillis()) {
            return cached.first
        }

        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val sessionUrl = '$normalizedBaseUrl/jmap/session'
        
        val request = Request.Builder()
            .url(sessionUrl)
            .header('Accept', 'application/json')
            .header('Authorization', getAuthHeader())
            .get()
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        
        if (!response.isSuccessful) {
            val discoveryUrl = '$normalizedBaseUrl/.well-known/jmap'
            val discoveryRequest = Request.Builder()
                .url(discoveryUrl)
                .header('Accept', 'application/json')
                .header('Authorization', getAuthHeader())
                .get()
                .build()
            
            val discoveryResponse = withContext(Dispatchers.IO) {
                client.newCall(discoveryRequest).execute()
            }
            
            if (!discoveryResponse.isSuccessful) {
                throw Exception('JMAP discovery failed: ${discoveryResponse.code}')
            }
            
            val discoveryJson = JSONObject(discoveryResponse.body?.string() ?: '{}')
            val apiUrl = discoveryJson.optString('apiUrl', '$normalizedBaseUrl/jmap')
            
            val sessionRequestJson = JSONObject().apply {
                put('using', JSONArray(listOf(
                    'urn:ietf:params:jmap:core',
                    'urn:ietf:params:jmap:mail'
                )))
                put('methodCalls', JSONArray(listOf(
                    JSONArray(listOf('Session/get', JSONObject(), '0'))
                )))
            }
            
            val sessionRequestBody = sessionRequestJson.toString()
                .toRequestBody('application/json'.toMediaType())
            
            val sessionRequest = Request.Builder()
                .url(apiUrl)
                .header('Content-Type', 'application/json')
                .header('Authorization', getAuthHeader())
                .post(sessionRequestBody)
                .build()
            
            val sessionResponse = withContext(Dispatchers.IO) {
                client.newCall(sessionRequest).execute()
            }
            
            if (!sessionResponse.isSuccessful) {
                throw Exception('JMAP session failed: ${sessionResponse.code}')
            }
            
            val sessionResponseJson = JSONObject(sessionResponse.body?.string() ?: '{}')
            val methodResponses = sessionResponseJson.getJSONArray('methodResponses')
            val sessionResponseData = methodResponses.getJSONArray(0)
            
            if (sessionResponseData.getString(0) != 'Session/get') {
                throw Exception('Invalid session response')
            }
            
            val sessionData = sessionResponseData.getJSONObject(1)
            session = parseSession(sessionData)
        } else {
            val sessionJson = JSONObject(response.body?.string() ?: '{}')
            session = parseSession(sessionJson)
        }

        session?.let {
            sessionCache[cacheKey] = Pair(it, System.currentTimeMillis() + SESSION_CACHE_TTL)
        }

        return session ?: throw Exception('Failed to get session')
    }

    private fun parseSession(json: JSONObject): JmapSession {
        val accountsJson = json.getJSONObject('accounts')
        val accounts = mutableMapOf<String, JmapAccount>()
        
        accountsJson.keys().forEach { key ->
            val accountJson = accountsJson.getJSONObject(key)
            accounts[key] = JmapAccount(
                id = accountJson.getString('id'),
                name = accountJson.optString('name', ''),
                isPersonal = accountJson.optBoolean('isPersonal', true),
                isReadOnly = accountJson.optBoolean('isReadOnly', false),
                accountCapabilities = null
            )
        }

        val primaryAccountsJson = json.optJSONObject('primaryAccounts')
        val primaryAccounts = primaryAccountsJson?.let {
            PrimaryAccounts(mail = it.optString('mail', null))
        }

        return JmapSession(
            apiUrl = json.getString('apiUrl'),
            downloadUrl = json.getString('downloadUrl'),
            uploadUrl = json.getString('uploadUrl'),
            eventSourceUrl = json.optString('eventSourceUrl', null),
            accounts = accounts,
            primaryAccounts = primaryAccounts,
            capabilities = null
        )
    }

    suspend fun getMailboxes(accountId: String? = null): List<JmapMailbox> {
        val session = getSession()
        val targetAccountId = accountId ?: session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull() ?: this.accountId

        val requestJson = JSONObject().apply {
            put('using', JSONArray(listOf(
                'urn:ietf:params:jmap:core',
                'urn:ietf:params:jmap:mail'
            )))
            put('methodCalls', JSONArray(listOf(
                JSONArray(listOf(
                    'Mailbox/get',
                    JSONObject().apply {
                        put('accountId', targetAccountId)
                    },
                    '0'
                ))
            )))
        }

        val response = makeRequest(session.apiUrl, requestJson)
        val methodResponses = response.getJSONArray('methodResponses')
        val mailboxResponse = methodResponses.getJSONArray(0)

        if (mailboxResponse.getString(0) != 'Mailbox/get') {
            throw Exception('Invalid mailbox response')
        }

        val mailboxData = mailboxResponse.getJSONObject(1)
        val list = mailboxData.getJSONArray('list')
        val mailboxes = mutableListOf<JmapMailbox>()

        for (i in 0 until list.length()) {
            val mailboxJson = list.getJSONObject(i)
            mailboxes.add(JmapMailbox(
                id = mailboxJson.getString('id'),
                name = mailboxJson.getString('name'),
                parentId = mailboxJson.optString('parentId', null),
                role = mailboxJson.optString('role', null),
                sortOrder = mailboxJson.optInt('sortOrder', 0),
                totalEmails = mailboxJson.optInt('totalEmails', 0),
                unreadEmails = mailboxJson.optInt('unreadEmails', 0),
                totalThreads = mailboxJson.optInt('totalThreads', 0),
                unreadThreads = mailboxJson.optInt('unreadThreads', 0)
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
            put('inMailbox', mailboxId)
        }

        val requestJson = JSONObject().apply {
            put('using', JSONArray(listOf(
                'urn:ietf:params:jmap:core',
                'urn:ietf:params:jmap:mail'
            )))
            put('methodCalls', JSONArray(listOf(
                JSONArray(listOf(
                    'Email/query',
                    JSONObject().apply {
                        put('accountId', targetAccountId)
                        put('filter', filterJson)
                        put('sort', JSONArray(listOf(
                            JSONObject().apply {
                                put('property', 'receivedAt')
                                put('isAscending', false)
                            }
                        )))
                        put('position', position)
                        put('limit', limit)
                    },
                    '0'
                ))
            )))
        }

        val response = makeRequest(session.apiUrl, requestJson)
        val methodResponses = response.getJSONArray('methodResponses')
        val queryResponse = methodResponses.getJSONArray(0)

        if (queryResponse.getString(0) != 'Email/query') {
            throw Exception('Invalid email query response')
        }

        val queryData = queryResponse.getJSONObject(1)
        val ids = mutableListOf<String>()
        val idsArray = queryData.getJSONArray('ids')
        
        for (i in 0 until idsArray.length()) {
            ids.add(idsArray.getString(i))
        }

        return EmailQueryResult(
            ids = ids,
            position = queryData.getInt('position'),
            total = queryData.optInt('total', null).takeIf { it != -1 },
            queryState = queryData.optString('queryState', null)
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
            'id', 'threadId', 'mailboxIds', 'keywords', 'size',
            'receivedAt', 'hasAttachment', 'preview', 'subject',
            'from', 'to', 'cc', 'bcc', 'bodyStructure', 'bodyValues',
            'textBody', 'htmlBody'
        )

        val requestJson = JSONObject().apply {
            put('using', JSONArray(listOf(
                'urn:ietf:params:jmap:core',
                'urn:ietf:params:jmap:mail'
            )))
            put('methodCalls', JSONArray(listOf(
                JSONArray(listOf(
                    'Email/get',
                    JSONObject().apply {
                        put('accountId', targetAccountId)
                        put('ids', JSONArray(ids))
                        put('properties', JSONArray(properties ?: defaultProperties))
                    },
                    '0'
                ))
            )))
        }

        val response = makeRequest(session.apiUrl, requestJson)
        val methodResponses = response.getJSONArray('methodResponses')
        val getResponse = methodResponses.getJSONArray(0)

        if (getResponse.getString(0) != 'Email/get') {
            throw Exception('Invalid email get response')
        }

        val emailData = getResponse.getJSONObject(1)
        val list = emailData.getJSONArray('list')
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
                addresses.add(EmailAddress(
                    name = addrJson.optString('name', null),
                    email = addrJson.getString('email')
                ))
            }
            return addresses
        }

        return JmapEmail(
            id = json.getString('id'),
            threadId = json.getString('threadId'),
            mailboxIds = json.getJSONObject('mailboxIds').let { obj ->
                val map = mutableMapOf<String, Boolean>()
                obj.keys().forEach { key ->
                    map[key] = obj.getBoolean(key)
                }
                map
            },
            keywords = json.optJSONObject('keywords')?.let { obj ->
                val map = mutableMapOf<String, Boolean>()
                obj.keys().forEach { key ->
                    map[key] = obj.getBoolean(key)
                }
                map
            },
            size = json.getLong('size'),
            receivedAt = json.getString('receivedAt'),
            hasAttachment = json.optBoolean('hasAttachment', false),
            preview = json.optString('preview', null),
            subject = json.optString('subject', null),
            from = parseEmailAddresses(json.optJSONArray('from')),
            to = parseEmailAddresses(json.optJSONArray('to')),
            cc = parseEmailAddresses(json.optJSONArray('cc')),
            bcc = parseEmailAddresses(json.optJSONArray('bcc')),
            bodyStructure = json.opt('bodyStructure'),
            bodyValues = json.optJSONObject('bodyValues')?.let { obj ->
                val map = mutableMapOf<String, BodyValue>()
                obj.keys().forEach { key ->
                    val valueJson = obj.getJSONObject(key)
                    map[key] = BodyValue(
                        value = valueJson.getString('value'),
                        isEncodingProblem = valueJson.optBoolean('isEncodingProblem', false),
                        isTruncated = valueJson.optBoolean('isTruncated', false)
                    )
                }
                map
            },
            textBody = json.optJSONArray('textBody')?.let { array ->
                val list = mutableListOf<BodyPart>()
                for (i in 0 until array.length()) {
                    val partJson = array.getJSONObject(i)
                    list.add(BodyPart(
                        partId = partJson.getString('partId'),
                        type = partJson.getString('type')
                    ))
                }
                list
            },
            htmlBody = json.optJSONArray('htmlBody')?.let { array ->
                val list = mutableListOf<BodyPart>()
                for (i in 0 until array.length()) {
                    val partJson = array.getJSONObject(i)
                    list.add(BodyPart(
                        partId = partJson.getString('partId'),
                        type = partJson.getString('type')
                    ))
                }
                list
            }
        )
    }

    private suspend fun makeRequest(url: String, requestJson: JSONObject): JSONObject {
        val requestBody = requestJson.toString()
            .toRequestBody('application/json'.toMediaType())

        val request = Request.Builder()
            .url(url)
            .header('Content-Type', 'application/json')
            .header('Authorization', getAuthHeader())
            .post(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw Exception('JMAP request failed: ${response.code} ${response.message}')
        }

        val responseBody = response.body?.string() ?: '{}'
        return JSONObject(responseBody)
    }
}
