package com.example.ui.outbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.OutboxEntity
import com.example.data.model.OutboxStatus
import com.example.data.repository.OutboxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OutboxUiState(
    val responses: List<OutboxEntity> = emptyList(),
    val filterStatus: OutboxStatus? = null,
    val pendingCount: Int = 0,
    val isFlushing: Boolean = false,
    val infoMessage: String? = null
)

class OutboxViewModel(
    private val outboxRepository: OutboxRepository
) : ViewModel() {

    private val _filterStatus = MutableStateFlow<OutboxStatus?>(null)
    private val _isFlushing = MutableStateFlow(false)
    private val _infoMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OutboxUiState> = combine(
        outboxRepository.allResponses,
        outboxRepository.pendingCount,
        _filterStatus,
        _isFlushing,
        _infoMessage
    ) { responses, pending, filter, flushing, message ->
        val filtered = if (filter == null) {
            responses
        } else {
            responses.filter { it.status == filter }
        }

        OutboxUiState(
            responses = filtered,
            filterStatus = filter,
            pendingCount = pending,
            isFlushing = flushing,
            infoMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OutboxUiState()
    )

    fun setFilterStatus(status: OutboxStatus?) {
        _filterStatus.value = status
    }

    fun flushOutbox() {
        viewModelScope.launch {
            _isFlushing.value = true
            outboxRepository.flushPendingOutbox()
            _isFlushing.value = false
            _infoMessage.value = "Outbox synchronized with servers"
        }
    }

    fun retryResponse(responseId: String) {
        viewModelScope.launch {
            outboxRepository.retryResponse(responseId)
            _infoMessage.value = "Retrying response delivery..."
        }
    }

    fun deleteResponse(responseId: String) {
        viewModelScope.launch {
            outboxRepository.deleteResponse(responseId)
            _infoMessage.value = "Response removed from outbox"
        }
    }

    fun clearDelivered() {
        viewModelScope.launch {
            outboxRepository.clearDelivered()
            _infoMessage.value = "Delivered entries cleared"
        }
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }

    class Factory(
        private val outboxRepository: OutboxRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OutboxViewModel(outboxRepository) as T
        }
    }
}
