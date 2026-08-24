package com.arsal.ragmobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsal.ragmobile.data.ChatMessage
import com.arsal.ragmobile.data.RagApi
import com.arsal.ragmobile.data.SourceItem
import kotlinx.coroutines.launch

sealed interface ChatUiMessage {
    data class User(val text: String) : ChatUiMessage
    data class Assistant(
        val text: String,
        val grounded: Boolean = false,
        val bestScore: Double = 0.0,
        val sources: List<SourceItem> = emptyList(),
    ) : ChatUiMessage
}

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ChatViewModel : ViewModel() {
    private val api = RagApi()

    var state = androidx.compose.runtime.mutableStateOf(ChatUiState())
        private set

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || state.value.isLoading) return

        val previous = state.value.messages
        state.value = state.value.copy(
            messages = previous + ChatUiMessage.User(trimmed),
            isLoading = true,
            error = null,
        )

        viewModelScope.launch {
            try {
                val history = previous.mapNotNull {
                    when (it) {
                        is ChatUiMessage.User -> ChatMessage("user", it.text)
                        is ChatUiMessage.Assistant -> ChatMessage("assistant", it.text)
                    }
                }
                val result = api.chat(trimmed, history)
                state.value = state.value.copy(
                    messages = state.value.messages + ChatUiMessage.Assistant(
                        text = result.answer,
                        grounded = result.grounded,
                        bestScore = result.bestScore,
                        sources = result.sources,
                    ),
                    isLoading = false,
                )
            } catch (e: Exception) {
                state.value = state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Something went wrong.",
                )
            }
        }
    }

    fun clearError() {
        state.value = state.value.copy(error = null)
    }
}
