package com.example.data.unifiedpush

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EndpointValidationTest {
    @Test
    fun `accepts valid HTTPS endpoint and connector key material`() {
        val publicKey = ByteArray(65) { if (it == 0) 4 else it.toByte() }
        val auth = ByteArray(16) { it.toByte() }

        assertTrue(EndpointValidation.isValidEndpoint("https://push.example.test/capability"))
        assertTrue(EndpointValidation.isValidPublicKey(encode(publicKey)))
        assertTrue(EndpointValidation.isValidAuthSecret(encode(auth)))
    }

    @Test
    fun `rejects insecure endpoint and malformed key material`() {
        assertFalse(EndpointValidation.isValidEndpoint("http://push.example.test"))
        assertFalse(EndpointValidation.isValidEndpoint("https://push.example.test/\n"))
        assertFalse(EndpointValidation.isValidPublicKey(encode(ByteArray(64))))
        assertFalse(EndpointValidation.isValidAuthSecret(encode(ByteArray(15))))
    }

    private fun encode(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
