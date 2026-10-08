package com.example.data.remote

import com.example.data.remote.model.ActionKindDto
import com.example.data.remote.model.EnvelopeDto
import com.example.data.remote.model.EnvelopeKind
import com.example.data.remote.model.ErrorDto
import com.example.data.remote.model.InboxPageDto
import com.example.data.remote.model.MeDto
import com.example.data.remote.model.NotificationStateDto
import com.example.data.remote.model.ServerInfoDto
import com.example.data.remote.model.SubscriptionStatusDto
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses one fixture per documented route/shape (API_CONTRACT.md) with the production
 * Moshi configuration, catching drift between the server contract and the client models.
 */
class ContractConformanceTest {

    private val moshi = Moshi.Builder().build()

    private inline fun <reified T> parse(json: String): T =
        moshi.adapter(T::class.java).fromJson(json)!!

    @Test
    fun `server-info shape`() {
        val dto = parse<ServerInfoDto>(
            """{"server_id":"11111111-2222-4333-8444-555555555555",
                "vapid_public_key":"BCk...","contract":"notifications.v2"}"""
        )
        assertEquals("notifications.v2", dto.contract)
    }

    @Test
    fun `me recipient and publisher shapes`() {
        val recipient = parse<MeDto>("""{"scope":"recipient","user_id":"alice"}""")
        assertEquals("alice", recipient.userId)
        assertNull(recipient.appId)

        val publisher = parse<MeDto>("""{"scope":"publisher","app_id":"app-1"}""")
        assertEquals("app-1", publisher.appId)
        assertNull(publisher.userId)
    }

    @Test
    fun `every stable error code parses`() {
        val codes = listOf(
            "unauthorized", "scope_required", "invalid_request", "not_ready",
            "notification_expired", "already_responded", "idempotency_conflict",
            "challenge_not_current", "challenge_expired", "invalid_challenge",
            "subscription_not_found", "subscription_not_owned", "invalid_cursor"
        )
        for (code in codes) {
            assertEquals(code, parse<ErrorDto>("""{"error":"$code"}""").error)
        }
    }

    @Test
    fun `notification envelope with actions and explicit responded flag`() {
        val env = parse<EnvelopeDto>(
            """{"version":1,"revision":"42","kind":"notification",
                "state":{"notification":{
                    "id":"9007199254740993","title":"Hi","body":"Body",
                    "actions":[{"id":"a1","label":"OK","kind":"BUTTON"},
                               {"id":"a2","label":"Reply","kind":"TEXT_REPLY"}],
                    "expires_at":"2025-03-01T12:00:00.123456789Z","open_path":"/x",
                    "created_at":"2025-03-01T12:00:00Z",
                    "from_server":"11111111-2222-4333-8444-555555555555","from_app":"app-1"},
                    "responded":false}}"""
        )
        assertEquals(EnvelopeKind.NOTIFICATION, env.kind)
        assertEquals("9007199254740993", env.state!!.notification.id)
        assertFalse(env.state!!.responded)
        assertEquals(ActionKindDto.TEXT_REPLY, env.state!!.notification.actions[1].kind)
    }

    @Test
    fun `state_update envelope`() {
        val env = parse<EnvelopeDto>(
            """{"version":1,"revision":"43","kind":"state_update",
                "state":{"notification":{"id":"1","title":"T"},"responded":true}}"""
        )
        assertEquals(EnvelopeKind.STATE_UPDATE, env.kind)
        assertTrue(env.state!!.responded)
    }

    @Test
    fun `oversized notification reference fallback`() {
        val env = parse<EnvelopeDto>(
            """{"version":1,"revision":"44","kind":"notification",
                "reference":{"from_server":"11111111-2222-4333-8444-555555555555","notification_id":"7"}}"""
        )
        assertNull(env.state)
        assertEquals("7", env.reference!!.notificationId)
    }

    @Test
    fun `registration challenge envelope`() {
        val env = parse<EnvelopeDto>(
            """{"version":1,"kind":"registration_challenge",
                "installation_id":"inst-1","subscription_version":3,"challenge":"abc",
                "from_server":"11111111-2222-4333-8444-555555555555",
                "visible_notification":{"title":"Verify","body":"Code"}}"""
        )
        assertEquals(EnvelopeKind.REGISTRATION_CHALLENGE, env.kind)
        assertEquals(3, env.subscriptionVersion)
        assertEquals("Verify", env.visibleNotification!!.title)
    }

    @Test
    fun `inbox page shape`() {
        val page = parse<InboxPageDto>(
            """{"items":[{"revision":"10","state":{"notification":{"id":"5","title":"A"},
                "responded":false}}],"cursor":"10","has_more":true}"""
        )
        assertEquals(1, page.items.size)
        assertTrue(page.hasMore)
        assertEquals("5", page.items[0].state.notification.id)
    }

    @Test
    fun `notification state shape for GET by id`() {
        val state = parse<NotificationStateDto>(
            """{"notification":{"id":"2","title":"T"},"responded":false}"""
        )
        assertFalse(state.responded)
    }

    @Test
    fun `subscription status shape`() {
        val status = parse<SubscriptionStatusDto>(
            """{"installation_id":"inst-1","version":2,"status":"pending"}"""
        )
        assertEquals("pending", status.status)
        assertEquals(2, status.version)
    }

    @Test
    fun `subscription confirm returns active status`() {
        val status = parse<SubscriptionStatusDto>(
            """{"installation_id":"inst-1","version":2,"status":"active"}"""
        )
        assertEquals("active", status.status)
    }

    @Test
    fun `response receipt shape`() {
        val receipt = parse<com.example.data.remote.model.ResponseReceiptDto>(
            """{"response_id":"r-1","accepted_at":"2025-03-01T12:00:00.123456789Z"}"""
        )
        assertEquals("r-1", receipt.responseId)
    }
}
