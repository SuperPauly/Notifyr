package com.example.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActionItem
import com.example.data.model.ActionKind
import com.example.data.model.NotificationEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.NotificationItemCard
import com.example.ui.components.NotificationLazyColumn
import com.example.ui.components.TextReplyDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onNavigateToDetail: (fromServer: String, id: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state for text replies
    var activeReplyAction by remember {
        mutableStateOf<Pair<NotificationEntity, ActionItem>?>(null)
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notifyr Inbox",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (uiState.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text("${uiState.unreadCount}")
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.markAllAsRead() },
                        modifier = Modifier.testTag("mark_all_read_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Mark all as read"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.simulateNewNotification() },
                modifier = Modifier.testTag("simulate_notification_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.AddAlert,
                    contentDescription = "Simulate New Notification"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Denial Recovery Banner
            if (!hasNotificationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("notification_permission_banner"),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Notifications Blocked",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Action buttons, RemoteInput replies, and alerts cannot appear in your notification shade.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.testTag("btn_enable_permission_banner")
                        ) {
                            Text("Enable", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            val isFiltered = uiState.searchQuery.isNotEmpty() ||
                    uiState.selectedServerId != null ||
                    uiState.selectedApp != null ||
                    uiState.onlyAwaitingResponse ||
                    uiState.onlyUnread

            NotificationLazyColumn(
                notifications = uiState.notifications,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                searchPlaceholder = "Search by title or server source...",
                searchTestTag = "inbox_search_input",
                headerContent = {
                    // Horizontal Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Awaiting Response Toggle Chip
                        FilterChip(
                            selected = uiState.onlyAwaitingResponse,
                            onClick = { viewModel.toggleAwaitingResponse() },
                            label = {
                                Text(
                                    if (uiState.awaitingCount > 0) "Awaiting Response (${uiState.awaitingCount})"
                                    else "Awaiting Response"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_awaiting_response")
                        )

                        // Unread Only Chip
                        FilterChip(
                            selected = uiState.onlyUnread,
                            onClick = { viewModel.toggleOnlyUnread() },
                            label = { Text("Unread") },
                            modifier = Modifier.testTag("filter_unread")
                        )

                        // Server Filter Chips
                        FilterChip(
                            selected = uiState.selectedServerId == null,
                            onClick = { viewModel.setSelectedServer(null) },
                            label = { Text("All Servers") }
                        )

                        uiState.servers.forEach { server ->
                            FilterChip(
                                selected = uiState.selectedServerId == server.serverId,
                                onClick = {
                                    if (uiState.selectedServerId == server.serverId) {
                                        viewModel.setSelectedServer(null)
                                    } else {
                                        viewModel.setSelectedServer(server.serverId)
                                    }
                                },
                                label = { Text(server.serverId) },
                                modifier = Modifier.testTag("filter_server_${server.serverId}")
                            )
                        }

                        // Application Filter Chips
                        if (uiState.availableApps.isNotEmpty()) {
                            uiState.availableApps.forEach { appName ->
                                FilterChip(
                                    selected = uiState.selectedApp == appName,
                                    onClick = {
                                        if (uiState.selectedApp == appName) {
                                            viewModel.setSelectedApp(null)
                                        } else {
                                            viewModel.setSelectedApp(appName)
                                        }
                                    },
                                    label = { Text(appName) },
                                    modifier = Modifier.testTag("filter_app_$appName")
                                )
                            }
                        }
                    }
                },
                onNotificationClick = { notification ->
                    viewModel.markAsRead(notification.fromServer, notification.id, true)
                    onNavigateToDetail(notification.fromServer, notification.id)
                },
                onActionClick = { notification, action ->
                    if (action.kind == ActionKind.TEXT_REPLY) {
                        activeReplyAction = Pair(notification, action)
                    } else {
                        viewModel.submitAction(notification, action)
                    }
                },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                emptyContent = {
                    EmptyStateView(
                        icon = Icons.Default.NotificationsNone,
                        title = if (isFiltered) "No Matching Notifications" else "Inbox is Clear",
                        message = if (isFiltered) {
                            "Try adjusting your active filters or clear search query."
                        } else {
                            "All notifications handled. Tap the + icon to simulate an incoming alert."
                        },
                        actionLabel = if (isFiltered) "Reset Filters" else "Simulate Notification",
                        onActionClick = {
                            if (isFiltered) {
                                viewModel.setSearchQuery("")
                                viewModel.setSelectedServer(null)
                                viewModel.setSelectedApp(null)
                                if (uiState.onlyAwaitingResponse) viewModel.toggleAwaitingResponse()
                                if (uiState.onlyUnread) viewModel.toggleOnlyUnread()
                            } else {
                                viewModel.simulateNewNotification()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("inbox_notifications_list")
            )
        }
    }

    // Text reply dialog when user interacts with a text action
    activeReplyAction?.let { (notif, action) ->
        TextReplyDialog(
            notification = notif,
            action = action,
            onDismiss = { activeReplyAction = null },
            onSubmitReply = { replyText ->
                viewModel.submitAction(notif, action, replyText)
                activeReplyAction = null
            }
        )
    }
}
