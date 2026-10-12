package com.example.data.response

/**
 * Phase 6.2: Response validation.
 * Validates action_id exists in notification's available actions.
 */
object ResponseValidator {

    /**
     * Validates action_id is present in the notification's action set.
     * 
     * @param actionId Action ID from user response
     * @param validActions Set of action IDs from the notification
     * @return true if valid, false otherwise
     */
    fun validateActionId(actionId: String, validActions: Set<String>): Boolean {
        return actionId in validActions
    }
}
