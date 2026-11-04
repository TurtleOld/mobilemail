package com.mobilemail.data.jmap

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendOutcomeUnknownExceptionTest {

    @Test
    fun `connection refused is definitely not dispatched`() {
        assertTrue(isSendDefinitelyNotDispatched(ConnectException("refused")))
    }

    @Test
    fun `unknown host is definitely not dispatched`() {
        assertTrue(isSendDefinitelyNotDispatched(UnknownHostException("no dns")))
    }

    @Test
    fun `read timeout leaves the outcome unknown`() {
        assertFalse(isSendDefinitelyNotDispatched(SocketTimeoutException("timeout")))
    }

    @Test
    fun `wrapped connection failure is detected through causes`() {
        val wrapped = IOException("upload failed", ConnectException("refused"))

        assertTrue(isSendDefinitelyNotDispatched(wrapped))
    }
}
