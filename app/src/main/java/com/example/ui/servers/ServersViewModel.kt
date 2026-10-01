package com.example.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ServerEntity
import com.example.data.repository.ServerRepository
import com.example.data.unifiedpush.DistributorInfo
import com.example.data.unifiedpush.UnifiedPushManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ServersUiState(
    val servers: List<ServerEntity> = emptyList(),
    val distributors: List<DistributorInfo> = emptyList(),
    val testingServerId: String? = null,
    val feedbackMessage: String? = null,
    val isAddDialogOpen: Boolean = false,
    val editingServer: ServerEntity? = null
)

class ServersViewModel(
    private val serverRepository: ServerRepository,
    private val unifiedPushManager: UnifiedPushManager
) : ViewModel() {

    private val _testingServerId = MutableStateFlow<String?>(null)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _editingServer = MutableStateFlow<ServerEntity?>(null)

    val uiState: StateFlow<ServersUiState> = combine(
        serverRepository.allServers,
        _testingServerId,
        _feedbackMessage,
        _isAddDialogOpen,
        _editingServer
    ) { servers, testingId, feedback, isOpen, editing ->
        val distList = unifiedPushManager.discoverDistributors()
        ServersUiState(
            servers = servers,
            distributors = distList,
            testingServerId = testingId,
            feedbackMessage = feedback,
            isAddDialogOpen = isOpen,
            editingServer = editing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ServersUiState()
    )

    fun openAddDialog() {
        _editingServer.value = null
        _isAddDialogOpen.value = true
    }

    fun openEditDialog(server: ServerEntity) {
        _editingServer.value = server
        _isAddDialogOpen.value = true
    }

    fun closeDialog() {
        _isAddDialogOpen.value = false
        _editingServer.value = null
    }

    fun saveServer(
        connectionId: String?,
        serverId: String,
        displayName: String,
        baseUrl: String,
        accountIdentity: String?,
        rawAuthToken: String?,
        colorHex: Long,
        isEnabled: Boolean
    ) {
        viewModelScope.launch {
            val finalConnId = connectionId ?: "conn-${UUID.randomUUID().toString().take(8)}"
            serverRepository.addServer(
                connectionId = finalConnId,
                serverId = serverId.trim().lowercase(),
                displayName = displayName.trim(),
                baseUrl = baseUrl.trim(),
                accountIdentity = accountIdentity?.trim()?.ifBlank { null },
                rawAuthToken = rawAuthToken?.trim()?.ifBlank { null },
                colorHex = colorHex,
                isEnabled = isEnabled
            )
            _isAddDialogOpen.value = false
            _editingServer.value = null
            _feedbackMessage.value = "Profile '$displayName' saved with Keystore-backed protection"
        }
    }

    fun toggleServerEnabled(server: ServerEntity) {
        viewModelScope.launch {
            val updated = server.copy(isEnabled = !server.isEnabled)
            serverRepository.updateServer(updated)
            _feedbackMessage.value = if (updated.isEnabled) "Server enabled" else "Server disabled"
        }
    }

    fun deleteServer(connectionId: String) {
        viewModelScope.launch {
            serverRepository.deleteServer(connectionId)
            _feedbackMessage.value = "Server profile removed"
        }
    }

    fun testConnection(server: ServerEntity) {
        viewModelScope.launch {
            _testingServerId.value = server.connectionId
            val result = serverRepository.testConnection(server)
            _testingServerId.value = null
            _feedbackMessage.value = if (result.isSuccess) {
                "${server.displayName}: ${result.getOrNull()}"
            } else {
                "${server.displayName}: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun registerUnifiedPush(server: ServerEntity, distributorPackage: String) {
        unifiedPushManager.register(server, distributorPackage)
        viewModelScope.launch {
            serverRepository.updateServer(
                server.copy(
                    upDistributor = distributorPackage,
                    upStatus = "Registering with $distributorPackage..."
                )
            )
            _feedbackMessage.value = "UnifiedPush registration broadcast sent to $distributorPackage"
        }
    }

    fun unregisterUnifiedPush(server: ServerEntity) {
        unifiedPushManager.unregister(server)
        viewModelScope.launch {
            serverRepository.updateServer(
                server.copy(
                    upDistributor = null,
                    upEndpoint = null,
                    upStatus = "Not Registered"
                )
            )
            _feedbackMessage.value = "UnifiedPush unregistered"
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    class Factory(
        private val serverRepository: ServerRepository,
        private val unifiedPushManager: UnifiedPushManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ServersViewModel(serverRepository, unifiedPushManager) as T
        }
    }
}
