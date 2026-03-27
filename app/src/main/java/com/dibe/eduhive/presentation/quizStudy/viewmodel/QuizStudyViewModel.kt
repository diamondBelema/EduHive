package com.dibe.eduhive.presentation.quizStudy.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.repository.QuizRepository
import com.dibe.eduhive.domain.usecase.concept.GetConceptsByHiveUseCase
import com.dibe.eduhive.domain.usecase.review.QuizQuestionResult
import com.dibe.eduhive.domain.usecase.review.SubmitQuizResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizStudyViewModel @Inject constructor(
    private val getConceptsByHiveUseCase: GetConceptsByHiveUseCase,
    private val quizRepository: QuizRepository,
    private val submitQuizResultUseCase: SubmitQuizResultUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(QuizStudyState())
    val state: StateFlow<QuizStudyState> = _state.asStateFlow()

    private val startTimeMap = mutableMapOf<String, Long>()

    init {
        loadQuizzesForHive()
    }

    fun setInitialQuizPairs(pairs: List<Pair<Quiz, List<QuizQuestion>>>) {
        if (pairs.isEmpty()) return
        _state.update {
            it.copy(
                isLoading = false,
                quizPairs = pairs,
                error = null
            )
        }
    }

    fun startQuestionTimer(questionId: String) {
        startTimeMap[questionId] = System.currentTimeMillis()
    }

    fun submitResults(results: Map<String, String>) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }

            // Build a lookup from quizId -> conceptId using the loaded quiz pairs.
            // question.quizId is the quiz's ID, not the concept's ID.
            // We need the concept's ID to update confidence in the learning engine.
            val quizIdToConceptId = state.value.quizPairs
                .associate { (quiz, _) -> quiz.id to quiz.conceptId }

            val questions = state.value.quizPairs.flatMap { it.second }

            questions.forEach { question ->
                val selected = results[question.id] ?: return@forEach
                val isCorrect = isAnswerCorrect(selected, question.correctAnswer)
                val duration = System.currentTimeMillis() - (startTimeMap[question.id] ?: System.currentTimeMillis())
                val conceptId = quizIdToConceptId[question.quizId] ?: return@forEach

                submitQuizResultUseCase(
                    conceptId = conceptId,
                    questionId = question.id,
                    wasCorrect = isCorrect,
                    responseTimeMs = duration
                )
            }

            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun isAnswerCorrect(selected: String, correctAnswerRaw: String): Boolean {
        val correct = correctAnswerRaw.trim().uppercase()
        return selected.uppercase() == correct ||
                (selected.uppercase() == "A" && correct == "TRUE") ||
                (selected.uppercase() == "B" && correct == "FALSE")
    }

    fun loadQuizzesForHive() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            getConceptsByHiveUseCase(hiveId).fold(
                onSuccess = { concepts ->
                    val quizPairs = mutableListOf<Pair<Quiz, List<QuizQuestion>>>()

                    concepts.forEach { concept ->
                        val quizzes = quizRepository.getQuizzesForConcept(concept.id)
                        val latest = quizzes.maxByOrNull { it.createdAt } ?: return@forEach
                        val pair = quizRepository.getQuizWithQuestions(latest.id)
                        if (pair != null && pair.second.isNotEmpty()) {
                            quizPairs.add(pair)
                        }
                    }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            quizPairs = quizPairs.sortedByDescending { pair -> pair.first.createdAt },
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load quizzes"
                        )
                    }
                }
            )
        }
    }
}

data class QuizStudyState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val quizPairs: List<Pair<Quiz, List<QuizQuestion>>> = emptyList(),
    val error: String? = null
)