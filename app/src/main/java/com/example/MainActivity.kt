package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.detail.NotificationDetailScreen
import com.example.ui.detail.NotificationDetailViewModel
import com.example.ui.inbox.InboxScreen
import com.example.ui.inbox.InboxViewModel
import com.example.ui.notifications.NotificationHelper
import com.example.ui.outbox.OutboxScreen
import com.example.ui.outbox.OutboxViewModel
import com.example.ui.servers.ServersScreen
import com.example.ui.servers.ServersViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.NotifyrTheme

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Inbox : Screen("inbox", "Inbox", Icons.Filled.Inbox, Icons.Outlined.Inbox, "nav_inbox")
    data object Outbox : Screen("outbox", "Outbox", Icons.Filled.Outbox, Icons.Outlined.Outbox, "nav_outbox")
    data object Servers : Screen("servers", "Servers", Icons.Filled.Dns, Icons.Outlined.Dns, "nav_servers")
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {

    private val pendingDeepLink = mutableStateOf<Pair<String, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NotifyrApplication
        extractNotificationDeepLink(intent)

        setContent {
            val userPrefs by app.preferencesRepository.userPreferences
                .collectAsStateWithLifecycle(initialValue = com.example.data.repository.UserPreferences())

            NotifyrTheme(themeMode = userPrefs.themeMode) {
                NotifyrMainApp(
                    app = app,
                    deepLink = pendingDeepLink.value,
                    onDeepLinkConsumed = {
                        pendingDeepLink.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationDeepLink(intent)
    }

    private fun extractNotificationDeepLink(intent: Intent?) {
        if (intent == null) return
        val server = intent.getStringExtra(NotificationHelper.EXTRA_FROM_SERVER)
        val notifId = intent.getLongExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1L)
        if (!server.isNullOrBlank() && notifId > 0L) {
            pendingDeepLink.value = Pair(server, notifId)
        }
    }
}

@Composable
fun NotifyrMainApp(
    app: NotifyrApplication,
    deepLink: Pair<String, Long>?,
    onDeepLinkConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    // Notification Permission Rationale Dialog State
    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                showPermissionRationale = true
            }
        }
    }

    // Direct navigation when tapping a notification
    LaunchedEffect(deepLink) {
        deepLink?.let { (serverId, notifId) ->
            navController.navigate("detail/${Uri.encode(serverId)}/$notifId")
            onDeepLinkConsumed()
        }
    }

    val unreadCount by app.notificationRepository.unreadCount
        .collectAsStateWithLifecycle(initialValue = 0)
    val awaitingCount by app.notificationRepository.awaitingResponseCount
        .collectAsStateWithLifecycle(initialValue = 0)
    val outboxPendingCount by app.outboxRepository.pendingCount
        .collectAsStateWithLifecycle(initialValue = 0)

    val bottomNavItems = listOf(
        Screen.Inbox,
        Screen.Outbox,
        Screen.Servers,
        Screen.Settings
    )

    // Hide bottom navigation bar when inside detail screen
    val showBottomBar = currentRoute?.startsWith("detail") != true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (screen == Screen.Inbox && (unreadCount > 0 || awaitingCount > 0)) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                val badgeText = if (awaitingCount > 0) "!$awaitingCount" else "$unreadCount"
                                                Text(badgeText)
                                            }
                                        } else if (screen == Screen.Outbox && outboxPendingCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            ) {
                                                Text("$outboxPendingCount")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                }
                            },
                            label = { Text(screen.title) },
                            modifier = Modifier.testTag(screen.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Inbox.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Inbox.route) {
                val inboxViewModel: InboxViewModel = viewModel(
                    factory = InboxViewModel.Factory(
                        app.notificationRepository,
                        app.serverRepository,
                        app.outboxRepository
                    )
                )
                InboxScreen(
                    viewModel = inboxViewModel,
                    onNavigateToDetail = { fromServer, id ->
                        navController.navigate("detail/${Uri.encode(fromServer)}/$id")
                    }
                )
            }

            composable(Screen.Outbox.route) {
                val outboxViewModel: OutboxViewModel = viewModel(
                    factory = OutboxViewModel.Factory(app.outboxRepository)
                )
                OutboxScreen(viewModel = outboxViewModel)
            }

            composable(Screen.Servers.route) {
                val serversViewModel: ServersViewModel = viewModel(
                    factory = ServersViewModel.Factory(
                        app.serverRepository,
                        app.unifiedPushManager
                    )
                )
                ServersScreen(viewModel = serversViewModel)
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        app.preferencesRepository,
                        app.notificationRepository,
                        app.serverRepository,
                        app.unifiedPushManager
                    )
                )
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(
                route = "detail/{fromServer}/{id}",
                arguments = listOf(
                    navArgument("fromServer") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val fromServer = backStackEntry.arguments?.getString("fromServer")?.let { Uri.decode(it) } ?: ""
                val id = backStackEntry.arguments?.getLong("id") ?: 0L

                val detailViewModel: NotificationDetailViewModel = viewModel(
                    key = "${fromServer}_$id",
                    factory = NotificationDetailViewModel.Factory(
                        fromServer,
                        id,
                        app.notificationRepository,
                        app.serverRepository,
                        app.outboxRepository,
                        app.preferencesRepository
                    )
                )

                NotificationDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Enable Notifications") },
            text = {
                Text(
                    "Notifyr needs notification permission to deliver critical system alerts, " +
                            "interactive approval action buttons, and inline text replies directly to your notification shade."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.testTag("btn_grant_notifications")
                ) {
                    Text("Enable Notifications")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Later")
                }
            }
        )
    }
}
