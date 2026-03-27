package com.dibe.eduhive.presentation.conceptList.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.QuizRepository
import com.dibe.eduhive.domain.usecase.concept.GetConceptsByHiveUseCase
import com.dibe.eduhive.domain.usecase.progress.GetWeakConceptsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConceptListViewModel @Inject constructor(
    private val getConceptsByHiveUseCase: GetConceptsByHiveUseCase,
    private val getWeakConceptsUseCase: GetWeakConceptsUseCase,
    private val flashcardRepository: FlashcardRepository,
    private val quizRepository: QuizRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(ConceptListState())
    val state: StateFlow<ConceptListState> = _state.asStateFlow()

    init {
        loadConcepts()
    }

    fun onEvent(event: ConceptListEvent) {
        when (event) {
            is ConceptListEvent.Reload -> loadConcepts()
            is ConceptListEvent.ToggleSelection -> toggleSelection(event.conceptId)
            is ConceptListEvent.SelectWeak -> selectWeakConcepts()
            is ConceptListEvent.ClearSelection -> clearSelection()
            is ConceptListEvent.Generate -> generate(event.mode)
            is ConceptListEvent.ClearGenerated -> clearGenerated()
            is ConceptListEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    fun loadConcepts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getConceptsByHiveUseCase(hiveId).fold(
                onSuccess = { concepts ->
                    _state.update { it.copy(isLoading = false, concepts = concepts, error = null) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    private fun toggleSelection(conceptId: String) {
        _state.update { current ->
            val newSet = current.selectedIds.toMutableSet()
            if (newSet.contains(conceptId)) newSet.remove(conceptId) else newSet.add(conceptId)
            current.copy(selectedIds = newSet)
        }
    }

    private fun selectWeakConcepts() {
        viewModelScope.launch {
            getWeakConceptsUseCase(hiveId, limit = 20, confidenceThreshold = 0.4).fold(
                onSuccess = { weakInfoList ->
                    val weakIds = weakInfoList.map { it.concept.id }.toSet()
                    _state.update { it.copy(selectedIds = weakIds) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                }
            )
        }
    }

    private fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    private fun generate(mode: GenerationMode) {
        val selectedConcepts = state.value.concepts.filter {
            it.id in state.value.selectedIds
        }
        if (selectedConcepts.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, generationProgress = "Starting generation...") }

            try {
                val generatedFlashcards = mutableListOf<Flashcard>()
                val generatedQuizPairs = mutableListOf<Pair<Quiz, List<QuizQuestion>>>()

                if (mode == GenerationMode.FLASHCARDS || mode == GenerationMode.BOTH) {
                    _state.update { it.copy(generationProgress = "Generating flashcards for ${selectedConcepts.size} concept(s)...") }

                    val flashcardJobs = selectedConcepts.map { concept ->
                        async {
                            flashcardRepository.generateFlashcardsForConcept(
                                conceptId = concept.id,
                                conceptName = concept.name,
                                conceptDescription = concept.description,
                                count = 3
                            )
                        }
                    }
                    generatedFlashcards.addAll(flashcardJobs.awaitAll().flatten())
                }

                if (mode == GenerationMode.QUIZ || mode == GenerationMode.BOTH) {
                    _state.update { it.copy(generationProgress = "Generating quiz questions...") }

                    // Generate quizzes sequentially to avoid overwhelming the on-device model
                    selectedConcepts.forEach { concept ->
                        val result = quizRepository.generateQuizForConcept(
                            conceptId = concept.id,
                            conceptName = concept.name,
                            conceptDescription = concept.description,
                            questionCount = 3
                        )
                        generatedQuizPairs.add(result)
                    }
                }

                _state.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = null,
                        generatedFlashcards = generatedFlashcards,
                        generatedQuizPairs = generatedQuizPairs,
                        generationMode = mode,
                        selectedIds = emptySet()
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = null,
                        error = e.message ?: "Generation failed"
                    )
                }
            }
        }
    }

    private fun clearGenerated() {
        _state.update {
            it.copy(
                generatedFlashcards = emptyList(),
                generatedQuizPairs = emptyList(),
                generationMode = null
            )
        }
    }
}

data class ConceptListState(
    val isLoading: Boolean = false,
    val concepts: List<Concept> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isGenerating: Boolean = false,
    val generationProgress: String? = null,
    val generatedFlashcards: List<Flashcard> = emptyList(),
    val generatedQuizPairs: List<Pair<Quiz, List<QuizQuestion>>> = emptyList(),
    val generationMode: GenerationMode? = null,
    val error: String? = null
) {
    val isSelectionActive: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size
}

enum class GenerationMode { FLASHCARDS, QUIZ, BOTH }

sealed class ConceptListEvent {
    object Reload : ConceptListEvent()
    data class ToggleSelection(val conceptId: String) : ConceptListEvent()
    object SelectWeak : ConceptListEvent()
    object ClearSelection : ConceptListEvent()
    data class Generate(val mode: GenerationMode) : ConceptListEvent()
    object ClearGenerated : ConceptListEvent()
    object DismissError : ConceptListEvent()
}