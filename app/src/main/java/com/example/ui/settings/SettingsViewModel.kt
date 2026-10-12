package com.example.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InboxRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.ServerRepository
import com.example.data.repository.ThemeMode
import com.example.data.repository.UserPreferences
import com.example.data.unifiedpush.DistributorInfo
import com.example.data.unifiedpush.UnifiedPushManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val distributors: List<DistributorInfo> = emptyList(),
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val notificationRepository: NotificationRepository,
    private val serverRepository: ServerRepository,
    private val unifiedPushManager: UnifiedPushManager,
    private val inboxRepository: InboxRepository
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.userPreferences,
        _statusMessage
    ) { prefs, msg ->
        val distList = unifiedPushManager.discoverDistributors()
        SettingsUiState(
            preferences = prefs,
            distributors = distList,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(themeMode)
            _statusMessage.value = "Theme set to ${themeMode.name.lowercase().replaceFirstChar { it.uppercase() }}"
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSoundEnabled(enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setVibrationEnabled(enabled)
        }
    }

    fun setAutoMarkRead(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoMarkRead(enabled)
        }
    }

    fun setAutoCleanExpired(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoCleanExpired(enabled)
            if (enabled) {
                notificationRepository.cleanExpired()
            }
        }
    }

    fun simulateUnifiedPushDelivery(context: Context) {
        viewModelScope.launch {
            val firstServer = serverRepository.enabledServers.firstOrNull()?.firstOrNull()
            if (firstServer == null) {
                _statusMessage.value = "No enabled server profile available for UnifiedPush test"
                return@launch
            }

            val now = java.time.Instant.now().toString()
            val sampleJson = """
                {
                    "version": 1,
                    "kind": "notification",
                    "revision": "1",
                    "from_server": "${firstServer.serverId}",
                    "state": {
                        "responded": false,
                        "notification": {
                            "id": "${System.currentTimeMillis()}",
                            "created_at": "$now",
                            "from_app": "k8s-autoscale",
                            "from_server": "${firstServer.serverId}",
                            "title": "UnifiedPush: HPA Scaled Deployment",
                            "body": "Ingest stream delivered through distributor protocol to connection '${firstServer.connectionId.take(8)}'.",
                            "actions": [
                                {"id": "ack_scale", "label": "Acknowledge", "kind": "BUTTON"},
                                {"id": "reply_scale", "label": "Scale Note", "kind": "TEXT_REPLY"}
                            ]
                        }
                    }
                }
            """.trimIndent()

            val decoded = com.example.data.unifiedpush.UnifiedPushPayloadDecoder.decodePayload(
                rawBytes = sampleJson.toByteArray(Charsets.UTF_8),
                rawString = null,
                server = firstServer
            )

            when (decoded) {
                is com.example.data.unifiedpush.DecodeResult.Notification -> {
                    inboxRepository.applyInlineNotification(decoded)
                    _statusMessage.value = "Simulated UnifiedPush alert posted to notification shade"
                }

                is com.example.data.unifiedpush.DecodeResult.RegistrationChallenge -> {
                    unifiedPushManager.handleRegistrationChallenge(firstServer, decoded)
                    _statusMessage.value = "Simulated registration challenge handled"
                }

                else -> {
                    _statusMessage.value = "Failed to decode push payload"
                }
            }
        }
    }

    fun cleanExpiredNow() {
        viewModelScope.launch {
            notificationRepository.cleanExpired()
            _statusMessage.value = "Expired notifications cleaned"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val preferencesRepository: PreferencesRepository,
        private val notificationRepository: NotificationRepository,
        private val serverRepository: ServerRepository,
        private val unifiedPushManager: UnifiedPushManager,
        private val inboxRepository: InboxRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository,
                notificationRepository,
                serverRepository,
                unifiedPushManager,
                inboxRepository
            ) as T
        }
    }
}
