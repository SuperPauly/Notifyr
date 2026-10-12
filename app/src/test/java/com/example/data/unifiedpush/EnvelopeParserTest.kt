package com.example.data.unifiedpush

import com.example.data.model.ServerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [UnifiedPushPayloadDecoder] against the notifications.v2 envelope shapes.
 * Pure JVM: no Android dependency beyond the data models.
 */
class EnvelopeParserTest {

    private val server = ServerEntity(
        serverId = "srv-1",
        displayName = "Prod",
        baseUrl = "https://srv.example"
    )

    private fun decode(json: String): DecodeResult =
        UnifiedPushPayloadDecoder.decodePayload(
            rawBytes = json.toByteArray(Charsets.UTF_8),
            rawString = null,
            server = server
        )

    private fun inlineNotification(
        revision: String = "5",
        fromServer: String = "srv-1",
        id: String = "42"
    ): String = """
        {
          "version": 1,
          "revision": "$revision",
          "kind": "notification",
          "from_server": "$fromServer",
          "state": {
            "notification": {
              "id": "$id",
              "title": "Title",
              "body": "Body",
              "created_at": "2025-03-01T12:00:00Z",
              "from_app": "app"
            },
            "responded": false
          }
        }
    """.trimIndent()

    @Test
    fun `inline notification binds revision and local connection`() {
        val result = decode(inlineNotification())
        assertTrue(result is DecodeResult.Notification)
        result as DecodeResult.Notification
        assertEquals("5", result.revision)
        assertEquals("srv-1", result.fromServer)
        assertEquals(server.connectionId, result.connectionId)
        assertEquals("42", result.state.notification.id)
    }

    @Test
    fun `unsupported version is ignored`() {
        val result = decode(inlineNotification().replace("\"version\": 1", "\"version\": 2"))
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.UNSUPPORTED_VERSION), result)
    }

    @Test
    fun `state and reference together is rejected`() {
        val json = """
            {
              "version": 1,
              "revision": "5",
              "kind": "notification",
              "from_server": "srv-1",
              "state": { "notification": { "id": "42", "title": "T" }, "responded": false },
              "reference": { "from_server": "srv-1", "notification_id": "42" }
            }
        """.trimIndent()
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.STATE_REFERENCE_BOTH), decode(json))
    }

    @Test
    fun `neither state nor reference is rejected`() {
        val json = """
            { "version": 1, "revision": "5", "kind": "notification", "from_server": "srv-1" }
        """.trimIndent()
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.NEITHER_STATE_NOR_REFERENCE), decode(json))
    }

    @Test
    fun `inline notification without revision is rejected`() {
        val json = """
            {
              "version": 1,
              "kind": "notification",
              "from_server": "srv-1",
              "state": { "notification": { "id": "42", "title": "T" }, "responded": false }
            }
        """.trimIndent()
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.NO_REVISION), decode(json))
    }

    @Test
    fun `envelope from_server mismatch is rejected`() {
        val result = decode(inlineNotification(fromServer = "srv-other"))
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.SERVER_MISMATCH), result)
    }

    @Test
    fun `valid reference resolves without a revision`() {
        val json = """
            {
              "version": 1,
              "kind": "notification",
              "from_server": "srv-1",
              "reference": { "from_server": "srv-1", "notification_id": "42" }
            }
        """.trimIndent()
        val result = decode(json)
        assertTrue(result is DecodeResult.Reference)
        result as DecodeResult.Reference
        assertEquals("42", result.notificationId)
        assertEquals("srv-1", result.fromServer)
        assertEquals(server.connectionId, result.connectionId)
    }

    @Test
    fun `reference with mismatched from_server is rejected`() {
        val json = """
            {
              "version": 1,
              "kind": "notification",
              "from_server": "srv-1",
              "reference": { "from_server": "srv-other", "notification_id": "42" }
            }
        """.trimIndent()
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.SERVER_MISMATCH), decode(json))
    }

    @Test
    fun `reference with non-positive id is rejected`() {
        val json = """
            {
              "version": 1,
              "kind": "notification",
              "from_server": "srv-1",
              "reference": { "from_server": "srv-1", "notification_id": "0" }
            }
        """.trimIndent()
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.INVALID_NOTIFICATION_ID), decode(json))
    }

    @Test
    fun `state_update carries a revision bump`() {
        val json = """
            {
              "version": 1,
              "revision": "7",
              "kind": "state_update",
              "from_server": "srv-1",
              "state": {
                "notification": { "id": "42", "title": "Updated" },
                "responded": true
              }
            }
        """.trimIndent()
        val result = decode(json)
        assertTrue(result is DecodeResult.StateUpdate)
        result as DecodeResult.StateUpdate
        assertEquals("7", result.revision)
        assertTrue(result.state.responded)
    }

    @Test
    fun `registration challenge binds installation and visible copy`() {
        val json = """
            {
              "version": 1,
              "kind": "registration_challenge",
              "from_server": "srv-1",
              "installation_id": "inst-1",
              "subscription_version": 3,
              "challenge": "nonce-abc",
              "visible_notification": { "title": "Confirm", "body": "Tap to confirm" }
            }
        """.trimIndent()
        val result = decode(json)
        assertTrue(result is DecodeResult.RegistrationChallenge)
        result as DecodeResult.RegistrationChallenge
        assertEquals("inst-1", result.installationId)
        assertEquals(3, result.subscriptionVersion)
        assertEquals("nonce-abc", result.challenge)
        assertEquals("Confirm", result.visibleTitle)
        assertEquals(server.connectionId, result.connectionId)
    }

    @Test
    fun `challenge missing visible notification is rejected`() {
        val json = """
            {
              "version": 1,
              "kind": "registration_challenge",
              "from_server": "srv-1",
              "installation_id": "inst-1",
              "subscription_version": 3,
              "challenge": "nonce-abc"
            }
        """.trimIndent()
        assertEquals(DecodeResult.Ignored(DecodeResult.Reason.CHALLENGE_SHAPE_INVALID), decode(json))
    }

    @Test
    fun `empty payload and non-json are distinguished`() {
        assertEquals(
            DecodeResult.Ignored(DecodeResult.Reason.EMPTY_PAYLOAD),
            UnifiedPushPayloadDecoder.decodePayload(null, "   ", server)
        )
        assertEquals(
            DecodeResult.Ignored(DecodeResult.Reason.NOT_JSON),
            decode("not json at all")
        )
    }
}
