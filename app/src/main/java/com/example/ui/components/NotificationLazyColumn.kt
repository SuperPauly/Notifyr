package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity

/**
 * Status indicator descriptor for notification cards.
 */
data class NotificationStatusInfo(
    val icon: ImageVector,
    val iconTint: Color,
    val label: String?,
    val containerColor: Color,
    val contentDescription: String
)

/**
 * Reusable LazyColumn component that displays a scrollable list of notification cards
 * with rich status icons, content snippets, metadata badges, and interactive action controls.
 */
@Composable
fun NotificationLazyColumn(
    notifications: List<NotificationEntity>,
    onNotificationClick: (NotificationEntity) -> Unit,
    onActionClick: (NotificationEntity, ActionItem) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    emptyContent: @Composable () -> Unit = {
        EmptyStateView(
            icon = Icons.Default.NotificationsNone,
            title = "No Notifications",
            message = "No notifications to display in this list."
        )
    }
) {
    if (notifications.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            emptyContent()
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("notification_lazy_column"),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = notifications,
                key = { "${it.fromServer}_${it.id}" }
            ) { notification ->
                NotificationSnippetCard(
                    notification = notification,
                    onClick = { onNotificationClick(notification) },
                    onActionClick = { action -> onActionClick(notification, action) },
                    modifier = Modifier.testTag("notification_snippet_card_${notification.fromServer}_${notification.id}")
                )
            }
        }
    }
}

/**
 * Notification card component showing a snippet of content, provenance, timestamp,
 * and comprehensive status icons indicating read/unread, responded, and expiration state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSnippetCard(
    notification: NotificationEntity,
    onClick: () -> Unit,
    onActionClick: (ActionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusInfo = resolveStatusInfo(notification)

    val cardElevation = if (!notification.isRead) 2.dp else 0.5.dp
    val containerColor = if (!notification.isRead) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Status Icon + Badges + Relative Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Status Icon with background container
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(statusInfo.containerColor)
                            .testTag("status_icon_box_${notification.fromServer}_${notification.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusInfo.icon,
                            contentDescription = statusInfo.contentDescription,
                            tint = statusInfo.iconTint,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    ServerBadge(serverId = notification.fromServer)
                    Spacer(modifier = Modifier.width(6.dp))
                    AppChip(appName = notification.fromApp)
                }

                Text(
                    text = DateTimeUtils.formatRelative(notification.createdAt),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title Snippet
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("snippet_title_${notification.fromServer}_${notification.id}")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Body Snippet (truncated preview)
            Text(
                text = notification.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                modifier = Modifier.testTag("snippet_body_${notification.fromServer}_${notification.id}")
            )

            // Status Pill (Responded, Expired, Awaiting Action)
            if (statusInfo.label != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusInfo.containerColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = statusInfo.icon,
                        contentDescription = null,
                        tint = statusInfo.iconTint,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusInfo.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = statusInfo.iconTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Interactive Action Buttons (only visible if awaiting response and not expired)
            if (notification.isAwaitingResponse && notification.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    notification.actions.forEach { action ->
                        if (action.kind == ActionKind.BUTTON) {
                            ElevatedButton(
                                onClick = { onActionClick(action) },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("card_action_btn_${action.id}"),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onActionClick(action) },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("card_action_btn_${action.id}"),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves appropriate icon, color, and description based on notification state.
 */
@Composable
private fun resolveStatusInfo(notification: NotificationEntity): NotificationStatusInfo {
    return when {
        notification.isResponded -> {
            NotificationStatusInfo(
                icon = Icons.Default.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                label = "Responded: ${notification.respondedText ?: "Sent"}",
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentDescription = "Notification answered"
            )
        }
        notification.isExpired -> {
            NotificationStatusInfo(
                icon = Icons.Default.Timer,
                iconTint = MaterialTheme.colorScheme.error,
                label = "Expired",
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                contentDescription = "Notification expired"
            )
        }
        notification.isAwaitingResponse -> {
            NotificationStatusInfo(
                icon = Icons.Default.PendingActions,
                iconTint = MaterialTheme.colorScheme.tertiary,
                label = "Action Required",
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                contentDescription = "Notification awaiting action"
            )
        }
        !notification.isRead -> {
            NotificationStatusInfo(
                icon = Icons.Default.MarkEmailUnread,
                iconTint = MaterialTheme.colorScheme.primary,
                label = null,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentDescription = "Unread notification"
            )
        }
        else -> {
            NotificationStatusInfo(
                icon = Icons.Default.Done,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = null,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentDescription = "Read notification"
            )
        }
    }
}
