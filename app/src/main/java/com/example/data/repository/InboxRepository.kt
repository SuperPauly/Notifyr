package com.example.data.repository

import android.content.Context
import com.example.data.local.NotificationDao
import com.example.data.local.ServerDao
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import com.example.data.remote.model.InboxItemDto
import com.example.data.remote.model.NotificationStateDto
import com.example.data.transport.InboxTransport
import com.example.data.unifiedpush.DecodeResult
import com.example.data.unifiedpush.UnifiedPushPayloadDecoder
import com.example.ui.notifications.NotificationHelper

/**
 * Persists the notifications.v2 inbox into Room.
 *
 * Two identities advance independently:
 * - a per-connection **cursor** (`servers.inbox_cursor`), advanced only after a whole page
 *   has committed, so a crash mid-page replays that page instead of skipping it;
 * - a per-notification **revision** (int64 as a decimal string), advanced only when the
 *   incoming revision is strictly greater than the stored one.
 */
class InboxRepository(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val serverDao: ServerDao,
    private val transport: InboxTransport
) {

    companion object {
        const val DEFAULT_CURSOR = "0"
        const val PAGE_LIMIT = 50

        /** int64-equivalent comparison of two decimal revision strings; both must be non-negative. */
        fun revisionIsNewer(incoming: String, stored: String?): Boolean {
            if (stored == null) return true
            val a = incoming.toLongOrNull() ?: return false
            val b = stored.toLongOrNull() ?: return true
            return a > b
        }
    }

    /**
     * Pumps `GET /v1/inbox` from the stored cursor until `has_more` is false. Each page is
     * applied and its cursor advanced in one transaction. An `invalid_cursor` 422 resets the
     * cursor to [DEFAULT_CURSOR] and retries once, self-healing a server-side cursor reset.
     */
    suspend fun refresh(server: ServerEntity) {
        var cursor = serverDao.getCursor(server.connectionId) ?: DEFAULT_CURSOR
        var selfHealed = false

        while (true) {
            val result = transport.fetchInboxPage(server, cursor, PAGE_LIMIT)

            if (result.invalidCursor) {
                if (selfHealed) return
                selfHealed = true
                cursor = DEFAULT_CURSOR
                serverDao.upsertCursor(server.connectionId, cursor)
                continue
            }

            val page = result.page ?: return

            applyPage(server, page.items, page.cursor)

            if (!page.hasMore) return
            cursor = page.cursor
        }
    }

    /**
     * Writes one page and advances the cursor atomically. Items whose revision is not newer than
     * the stored one are dropped so a replayed page cannot overwrite newer local state. An empty
     * page leaves the cursor unchanged so the same position is retried rather than skipped.
     */
    suspend fun applyPage(server: ServerEntity, items: List<InboxItemDto>, cursor: String) {
        val entities = ArrayList<NotificationEntity>(items.size)
        for (item in items) {
            val id = item.state.notification.id.toLongOrNull() ?: continue
            if (!revisionIsNewer(item.revision, notificationDao.revisionOf(server.serverId, id))) continue
            entities.add(UnifiedPushPayloadDecoder.toEntity(item.state.notification, item.revision, server.serverId))
        }
        val advanceTo = if (items.isEmpty()) serverDao.getCursor(server.connectionId) ?: DEFAULT_CURSOR else cursor

        // No alerting here: inbox pages are background reconciliation, not fresh user-facing events.
        notificationDao.applyPageAndAdvanceCursor(server.connectionId, entities, advanceTo)
    }

    /** Inline `kind=notification` push: persist and alert once, honouring revision ordering. */
    suspend fun applyInlineNotification(decoded: DecodeResult.Notification): NotificationEntity? {
        val entity = runCatching {
            UnifiedPushPayloadDecoder.toEntity(
                dto = decoded.state.notification,
                revision = decoded.revision,
                fromServer = decoded.fromServer
            )
        }.getOrNull() ?: return null

        if (!revisionIsNewer(decoded.revision, notificationDao.revisionOf(entity.fromServer, entity.id))) {
            return null
        }

        NotificationStateApplier.apply(notificationDao, entity, decoded.state)
        NotificationHelper.postNotification(context, entity, decoded.connectionId)
        return entity
    }

    /** `kind=state_update` push: merge state, drop resolved actions, never alert. */
    suspend fun applyStateUpdate(decoded: DecodeResult.StateUpdate): Boolean {
        val id = decoded.state.notification.id.toLongOrNull() ?: return false
        val stored = notificationDao.getNotificationDirect(decoded.fromServer, id) ?: return false

        if (!revisionIsNewer(decoded.revision, stored.revision)) return false

        val entity = runCatching {
            UnifiedPushPayloadDecoder.toEntity(
                dto = decoded.state.notification,
                revision = decoded.revision,
                fromServer = decoded.fromServer
            )
        }.getOrNull() ?: return false

        NotificationStateApplier.apply(notificationDao, entity, decoded.state)
        return true
    }

    /** `kind=notification` reference push: fetch the full state by id then treat as inline. */
    suspend fun resolveReference(decoded: DecodeResult.Reference): Boolean {
        val server = serverDao.getServerByConnectionIdDirect(decoded.connectionId)
            ?: serverDao.getServerByServerIdDirect(decoded.fromServer)
            ?: return false

        val fetch = transport.fetchNotification(server, decoded.notificationId, decoded.fromServer)
        val state = fetch.state ?: return false
        val revision = decoded.revision

        val entity = runCatching {
            UnifiedPushPayloadDecoder.toEntity(state.notification, revision ?: "0", decoded.fromServer)
        }.getOrNull() ?: return false

        if (revision != null &&
            !revisionIsNewer(revision, notificationDao.revisionOf(entity.fromServer, entity.id))
        ) {
            return false
        }

        NotificationStateApplier.apply(notificationDao, entity, state)
        NotificationHelper.postNotification(context, entity, decoded.connectionId)
        return true
    }
}

/**
 * Applies a [NotificationStateDto] over the persisted row without clobbering local read/respond
 * state, and removes actions once the notification is answered.
 */
internal object NotificationStateApplier {
    suspend fun apply(
        dao: NotificationDao,
        entity: NotificationEntity,
        state: NotificationStateDto
    ) {
        val existing = dao.getNotificationDirect(entity.fromServer, entity.id)

        val merged = entity.copy(
            isRead = existing?.isRead ?: false,
            isResponded = state.responded || (existing?.isResponded ?: false),
            respondedActionId = existing?.respondedActionId,
            respondedText = existing?.respondedText,
            respondedAt = existing?.respondedAt,
            actions = if (state.responded || existing?.isResponded == true) emptyList() else entity.actions
        )

        dao.insertNotification(merged)
    }
}
