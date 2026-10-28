package com.mobilemail.data.oauth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class OAuthRefreshFailureClassifierTest {

    @Test
    fun `classifies socket timeout as transient`() {
        val outcome = OAuthRefreshFailureClassifier.classify(SocketTimeoutException("timeout"))
        assertTrue(outcome is OAuthRefreshFailure.Transient)
    }

    @Test
    fun `classifies 400 invalid_grant as terminal`() {
        val error = OAuthException("invalid_grant", statusCode = 400, errorBody = """{"error":"invalid_grant"}""")
        val outcome = OAuthRefreshFailureClassifier.classify(error)
        assertTrue(outcome is OAuthRefreshFailure.TerminalAuthFailure)
    }

    @Test
    fun `classifies 401 as terminal`() {
        val error = OAuthException("unauthorized", statusCode = 401)
        val outcome = OAuthRefreshFailureClassifier.classify(error)
        assertTrue(outcome is OAuthRefreshFailure.TerminalAuthFailure)
    }

    @Test
    fun `classifies 429 rate limit as transient`() {
        val error = OAuthException("rate limited", statusCode = 429)
        val outcome = OAuthRefreshFailureClassifier.classify(error)
        assertTrue(outcome is OAuthRefreshFailure.Transient)
    }

    @Test
    fun `classifies 408 request timeout as transient`() {
        val error = OAuthException("request timeout", statusCode = 408)
        val outcome = OAuthRefreshFailureClassifier.classify(error)
        assertTrue(outcome is OAuthRefreshFailure.Transient)
    }

    @Test
    fun `classifies 5xx as transient`() {
        val error = OAuthException("server error", statusCode = 503)
        val outcome = OAuthRefreshFailureClassifier.classify(error)
        assertTrue(outcome is OAuthRefreshFailure.Transient)
    }

    @Test
    fun `classifies oauth exception without status code as transient`() {
        val error = OAuthException("parse error", statusCode = null)
        val outcome = OAuthRefreshFailureClassifier.classify(error)
        assertTrue(outcome is OAuthRefreshFailure.Transient)
    }
}
