package com.example.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActionItem
import com.example.data.model.NotificationEntity
import com.example.data.model.ServerEntity
import com.example.data.repository.NotificationRepository
import com.example.data.repository.OutboxRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationDetailUiState(
    val notification: NotificationEntity? = null,
    val server: ServerEntity? = null,
    val isLoading: Boolean = true,
    val infoMessage: String? = null
)

class NotificationDetailViewModel(
    private val serverId: String,
    private val notificationId: Long,
    private val notificationRepository: NotificationRepository,
    private val serverRepository: ServerRepository,
    private val outboxRepository: OutboxRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _infoMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotificationDetailUiState> = combine(
        notificationRepository.getNotification(serverId, notificationId),
        serverRepository.getServer(serverId),
        _infoMessage
    ) { notif, server, msg ->
        NotificationDetailUiState(
            notification = notif,
            server = server,
            isLoading = false,
            infoMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationDetailUiState(isLoading = true)
    )

    init {
        // Auto mark as read if preference is enabled
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.firstOrNull()
            if (prefs?.autoMarkReadOnOpen == true) {
                notificationRepository.markAsRead(serverId, notificationId, true)
            }
        }
    }

    fun toggleReadStatus() {
        val current = uiState.value.notification ?: return
        viewModelScope.launch {
            notificationRepository.markAsRead(serverId, notificationId, !current.isRead)
            _infoMessage.value = if (!current.isRead) "Marked as read" else "Marked as unread"
        }
    }

    fun submitAction(action: ActionItem, replyText: String? = null) {
        val current = uiState.value.notification ?: return
        viewModelScope.launch {
            outboxRepository.queueResponse(current, action, replyText)
            _infoMessage.value = "Response '${action.label}' submitted to Outbox"
        }
    }

    fun deleteNotification(onDeleted: () -> Unit) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(serverId, notificationId)
            onDeleted()
        }
    }

    fun navigateInternalPath(path: String) {
        _infoMessage.value = "Opening internal navigation path: $path"
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }

    class Factory(
        private val serverId: String,
        private val notificationId: Long,
        private val notificationRepository: NotificationRepository,
        private val serverRepository: ServerRepository,
        private val outboxRepository: OutboxRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationDetailViewModel(
                serverId,
                notificationId,
                notificationRepository,
                serverRepository,
                outboxRepository,
                preferencesRepository
            ) as T
        }
    }
}
