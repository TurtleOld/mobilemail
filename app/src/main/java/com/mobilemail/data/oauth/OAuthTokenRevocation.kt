package com.mobilemail.data.oauth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OAuthTokenRevocation(
    private val revocationEndpoint: String,
    private val clientId: String,
    private val client: OkHttpClient
) {
    // RFC 7009: revoke a single token (access or refresh).
    // Returns true on success, false if the server returned an error.
    // Network failures are caught and logged — callers should always clear tokens locally regardless.
    suspend fun revokeToken(token: String, tokenTypeHint: String): Boolean = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("token", token)
            .add("token_type_hint", tokenTypeHint)
            .add("client_id", clientId)
            .build()

        val request = Request.Builder()
            .url(revocationEndpoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) {
                Log.d("OAuthTokenRevocation", "Token revoked ($tokenTypeHint), code=${response.code}")
                true
            } else {
                Log.w("OAuthTokenRevocation", "Revocation returned ${response.code} for $tokenTypeHint")
                false
            }
        } catch (e: Exception) {
            Log.w("OAuthTokenRevocation", "Revocation request failed ($tokenTypeHint): ${e.message}")
            false
        }
    }

    companion object {
        fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
