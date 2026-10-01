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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * with rich status icons, content snippets, metadata badges, interactive action controls,
 * an integrated search bar at the top allowing users to filter by title or server source,
 * and a pull-to-refresh mechanism for manual re-fetching and updating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationLazyColumn(
    notifications: List<NotificationEntity>,
    onNotificationClick: (NotificationEntity) -> Unit,
    onActionClick: (NotificationEntity, ActionItem) -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    showSearchBar: Boolean = true,
    searchPlaceholder: String = "Filter by title or server source...",
    searchTestTag: String = "notification_search_input",
    headerContent: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    emptyContent: @Composable () -> Unit = {
        EmptyStateView(
            icon = Icons.Default.NotificationsNone,
            title = "No Notifications",
            message = "No notifications match your search or filters."
        )
    }
) {
    var internalQuery by rememberSaveable { mutableStateOf("") }
    val currentQuery = searchQuery ?: internalQuery
    val onQueryChange: (String) -> Unit = onSearchQueryChange ?: { internalQuery = it }

    val displayedNotifications = remember(notifications, currentQuery, onSearchQueryChange) {
        if (onSearchQueryChange == null && currentQuery.isNotBlank()) {
            val q = currentQuery.trim()
            notifications.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.fromServer.contains(q, ignoreCase = true)
            }
        } else {
            notifications
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (showSearchBar) {
            NotificationSearchBar(
                query = currentQuery,
                onQueryChange = onQueryChange,
                placeholder = searchPlaceholder,
                testTag = searchTestTag,
                totalCount = notifications.size,
                filteredCount = displayedNotifications.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("notification_search_bar")
            )
        }

        headerContent?.invoke()

        val listContent: @Composable () -> Unit = {
            if (displayedNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    emptyContent()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notification_lazy_column"),
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = displayedNotifications,
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

        if (onRefresh != null) {
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("notification_pull_to_refresh")
            ) {
                listContent()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                listContent()
            }
        }
    }
}

/**
 * Clean search bar component embedded at the top of the LazyColumn list,
 * allowing instant filtering by notification title or server source.
 */
@Composable
fun NotificationSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "notification_search_input",
    totalCount: Int = 0,
    filteredCount: Int = 0
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search notifications by title or server",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search query"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (query.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtering title/server: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\"$query\"",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (totalCount > 0) {
                    Text(
                        text = " ($filteredCount of $totalCount)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
