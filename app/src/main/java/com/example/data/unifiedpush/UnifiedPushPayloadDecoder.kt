package com.example.data.unifiedpush

import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import com.example.data.remote.model.ActionDto
import com.example.data.remote.model.ActionKindDto
import com.example.data.remote.model.DecimalInt64
import com.example.data.remote.model.EnvelopeDto
import com.example.data.remote.model.EnvelopeKind
import com.example.data.remote.model.NotificationDto
import com.example.data.remote.model.NotificationStateDto
import com.example.data.remote.model.Rfc3339Instant
import com.squareup.moshi.Moshi

/**
 * Result of parsing one UnifiedPush message. A malformed or untrusted message yields
 * [Ignored] with a stable reason code rather than a null, so callers branch exhaustively
 * and diagnostics never leak payload contents.
 *
 * Every variant is bound to the locally registered [connectionId]/[fromServer]; a push
 * can never address a server the device has not registered.
 */
sealed interface DecodeResult {
    /** `kind=notification` carrying an inline [state]. */
    data class Notification(
        val state: NotificationStateDto,
        val revision: String,
        val fromServer: String,
        val connectionId: String
    ) : DecodeResult

    /** `kind=notification` carrying only a reference; the full state must be fetched. */
    data class Reference(
        val fromServer: String,
        val notificationId: String,
        val revision: String?,
        val connectionId: String
    ) : DecodeResult

    /** `kind=state_update`; a revision bump on an already-known notification. */
    data class StateUpdate(
        val state: NotificationStateDto,
        val revision: String,
        val fromServer: String,
        val connectionId: String
    ) : DecodeResult

    /** `kind=registration_challenge`; rendered locally to satisfy `userVisibleOnly`. */
    data class RegistrationChallenge(
        val installationId: String,
        val subscriptionVersion: Int,
        val challenge: String,
        val visibleTitle: String,
        val visibleBody: String,
        val fromServer: String,
        val connectionId: String
    ) : DecodeResult

    /** Malformed, unsupported, or untrusted message. [reason] is a stable enum name. */
    data class Ignored(val reason: Reason) : DecodeResult

    enum class Reason {
        EMPTY_PAYLOAD,
        NOT_JSON,
        UNSUPPORTED_VERSION,
        STATE_REFERENCE_BOTH,
        NEITHER_STATE_NOR_REFERENCE,
        NO_REVISION,
        CHALLENGE_SHAPE_INVALID,
        SERVER_MISMATCH,
        INVALID_NOTIFICATION_ID,
        INVALID_TIMESTAMP
    }
}

/**
 * Parses the notifications.v2 UnifiedPush envelope. Accepts only `version == 1` and
 * enforces the `state` XOR `reference` XOR challenge-fields shape per [EnvelopeKind].
 */
object UnifiedPushPayloadDecoder {

    private val moshi: Moshi = Moshi.Builder().build()
    private val envelopeAdapter = moshi.adapter(EnvelopeDto::class.java)

