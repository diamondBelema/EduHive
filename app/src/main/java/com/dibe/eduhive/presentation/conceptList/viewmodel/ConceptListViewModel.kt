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
     *
     * Strategy:
     * 1. Try to restore from SavedStateHandle (covers process recreation / config change).
     * 2. Fall back to querying WorkManager by tag for this hiveId (covers the case where
     *    the user navigated *back* past the ConceptList — destroying the back-stack entry
     *    and its SavedStateHandle — then returned to the screen).
     */
    private fun reattachPendingWork() {
        val conceptIds = savedStateHandle.get<Array<String>>(KEY_PENDING_CONCEPT_IDS)
            ?.toList()

        val flashId = savedStateHandle.get<String>(KEY_PENDING_FLASH_WORK_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val quizId = savedStateHandle.get<String>(KEY_PENDING_QUIZ_WORK_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        if (conceptIds != null && (flashId != null || quizId != null)) {
            // Restore from SavedStateHandle
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
        } else {
            // Fall back to WorkManager tag query — finds work that belongs to this hive
            // even when the nav back-stack entry was destroyed.
            reattachFromWorkManagerTags()
        }
    }

    /**
     * Queries WorkManager for any RUNNING/ENQUEUED flashcard or quiz generation work
     * tagged to this hiveId. Re-attaches observers so the UI can resume showing progress.
     */
    private fun reattachFromWorkManagerTags() {
        viewModelScope.launch {
            val allWork = workManager.getWorkInfosByTag("flashcard_generation").get() +
                workManager.getWorkInfosByTag("quiz_generation").get()

            val activeFlash = allWork
                .filter { it.tags.contains("flashcard_generation") }
                .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                .filter { it.progress.getString(FlashcardGenerationWorker.KEY_HIVE_ID) == hiveId }
                .maxByOrNull { it.id.toString() }

            val activeQuiz = allWork
                .filter { it.tags.contains("quiz_generation") }
                .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                .filter { it.progress.getString(QuizGenerationWorker.KEY_HIVE_ID) == hiveId }
                .maxByOrNull { it.id.toString() }

            if (activeFlash != null || activeQuiz != null) {
                // We found running work — recover concept IDs from worker input
                val conceptIds = (activeFlash ?: activeQuiz)
                    ?.let {
                        // The worker stores them in input data; also try outputData/progress
                        workManager.getWorkInfoById(it.id).get()?.outputData
                            ?.getStringArray(FlashcardGenerationWorker.KEY_CONCEPT_IDS)?.toList()
                    } ?: emptyList()

                _state.update { it.copy(isGenerating = true, generationProgress = "Resuming generation...") }

                when {
                    activeFlash != null && activeQuiz != null ->
                        observeBothWork(activeFlash.id, activeQuiz.id, conceptIds)
                    activeFlash != null ->
                        observeFlashcardWork(activeFlash.id, conceptIds, GenerationMode.FLASHCARDS)
                    activeQuiz != null ->
                        observeQuizWork(activeQuiz.id, conceptIds, GenerationMode.QUIZ)
                }
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
        val total = conceptIds.size

        _state.update {
            it.copy(
                isGenerating = true,
                generationProgress = "Scheduling generation...",
                generationProgressFloat = 0f,
                generationTotal = total,
                generationCompleted = 0,
                currentConceptName = null,
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
                        val conceptName = workInfo.progress.getString(FlashcardGenerationWorker.KEY_CURRENT_CONCEPT_NAME)
                        val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
                        _state.update {
                            it.copy(
                                generationProgress = "Generating flashcards: $completed/$total",
                                generationProgressFloat = progress,
                                generationCompleted = completed,
                                generationTotal = total,
                                currentConceptName = conceptName?.takeIf { s -> s.isNotEmpty() }
                            )
                        }
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
                                generationProgressFloat = 1f,
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
                                generationProgressFloat = 0f,
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
                        val conceptName = workInfo.progress.getString(QuizGenerationWorker.KEY_CURRENT_CONCEPT_NAME)
                        val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
                        _state.update {
                            it.copy(
                                generationProgress = "Generating quiz: $completed/$total",
                                generationProgressFloat = progress,
                                generationCompleted = completed,
                                generationTotal = total,
                                currentConceptName = conceptName?.takeIf { s -> s.isNotEmpty() }
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        clearPendingWorkState()
                        val quizPairs = fetchLatestQuizPairs(conceptIds)
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                generationProgress = null,
                                generationProgressFloat = 1f,
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
                                generationProgressFloat = 0f,
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
                                    generationProgressFloat = 0f,
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
                                    generationProgressFloat = 1f,
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
                            val flashConceptName = flashInfo?.progress?.getString(FlashcardGenerationWorker.KEY_CURRENT_CONCEPT_NAME)
                            val quizConceptName = quizInfo?.progress?.getString(QuizGenerationWorker.KEY_CURRENT_CONCEPT_NAME)
                            val combinedTotal = flashTotal + quizTotal
                            val combinedCompleted = flashCompleted + quizCompleted
                            val progress = if (combinedTotal > 0) combinedCompleted.toFloat() / combinedTotal.toFloat() else 0f
                            _state.update {
                                it.copy(
                                    generationProgress = "Flashcards: $flashCompleted/$flashTotal | Quiz: $quizCompleted/$quizTotal",
                                    generationProgressFloat = progress,
                                    generationCompleted = combinedCompleted,
                                    generationTotal = combinedTotal,
                                    currentConceptName = flashConceptName?.takeIf { s -> s.isNotEmpty() }
                                        ?: quizConceptName?.takeIf { s -> s.isNotEmpty() }
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

