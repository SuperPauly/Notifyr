package com.example.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConnectionStatus
import com.example.data.model.ServerEntity
import com.example.data.unifiedpush.DistributorInfo
import com.example.ui.components.DateTimeUtils
import com.example.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    viewModel: ServersViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Server Profiles", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { viewModel.openAddDialog() },
                        modifier = Modifier.testTag("add_server_top_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Server Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                modifier = Modifier.testTag("add_server_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { innerPadding ->
        if (uiState.servers.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Dns,
                title = "No Server Profiles",
                message = "Connect to an independent notification server to begin receiving push messages.",
                actionLabel = "Add Server Profile",
                onActionClick = { viewModel.openAddDialog() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(
                    items = uiState.servers,
                    key = { it.connectionId }
                ) { server ->
                    ServerProfileCard(
                        server = server,
                        distributors = uiState.distributors,
                        isTesting = uiState.testingServerId == server.connectionId,
                        onTest = { viewModel.testConnection(server) },
                        onToggleEnabled = { viewModel.toggleServerEnabled(server) },
                        onEdit = { viewModel.openEditDialog(server) },
                        onDelete = { viewModel.deleteServer(server.connectionId) },
                        onRegisterUp = { pkg -> viewModel.registerUnifiedPush(server, pkg) },
                        onUnregisterUp = { viewModel.unregisterUnifiedPush(server) }
                    )
                }
            }
        }
    }

    if (uiState.isAddDialogOpen) {
        ServerFormDialog(
            initialServer = uiState.editingServer,
            onDismiss = { viewModel.closeDialog() },
            onSave = { connId, serverId, name, url, account, token, color, isEnabled ->
                viewModel.saveServer(connId, serverId, name, url, account, token, color, isEnabled)
            }
        )
    }
}

@Composable
fun ServerProfileCard(
    server: ServerEntity,
    distributors: List<DistributorInfo>,
    isTesting: Boolean,
    onTest: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegisterUp: (String) -> Unit,
    onUnregisterUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUpDialog by remember { mutableStateOf(false) }
    val serverColor = Color(server.colorHex.toInt())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("server_card_${server.serverId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Color + Display Name + Enable Switch + Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(serverColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = server.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Server ID: ${server.serverId} • Connection: ${server.connectionId.take(12)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = server.isEnabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.testTag("toggle_server_${server.serverId}")
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // URL Endpoint
            Text(
                text = server.baseUrl,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Account Identity & Keystore Token status
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = server.accountIdentity ?: "No account identity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (server.encryptedAuthToken != null) "Keystore Protected" else "No Credential",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Connection Status & Last Synced
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Status: ${server.connectionStatus.name}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (server.connectionStatus == ConnectionStatus.CONNECTED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Checked: ${DateTimeUtils.formatRelative(server.lastSyncTime)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // UnifiedPush Status Section
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "UnifiedPush",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = server.upStatus,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (server.upDistributor == null) {
                    TextButton(onClick = { showUpDialog = true }) {
                        Text("Register", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    TextButton(onClick = onUnregisterUp) {
                        Text("Unregister", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Test Connection
            OutlinedButton(
                onClick = onTest,
                enabled = !isTesting && server.isEnabled,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("test_server_btn_${server.serverId}")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Testing Handshake...", style = MaterialTheme.typography.labelSmall)
                } else {
                    Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Connection", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Server Profile") },
            text = {
                Text(
                    "Remove '${server.displayName}' (${server.serverId}) from local storage? " +
                            "Any responses in the Outbox for this server will become orphaned and clearly labelled."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUpDialog) {
        AlertDialog(
            onDismissRequest = { showUpDialog = false },
            title = { Text("Select Push Distributor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "UnifiedPush enables decentralized push notifications without mandatory Google Play Services.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (distributors.isEmpty()) {
                        Text(
                            text = "No UnifiedPush distributor installed on this device (such as ntfy, NextPush, or Conversations).\n\n" +
                                    "You can install an open-source distributor or use Notifyr's built-in push simulator in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        OutlinedButton(
                            onClick = {
                                try {
                                    val browseIntent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://unifiedpush.org/users/distributors/")
                                    )
                                    ctx.startActivity(browseIntent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Browse Available Distributors")
                        }
                    } else {
                        distributors.forEach { dist ->
                            Button(
                                onClick = {
                                    showUpDialog = false
                                    onRegisterUp(dist.packageName)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Register with ${dist.appName} (${dist.packageName})")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showUpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ServerFormDialog(
    initialServer: ServerEntity?,
    onDismiss: () -> Unit,
    onSave: (connId: String?, serverId: String, name: String, url: String, account: String?, token: String?, color: Long, isEnabled: Boolean) -> Unit
) {
    val isEditing = initialServer != null
    var serverId by remember { mutableStateOf(initialServer?.serverId ?: "") }
    var displayName by remember { mutableStateOf(initialServer?.displayName ?: "") }
    var baseUrl by remember { mutableStateOf(initialServer?.baseUrl ?: "") }
    var accountIdentity by remember { mutableStateOf(initialServer?.accountIdentity ?: "") }
    var rawAuthToken by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(initialServer?.colorHex ?: 0xFF4F46E5) }

    var isError by remember { mutableStateOf(false) }

    val colorOptions = listOf(
        0xFF4F46E5, // Indigo
        0xFF059669, // Emerald
        0xFF0284C7, // Sky
        0xFFD97706, // Amber
        0xFFDC2626, // Red
        0xFF7C3AED  // Purple
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Server Profile" else "Add Server Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serverId,
                    onValueChange = { serverId = it },
                    label = { Text("Server ID (e.g. srv-prod-us)") },
                    enabled = !isEditing,
                    isError = isError && serverId.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_id_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("US-East Production") },
                    isError = isError && displayName.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL / Endpoint") },
                    placeholder = { Text("https://gateway.prod.internal") },
                    isError = isError && baseUrl.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountIdentity,
                    onValueChange = { accountIdentity = it },
                    label = { Text("Account Identity (Optional)") },
                    placeholder = { Text("ops-lead@company.internal") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_account_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = rawAuthToken,
                    onValueChange = { rawAuthToken = it },
                    label = { Text(if (isEditing && initialServer?.encryptedAuthToken != null) "Replace Secret Token (Optional)" else "Secret Token (Optional)") },
                    placeholder = { Text("tok_sec_...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_token_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text("Color Palette", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorOptions.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex.toInt()))
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (serverId.isBlank() || displayName.isBlank() || baseUrl.isBlank()) {
                        isError = true
                    } else {
                        onSave(
                            initialServer?.connectionId,
                            serverId,
                            displayName,
                            baseUrl,
                            accountIdentity,
                            rawAuthToken.ifBlank { null },
                            selectedColor,
                            initialServer?.isEnabled ?: true
                        )
                    }
                },
                modifier = Modifier.testTag("save_server_btn")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
