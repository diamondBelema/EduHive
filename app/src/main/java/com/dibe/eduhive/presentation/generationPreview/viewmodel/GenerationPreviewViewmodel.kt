package com.dibe.eduhive.presentation.generationPreview.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.presentation.conceptList.viewmodel.GenerationMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds generated flashcards and/or quiz questions for preview before studying.
 *
 * Content is passed in via [setGeneratedContent] right after the ConceptListViewModel
 * finishes generating — this avoids serializing large lists through SavedStateHandle
 * or nav arguments.
 */
@HiltViewModel
class GenerationPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])
    private val rawMode: String = checkNotNull(savedStateHandle["mode"])
    val mode: GenerationMode = GenerationMode.valueOf(rawMode)

    private val _state = MutableStateFlow(GenerationPreviewState())
    val state: StateFlow<GenerationPreviewState> = _state.asStateFlow()

    private val _navigateToStudy = MutableSharedFlow<PreviewTab>()
    val navigateToStudy = _navigateToStudy.asSharedFlow()

    fun setGeneratedContent(
        flashcards: List<Flashcard>,
        quizPairs: List<Pair<Quiz, List<QuizQuestion>>>
    ) {
        _state.update {
            it.copy(
                flashcards = flashcards,
                quizPairs = quizPairs,
                selectedTab = when (mode) {
                    GenerationMode.QUIZ -> PreviewTab.QUIZ
                    else -> PreviewTab.FLASHCARDS
                }
            )
        }
    }

    fun onEvent(event: GenerationPreviewEvent) {
        when (event) {
            is GenerationPreviewEvent.SelectTab -> {
                _state.update { it.copy(selectedTab = event.tab) }
            }
            is GenerationPreviewEvent.StartStudying -> {
                viewModelScope.launch {
                    _navigateToStudy.emit(state.value.selectedTab)
                }
            }
        }
    }
}

data class GenerationPreviewState(
    val flashcards: List<Flashcard> = emptyList(),
    val quizPairs: List<Pair<Quiz, List<QuizQuestion>>> = emptyList(),
    val selectedTab: PreviewTab = PreviewTab.FLASHCARDS
) {
    val allQuestions: List<QuizQuestion>
        get() = quizPairs.flatMap { it.second }

    val totalFlashcards: Int get() = flashcards.size
    val totalQuestions: Int get() = allQuestions.size
}

enum class PreviewTab { FLASHCARDS, QUIZ }

sealed class GenerationPreviewEvent {
    data class SelectTab(val tab: PreviewTab) : GenerationPreviewEvent()
    object StartStudying : GenerationPreviewEvent()
}