package com.dibe.eduhive.presentation.documentChat.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.data.session.ActiveHiveStore
import com.dibe.eduhive.domain.usecase.chat.AskHiveQuestionUseCase
import com.dibe.eduhive.domain.usecase.chat.DocumentChatAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentChatViewModel @Inject constructor(
    private val askHiveQuestionUseCase: AskHiveQuestionUseCase,
    private val activeHiveStore: ActiveHiveStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = savedStateHandle["hiveId"]
        ?: activeHiveStore.activeHiveId.value
        ?: "ALL"

    private val _state = MutableStateFlow(DocumentChatState())
    val state: StateFlow<DocumentChatState> = _state.asStateFlow()

    fun onEvent(event: DocumentChatEvent) {
        when (event) {
            is DocumentChatEvent.UpdateInput -> {
                _state.update { it.copy(input = event.value) }
            }
            is DocumentChatEvent.SubmitQuestion -> submitQuestion()
            is DocumentChatEvent.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun submitQuestion() {
        val question = _state.value.input.trim()
        if (question.isBlank() || _state.value.isLoading) return

        _state.update {
            it.copy(
                input = "",
                isLoading = true,
                error = null,
                messages = it.messages + ChatMessage.User(question)
            )
        }

        viewModelScope.launch {
            val result = askHiveQuestionUseCase(hiveId, question)
            result.fold(
                onSuccess = { answer ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            messages = it.messages + ChatMessage.Assistant(answer)
                        )
                    }
                },
                onFailure = { error ->
                    val userFacingMessage = when {
                        error.message?.contains("re-imported", ignoreCase = true) == true ->
                            error.message!! // actionable stale-URI message, show as-is
                        error.message?.contains("No processed documents", ignoreCase = true) == true ->
                            "No processed documents found in this hive yet. Add a document first."
                        else ->
                            "I couldn't fully answer from your materials yet. Try rephrasing the question or import clearer docs."
                    }
                    val fallbackAnswer = DocumentChatAnswer(
                        question = question,
                        answer = userFacingMessage,
                        citations = emptyList(),
                        isGrounded = false,
                        warning = error.message ?: "Unable to answer right now"
                    )
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message,
                            messages = it.messages + ChatMessage.Assistant(fallbackAnswer)
                        )
                    }
                }
            )
        }
    }
}

data class DocumentChatState(
    val input: String = "",
    val isLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null
)

sealed class DocumentChatEvent {
    data class UpdateInput(val value: String) : DocumentChatEvent()
    object SubmitQuestion : DocumentChatEvent()
    object ClearError : DocumentChatEvent()
}

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Assistant(val answer: DocumentChatAnswer) : ChatMessage()
}