package com.example.data.unifiedpush

import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import org.json.JSONArray
import org.json.JSONObject

object UnifiedPushPayloadDecoder {

    /**
     * Decodes and validates an incoming raw push message byte array or string.
     * Binds strictly to the registered server profile to prevent destination spoofing.
     * Rejects malformed messages safely without crashing or exposing contents.
     */
    fun decodePayload(
        rawBytes: ByteArray?,
        rawString: String?,
        server: ServerEntity
    ): NotificationEntity? {
        val jsonString = when {
            rawBytes != null && rawBytes.isNotEmpty() -> {
                try {
                    String(rawBytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    return null
                }
            }
            !rawString.isNullOrBlank() -> rawString
            else -> return null
        }

        return try {
            val json = JSONObject(jsonString)

            // ID: Positive Kotlin Long corresponding to protobuf int64
            val id = json.optLong("id", -1L)
            if (id <= 0L) {
                // Must be positive Long
                return null
            }

            val title = json.optString("title", "").trim()
            if (title.isBlank()) return null

            val body = json.optString("body", "").trim()
            val fromApp = json.optString("from_app", "unknown-service").trim()

            // Bind to the registered server's serverId rather than trusting remote payload
            val fromServer = server.serverId

            val createdAt = json.optLong("created_at", System.currentTimeMillis())
            val expiresAt = if (json.has("expires_at")) json.optLong("expires_at") else null
            val openPath = if (json.has("open_path")) json.optString("open_path").takeIf { it.isNotBlank() } else null

            val actions = mutableListOf<ActionItem>()
            val actionsArray = json.optJSONArray("actions")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val actionObj = actionsArray.optJSONObject(i) ?: continue
                    val actId = actionObj.optString("id", "").trim()
                    val label = actionObj.optString("label", "").trim()
                    val kindStr = actionObj.optString("kind", ActionKind.BUTTON.name)
                    val kind = try {
                        ActionKind.valueOf(kindStr)
                    } catch (_: Exception) {
                        ActionKind.BUTTON
                    }

                    if (actId.isNotBlank() && label.isNotBlank()) {
                        actions.add(ActionItem(actId, label, kind))
                    }
                }
            }

            NotificationEntity(
                id = id,
                createdAt = createdAt,
                fromServer = fromServer,
                fromApp = fromApp,
                title = title,
                body = body,
                actions = actions,
                expiresAt = expiresAt,
                openPath = openPath,
                isRead = false,
                isResponded = false
            )
        } catch (_: Exception) {
            // Malformed JSON is safely rejected without crash
            null
        }
    }
}
