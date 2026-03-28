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
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(ConceptListState())
    val state: StateFlow<ConceptListState> = _state.asStateFlow()

    init {
        loadConcepts()
        reattachPendingWork()
    }

    /**
     * Re-attach observers for any WorkManager jobs that were running when the
     * ViewModel was last destroyed (e.g. user navigated away mid-generation).
     * Work IDs are persisted in SavedStateHandle so they survive ViewModel recreation.
     */
    private fun reattachPendingWork() {
        val conceptIds = savedStateHandle.get<Array<String>>(KEY_PENDING_CONCEPT_IDS)
            ?.toList() ?: return // nothing was pending

        val flashId = savedStateHandle.get<String>(KEY_PENDING_FLASH_WORK_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val quizId = savedStateHandle.get<String>(KEY_PENDING_QUIZ_WORK_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        when {
            flashId != null && quizId != null -> {
                _state.update { it.copy(isGenerating = true, generationProgress = "Resuming generation...") }
                observeBothWork(flashId, quizId, conceptIds)
            }
            flashId != null -> {
                _state.update { it.copy(isGenerating = true, generationProgress = "Resuming flashcard generation...") }
                observeFlashcardWork(flashId, conceptIds, GenerationMode.FLASHCARDS)
            }
            quizId != null -> {
                _state.update { it.copy(isGenerating = true, generationProgress = "Resuming quiz generation...") }
                observeQuizWork(quizId, conceptIds, GenerationMode.QUIZ)
            }
        }
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
                savedStateHandle[KEY_PENDING_FLASH_WORK_ID] = workId.toString()
                savedStateHandle[KEY_PENDING_CONCEPT_IDS] = conceptIds.toTypedArray()
                observeFlashcardWork(workId, conceptIds, mode)
            }
            GenerationMode.QUIZ -> {
                val workId = backgroundGenerationManager.scheduleQuizGeneration(hiveId, conceptIds)
                savedStateHandle[KEY_PENDING_QUIZ_WORK_ID] = workId.toString()
                savedStateHandle[KEY_PENDING_CONCEPT_IDS] = conceptIds.toTypedArray()
                observeQuizWork(workId, conceptIds, mode)
            }
            GenerationMode.BOTH -> {
                val flashWorkId = backgroundGenerationManager.scheduleFlashcardGeneration(hiveId, conceptIds)
                val quizWorkId = backgroundGenerationManager.scheduleQuizGeneration(hiveId, conceptIds)
                savedStateHandle[KEY_PENDING_FLASH_WORK_ID] = flashWorkId.toString()
                savedStateHandle[KEY_PENDING_QUIZ_WORK_ID] = quizWorkId.toString()
                savedStateHandle[KEY_PENDING_CONCEPT_IDS] = conceptIds.toTypedArray()
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
                        clearPendingWorkState()
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
                        clearPendingWorkState()
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
                        clearPendingWorkState()
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
                        clearPendingWorkState()
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
                            clearPendingWorkState()
                            _state.update {
                                it.copy(
                                    isGenerating = false,
                                    generationProgress = null,
                                    error = "Generation failed"
                                )
                            }
                        }
                        flashState == WorkInfo.State.SUCCEEDED && quizState == WorkInfo.State.SUCCEEDED -> {
                            clearPendingWorkState()
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

    private fun clearPendingWorkState() {
        savedStateHandle.remove<String>(KEY_PENDING_FLASH_WORK_ID)
        savedStateHandle.remove<String>(KEY_PENDING_QUIZ_WORK_ID)
        savedStateHandle.remove<Array<String>>(KEY_PENDING_CONCEPT_IDS)
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

    companion object {
        private const val KEY_PENDING_FLASH_WORK_ID = "pendingFlashWorkId"
        private const val KEY_PENDING_QUIZ_WORK_ID = "pendingQuizWorkId"
        private const val KEY_PENDING_CONCEPT_IDS = "pendingConceptIds"
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