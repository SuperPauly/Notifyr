package com.example.data.response

import com.example.data.model.ActionKind
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6.1: Response wire format validation.
 * Tests button responses omit text key entirely, TEXT_REPLY enforces UTF-8 byte limits,
 * and action_id must exist in notification's actions.
 */
class ResponseWireFormatTest {

    @Test
    fun `BUTTON response serializes without text key`() {
        // Given: button action (no text)
        val actionId = "action-1"
        val actionKind = ActionKind.BUTTON
        val replyText: String? = null
        
        // When: serialize to wire format
        val wireFormat = serializeResponse(
            actionId = actionId,
            actionKind = actionKind,
            replyText = replyText
        )
        
        // Then: text key must be absent (not null, not empty string)
        assertNotNull("Wire format should be generated", wireFormat)
        assertFalse("Button response must not contain 'text' key", wireFormat!!.containsKey("text"))
        assertEquals(actionId, wireFormat["action_id"])
    }

    @Test
    fun `TEXT_REPLY requires non-blank text`() {
        // Given: text reply action with blank text
        val actionId = "action-reply"
        val actionKind = ActionKind.TEXT_REPLY
        
        // When/Then: blank text is rejected
        assertNull("Blank text should be rejected", 
            serializeResponse(actionId, actionKind, ""))
        assertNull("Whitespace-only text should be rejected", 
            serializeResponse(actionId, actionKind, "   "))
        assertNull("Null text for TEXT_REPLY should be rejected", 
            serializeResponse(actionId, actionKind, null))
    }

    @Test
    fun `TEXT_REPLY enforces 4096 UTF-8 byte limit`() {
        // Given: text at/near byte limit
        val actionId = "action-reply"
        val actionKind = ActionKind.TEXT_REPLY
        
        // 4096 ASCII bytes (1 byte per char)
        val exactly4096 = "a".repeat(4096)
        val wireFormat4096 = serializeResponse(actionId, actionKind, exactly4096)
        assertNotNull("4096 ASCII bytes should be accepted", wireFormat4096)
        
        // 4097 ASCII bytes exceeds limit
        val exactly4097 = "a".repeat(4097)
        assertNull("4097 bytes should be rejected", 
            serializeResponse(actionId, actionKind, exactly4097))
    }

    @Test
    fun `TEXT_REPLY enforces UTF-8 byte limit not character count`() {
        // Given: CJK characters (3 bytes each in UTF-8)
        val actionId = "action-reply"
        val actionKind = ActionKind.TEXT_REPLY
        
        // 1365 CJK chars = 4095 bytes (under limit)
        val cjk1365 = "한".repeat(1365) // 한 = 3 bytes in UTF-8
        val wireFormat1365 = serializeResponse(actionId, actionKind, cjk1365)
        assertNotNull("1365 CJK chars (4095 bytes) should be accepted", wireFormat1365)
        
        // 1366 CJK chars = 4098 bytes (over limit)
        val cjk1366 = "한".repeat(1366)
        assertNull("1366 CJK chars (4098 bytes) should be rejected", 
            serializeResponse(actionId, actionKind, cjk1366))
        
        // 2000 CJK chars = 6000 bytes (way over limit)
        val cjk2000 = "한".repeat(2000)
        assertNull("2000 CJK chars (6000 bytes) should be rejected", 
            serializeResponse(actionId, actionKind, cjk2000))
    }

    @Test
    fun `action_id must exist in notification actions`() {
        // Given: notification with specific action_ids
        val validActions = setOf("action-yes", "action-no", "action-reply")
        
        // When/Then: valid action_id accepted
        val validResult = validateActionId("action-yes", validActions)
        assertTrue("Valid action_id should be accepted", validResult)
        
        // When/Then: unknown action_id rejected
        val invalidResult = validateActionId("action-unknown", validActions)
        assertFalse("Unknown action_id should be rejected", invalidResult)
    }

    // --- Implementations using production code ---

    private fun serializeResponse(
        actionId: String,
        actionKind: ActionKind,
        replyText: String?
    ): Map<String, Any>? {
        return ResponseSerializer.serializeResponse(actionId, actionKind, replyText)
    }

    private fun validateActionId(actionId: String, validActions: Set<String>): Boolean {
        return ResponseValidator.validateActionId(actionId, validActions)
    }
}
