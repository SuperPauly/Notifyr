package com.example.data.response

import com.example.data.model.ActionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6.1: Response wire format validation.
 * Button responses omit text entirely, TEXT_REPLY enforces UTF-8 byte limits,
 * and action_id must exist in the notification's available actions.
 */
class ResponseWireFormatTest {

    @Test
    fun `BUTTON response serializes without text key`() {
        val actionId = "action-1"

        val wireFormat = ResponseSerializer.serializeResponse(
            actionId = actionId,
            actionKind = ActionKind.BUTTON,
            replyText = null
        )

        assertNotNull("Wire format should be generated", wireFormat)
        assertFalse("Button response must not contain 'text' key", wireFormat!!.containsKey("text"))
        assertEquals(actionId, wireFormat["action_id"])
    }

    @Test
    fun `TEXT_REPLY rejects blank text`() {
        val actionId = "action-reply"

        assertNull("Empty text should be rejected", ResponseSerializer.serializeResponse(actionId, ActionKind.TEXT_REPLY, ""))
        assertNull("Whitespace-only text should be rejected", ResponseSerializer.serializeResponse(actionId, ActionKind.TEXT_REPLY, "   "))
        assertNull("Null text should be rejected", ResponseSerializer.serializeResponse(actionId, ActionKind.TEXT_REPLY, null))
    }

    @Test
    fun `TEXT_REPLY accepts valid text with trimmed wire value`() {
        val actionId = "action-reply"

        val wireFormat = ResponseSerializer.serializeResponse(
            actionId = actionId,
            actionKind = ActionKind.TEXT_REPLY,
            replyText = "  Acknowledged  "
        )

        assertNotNull("Valid text should be accepted", wireFormat)
        assertEquals(actionId, wireFormat!!["action_id"])
        assertEquals("Acknowledged", wireFormat["text"])
    }

    @Test
    fun `TEXT_REPLY enforces 4096 UTF-8 byte limit`() {
        val actionId = "action-reply"

        val exactly4096 = "a".repeat(4096)
        assertNotNull("4096 ASCII bytes should be accepted", ResponseSerializer.serializeResponse(actionId, ActionKind.TEXT_REPLY, exactly4096))

        val exactly4097 = "a".repeat(4097)
        assertNull("4097 bytes should be rejected", ResponseSerializer.serializeResponse(actionId, ActionKind.TEXT_REPLY, exactly4097))
    }

    @Test
    fun `TEXT_REPLY enforces UTF-8 byte limit not character count`() {
        val actionId = "action-reply"

        val cjk1365 = "한".repeat(1365)
        assertNotNull("1365 CJK chars (4095 bytes) should be accepted", ResponseSerializer.serializeResponse(actionId, ActionKind.TEXT_REPLY, cjk1365))

        val cjk1366 = "한".repeat(1366)
        assertNull("1366 CJK chars (4098 bytes) should be rejected", ResponseSerializer.serializeResponse(actionId, ActionKind.TEXT_REPLY, cjk1366))
    }

    @Test
    fun `action_id validator accepts known action and rejects unknown`() {
        val validActions = setOf("action-yes", "action-no", "action-reply")

        assertTrue("Known action should be accepted", ResponseValidator.validateActionId("action-yes", validActions))
        assertFalse("Unknown action should be rejected", ResponseValidator.validateActionId("action-unknown", validActions))
    }
}
