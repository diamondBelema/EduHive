package com.dibe.eduhive.presentation.conceptList.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.QuizRepository
import com.dibe.eduhive.domain.usecase.concept.GetConceptsByHiveUseCase
import com.dibe.eduhive.domain.usecase.progress.GetWeakConceptsUseCase
import com.dibe.eduhive.manager.BackgroundGenerationManager
import com.dibe.eduhive.workers.FlashcardGenerationWorker
import com.dibe.eduhive.workers.QuizGenerationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConceptListViewModel @Inject constructor(
    private val getConceptsByHiveUseCase: GetConceptsByHiveUseCase,
    private val getWeakConceptsUseCase: GetWeakConceptsUseCase,
    private val flashcardRepository: FlashcardRepository,
    private val quizRepository: QuizRepository,
    private val backgroundGenerationManager: BackgroundGenerationManager,
    private val workManager: WorkManager,
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

        val conceptIds = selectedConcepts.map { it.id }

        _state.update {
            it.copy(
                isGenerating = true,
                generationProgress = "Scheduling generation...",
                selectedIds = emptySet()
            )
        }

        when (mode) {
            GenerationMode.FLASHCARDS -> {
                val workId = backgroundGenerationManager.scheduleFlashcardGeneration(hiveId, conceptIds)
                observeFlashcardWork(workId, conceptIds, mode)
            }
            GenerationMode.QUIZ -> {
                val workId = backgroundGenerationManager.scheduleQuizGeneration(hiveId, conceptIds)
                observeQuizWork(workId, conceptIds, mode)
            }
            GenerationMode.BOTH -> {
                val flashWorkId = backgroundGenerationManager.scheduleFlashcardGeneration(hiveId, conceptIds)
                val quizWorkId = backgroundGenerationManager.scheduleQuizGeneration(hiveId, conceptIds)
                observeBothWork(flashWorkId, quizWorkId, conceptIds)
            }
        }
    }

    private fun observeFlashcardWork(workId: UUID, conceptIds: List<String>, mode: GenerationMode) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val completed = workInfo.progress.getInt(FlashcardGenerationWorker.KEY_COMPLETED, 0)
                        val total = workInfo.progress.getInt(FlashcardGenerationWorker.KEY_TOTAL, 0)
                        _state.update { it.copy(generationProgress = "Generating flashcards: $completed/$total") }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val flashcards = conceptIds.flatMap { conceptId ->
                            flashcardRepository.getFlashcardsForConcept(conceptId)
                        }
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                generationProgress = null,
                                generatedFlashcards = flashcards,
                                generationMode = mode
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                generationProgress = null,
                                error = "Flashcard generation failed"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeQuizWork(workId: UUID, conceptIds: List<String>, mode: GenerationMode) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val completed = workInfo.progress.getInt(QuizGenerationWorker.KEY_COMPLETED, 0)
                        val total = workInfo.progress.getInt(QuizGenerationWorker.KEY_TOTAL, 0)
                        _state.update { it.copy(generationProgress = "Generating quiz: $completed/$total") }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val quizPairs = fetchLatestQuizPairs(conceptIds)
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                generationProgress = null,
                                generatedQuizPairs = quizPairs,
                                generationMode = mode
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                generationProgress = null,
                                error = "Quiz generation failed"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeBothWork(flashWorkId: UUID, quizWorkId: UUID, conceptIds: List<String>) {
        viewModelScope.launch {
            combine(
                workManager.getWorkInfoByIdFlow(flashWorkId),
                workManager.getWorkInfoByIdFlow(quizWorkId)
            ) { flashInfo, quizInfo -> Pair(flashInfo, quizInfo) }
                .collect { (flashInfo, quizInfo) ->
                    val flashState = flashInfo?.state
                    val quizState = quizInfo?.state

                    when {
                        flashState == WorkInfo.State.FAILED || quizState == WorkInfo.State.FAILED -> {
                            _state.update {
                                it.copy(
                                    isGenerating = false,
                                    generationProgress = null,
                                    error = "Generation failed"
                                )
                            }
                        }
                        flashState == WorkInfo.State.SUCCEEDED && quizState == WorkInfo.State.SUCCEEDED -> {
                            val flashcards = conceptIds.flatMap { conceptId ->
                                flashcardRepository.getFlashcardsForConcept(conceptId)
                            }
                            val quizPairs = fetchLatestQuizPairs(conceptIds)
                            _state.update {
                                it.copy(
                                    isGenerating = false,
                                    generationProgress = null,
                                    generatedFlashcards = flashcards,
                                    generatedQuizPairs = quizPairs,
                                    generationMode = GenerationMode.BOTH
                                )
                            }
                        }
                        else -> {
                            val flashCompleted = flashInfo?.progress?.getInt(FlashcardGenerationWorker.KEY_COMPLETED, 0) ?: 0
                            val flashTotal = flashInfo?.progress?.getInt(FlashcardGenerationWorker.KEY_TOTAL, 0) ?: 0
                            val quizCompleted = quizInfo?.progress?.getInt(QuizGenerationWorker.KEY_COMPLETED, 0) ?: 0
                            val quizTotal = quizInfo?.progress?.getInt(QuizGenerationWorker.KEY_TOTAL, 0) ?: 0
                            _state.update {
                                it.copy(
                                    generationProgress = "Flashcards: $flashCompleted/$flashTotal | Quiz: $quizCompleted/$quizTotal"
                                )
                            }
                        }
                    }
                }
        }
    }

    /** Fetches the most recently generated quiz and its questions for each concept. */
    private suspend fun fetchLatestQuizPairs(conceptIds: List<String>): List<Pair<Quiz, List<QuizQuestion>>> {
        return conceptIds.flatMap { conceptId ->
            val quizzes = quizRepository.getQuizzesForConcept(conceptId)
            val latestQuiz = quizzes.maxByOrNull { it.createdAt }
            latestQuiz?.let { quiz ->
                quizRepository.getQuizWithQuestions(quiz.id)?.let { listOf(it) } ?: emptyList()
            } ?: emptyList()
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