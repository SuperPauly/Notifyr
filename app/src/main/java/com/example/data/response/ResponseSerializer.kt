package com.example.data.response

import com.example.data.model.ActionKind

/**
 * Phase 6.2: Response wire format serialization.
 * Implements POST /v1/responses request body construction per API contract.
 */
object ResponseSerializer {

    /**
     * Serializes a response to wire format for POST /v1/responses.
     * 
     * @return Map representing JSON body, or null if validation fails
     */
    fun serializeResponse(
        actionId: String,
        actionKind: ActionKind,
        replyText: String?
    ): Map<String, Any>? {
        // TEXT_REPLY: require non-blank text under 4096 UTF-8 bytes
        if (actionKind == ActionKind.TEXT_REPLY) {
            val trimmed = replyText?.trim()
            if (trimmed.isNullOrBlank()) return null
            
            val utf8Bytes = trimmed.toByteArray(Charsets.UTF_8)
            if (utf8Bytes.size > 4096) return null
            
            return mapOf(
                "action_id" to actionId,
                "text" to trimmed
            )
        }
        
        // BUTTON: omit text key entirely (not null, not empty)
        return mapOf("action_id" to actionId)
    }
}