    fun decodePayload(
        rawBytes: ByteArray?,
        rawString: String?,
        server: ServerEntity
    ): DecodeResult {
        val json = when {
            rawBytes != null && rawBytes.isNotEmpty() -> String(rawBytes, Charsets.UTF_8).trim()
            !rawString.isNullOrBlank() -> rawString.trim()
            else -> return DecodeResult.Ignored(DecodeResult.Reason.EMPTY_PAYLOAD)
        }

        val envelope = try {
            envelopeAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return DecodeResult.Ignored(DecodeResult.Reason.NOT_JSON)

        if (envelope.version != 1) {
            return DecodeResult.Ignored(DecodeResult.Reason.UNSUPPORTED_VERSION)
        }

        val boundServer = server.serverId
        val connectionId = server.connectionId

        if (envelope.fromServer != null && envelope.fromServer != boundServer) {
            return DecodeResult.Ignored(DecodeResult.Reason.SERVER_MISMATCH)
        }

        return try {
            when (envelope.kind) {
                EnvelopeKind.NOTIFICATION -> decodeNotification(envelope, boundServer, connectionId)
                EnvelopeKind.STATE_UPDATE -> decodeStateUpdate(envelope, boundServer, connectionId)
                EnvelopeKind.REGISTRATION_CHALLENGE -> decodeChallenge(envelope, boundServer, connectionId)
            }
        } catch (_: Exception) {
            DecodeResult.Ignored(DecodeResult.Reason.NOT_JSON)
        }
    }

    private fun decodeNotification(
        envelope: EnvelopeDto,
        fromServer: String,
        connectionId: String
    ): DecodeResult {
        val state = envelope.state
        val reference = envelope.reference

        if (state != null && reference != null) {
            return DecodeResult.Ignored(DecodeResult.Reason.STATE_REFERENCE_BOTH)
        }
        if (state != null) {
            val revision = envelope.revision
                ?: return DecodeResult.Ignored(DecodeResult.Reason.NO_REVISION)
            return DecodeResult.Notification(state, revision, fromServer, connectionId)
        }
        if (reference != null) {
            if (reference.fromServer != fromServer) {
                return DecodeResult.Ignored(DecodeResult.Reason.SERVER_MISMATCH)
            }
            if (reference.notificationId.toLongOrNull()?.let { it > 0L } != true) {
                return DecodeResult.Ignored(DecodeResult.Reason.INVALID_NOTIFICATION_ID)
            }
            return DecodeResult.Reference(fromServer, reference.notificationId, envelope.revision, connectionId)
        }
        return DecodeResult.Ignored(DecodeResult.Reason.NEITHER_STATE_NOR_REFERENCE)
    }

    private fun decodeStateUpdate(
        envelope: EnvelopeDto,
        fromServer: String,
        connectionId: String
    ): DecodeResult {
        val state = envelope.state
            ?: return DecodeResult.Ignored(DecodeResult.Reason.NEITHER_STATE_NOR_REFERENCE)
        if (envelope.reference != null) {
            return DecodeResult.Ignored(DecodeResult.Reason.STATE_REFERENCE_BOTH)
        }
        val revision = envelope.revision
            ?: return DecodeResult.Ignored(DecodeResult.Reason.NO_REVISION)
        return DecodeResult.StateUpdate(state, revision, fromServer, connectionId)
    }

    private fun decodeChallenge(
        envelope: EnvelopeDto,
        fromServer: String,
        connectionId: String
    ): DecodeResult {
        val installationId = envelope.installationId
        val subscriptionVersion = envelope.subscriptionVersion
        val challenge = envelope.challenge
        val visible = envelope.visibleNotification

        if (installationId.isNullOrBlank() || subscriptionVersion == null ||
            challenge.isNullOrBlank() || visible == null ||
            envelope.state != null || envelope.reference != null
        ) {
            return DecodeResult.Ignored(DecodeResult.Reason.CHALLENGE_SHAPE_INVALID)
        }

        return DecodeResult.RegistrationChallenge(
            installationId = installationId,
            subscriptionVersion = subscriptionVersion,
            challenge = challenge,
            visibleTitle = visible.title,
            visibleBody = visible.body,
            fromServer = fromServer,
            connectionId = connectionId
        )
    }

    /**
     * Maps a parsed [NotificationDto] onto the persisted [NotificationEntity], converting
     * RFC3339 timestamps to epoch millis exactly at this storage boundary.
     */
    fun toEntity(
        dto: NotificationDto,
        revision: String,
        fromServer: String
    ): NotificationEntity {
        val createdAt = dto.createdAt?.let { Rfc3339Instant.parse(it).toEpochMilli() }
            ?: System.currentTimeMillis()
        val expiresAt = dto.expiresAt?.let { Rfc3339Instant.parse(it).toEpochMilli() }
        return NotificationEntity(
            id = DecimalInt64.parse(dto.id).also { require(it > 0L) },
            createdAt = createdAt,
            fromServer = fromServer,
            fromApp = dto.fromApp.ifBlank { "unknown-service" },
            title = dto.title,
            body = dto.body,
            actions = dto.actions.map(::toActionItem),
            expiresAt = expiresAt,
            openPath = dto.openPath.ifBlank { null },
            revision = revision
        )
    }

    private fun toActionItem(dto: ActionDto): ActionItem = ActionItem(
        id = dto.id,
        label = dto.label,
        kind = when (dto.kind) {
            ActionKindDto.BUTTON -> ActionKind.BUTTON
            ActionKindDto.TEXT_REPLY -> ActionKind.TEXT_REPLY
        }
    )
}
