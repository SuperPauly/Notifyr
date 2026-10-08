package com.example.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActionItem
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import com.example.data.repository.NotificationRepository
import com.example.data.repository.OutboxRepository
import com.example.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val servers: List<ServerEntity> = emptyList(),
    val availableApps: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedServerId: String? = null, // null means "All Servers"
    val selectedApp: String? = null, // null means "All Apps"
    val onlyAwaitingResponse: Boolean = false,
    val onlyUnread: Boolean = false,
    val unreadCount: Int = 0,
    val awaitingCount: Int = 0,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

class InboxViewModel(
    private val notificationRepository: NotificationRepository,
    private val serverRepository: ServerRepository,
    private val outboxRepository: OutboxRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedServerId = MutableStateFlow<String?>(null)
    private val _selectedApp = MutableStateFlow<String?>(null)
    private val _onlyAwaitingResponse = MutableStateFlow(false)
    private val _onlyUnread = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<InboxUiState> = combine(
        notificationRepository.allNotifications,
        serverRepository.allServers,
        notificationRepository.unreadCount,
        notificationRepository.awaitingResponseCount,
        _searchQuery,
        _selectedServerId,
        _selectedApp,
        _onlyAwaitingResponse,
        _onlyUnread,
        _statusMessage,
        _isLoading
    ) { params ->
        val rawNotifications = params[0] as List<NotificationEntity>
        val servers = params[1] as List<ServerEntity>
        val unreadCount = params[2] as Int
        val awaitingCount = params[3] as Int
        val query = (params[4] as String).trim()
        val serverId = params[5] as String?
        val appFilter = params[6] as String?
        val onlyAwaiting = params[7] as Boolean
        val onlyUnread = params[8] as Boolean
        val message = params[9] as String?
        val loading = params[10] as Boolean

        val apps = rawNotifications.map { it.fromApp }.distinct().sorted()

        val filtered = rawNotifications.filter { notif ->
            val matchesServer = serverId == null || notif.fromServer == serverId
            val matchesApp = appFilter == null || notif.fromApp == appFilter
            val matchesAwaiting = !onlyAwaiting || notif.isAwaitingResponse
            val matchesUnread = !onlyUnread || !notif.isRead
            val matchesSearch = query.isEmpty() ||
                    notif.title.contains(query, ignoreCase = true) ||
                    notif.body.contains(query, ignoreCase = true) ||
                    notif.fromApp.contains(query, ignoreCase = true) ||
                    notif.fromServer.contains(query, ignoreCase = true)

            matchesServer && matchesApp && matchesAwaiting && matchesUnread && matchesSearch
        }

        InboxUiState(
            notifications = filtered,
            servers = servers,
            availableApps = apps,
            searchQuery = query,
            selectedServerId = serverId,
            selectedApp = appFilter,
            onlyAwaitingResponse = onlyAwaiting,
            onlyUnread = onlyUnread,
            unreadCount = unreadCount,
            awaitingCount = awaitingCount,
            isLoading = loading,
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InboxUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedServer(serverId: String?) {
        _selectedServerId.value = serverId
    }

    fun setSelectedApp(app: String?) {
        _selectedApp.value = app
    }

    fun toggleAwaitingResponse() {
        _onlyAwaitingResponse.value = !_onlyAwaitingResponse.value
    }

    fun toggleOnlyUnread() {
        _onlyUnread.value = !_onlyUnread.value
    }

    fun markAsRead(fromServer: String, id: Long, isRead: Boolean) {
        viewModelScope.launch {
            notificationRepository.markAsRead(fromServer, id, isRead)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead(_selectedServerId.value)
            _statusMessage.value = "All marked as read"
        }
    }

    fun deleteNotification(fromServer: String, id: Long) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(fromServer, id)
        }
    }

    fun submitAction(notification: NotificationEntity, action: ActionItem, replyText: String? = null) {
        viewModelScope.launch {
            outboxRepository.queueResponse(notification, action, replyText)
            _statusMessage.value = "Response queued for '${action.label}'"
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                notificationRepository.refreshNotifications()
                _statusMessage.value = "Notifications updated"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to update: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val notificationRepository: NotificationRepository,
        private val serverRepository: ServerRepository,
        private val outboxRepository: OutboxRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InboxViewModel(notificationRepository, serverRepository, outboxRepository) as T
        }
    }
}
