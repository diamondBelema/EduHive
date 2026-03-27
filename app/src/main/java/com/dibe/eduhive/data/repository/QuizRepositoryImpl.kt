package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.QuizEntity
import com.dibe.eduhive.data.local.entity.QuizQuestionEntity
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.ai.GeneratedQuizQuestion
import com.dibe.eduhive.data.source.ai.QuizGenerationState
import com.dibe.eduhive.data.source.local.QuizLocalDataSource
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.model.enums.QuizQuestionType
import com.dibe.eduhive.domain.repository.QuizRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import jakarta.inject.Inject


class QuizRepositoryImpl @Inject constructor(
    private val localDataSource: QuizLocalDataSource,
    private val aiDataSource: AIDataSource
) : QuizRepository {

    override suspend fun createQuiz(quiz: Quiz, questions: List<QuizQuestion>) {
        localDataSource.insert(QuizEntity.fromDomain(quiz))
        val questionEntities = questions.map { QuizQuestionEntity.fromDomain(it) }
        localDataSource.insertQuestions(questionEntities)
    }

    override suspend fun getQuizzesForConcept(conceptId: String): List<Quiz> {
        return localDataSource.getForConcept(conceptId).map { it.toDomain() }
    }

    override suspend fun getQuizzesForHive(hiveId: String): List<Quiz> {
        return localDataSource.getQuizzesWithQuestionsForHive(hiveId).map { it.quiz.toDomain() }
    }

    override suspend fun getQuizById(quizId: String): Quiz? {
        return localDataSource.getQuizWithQuestions(quizId)?.quiz?.toDomain()
    }

    override suspend fun getQuestionsForQuiz(quizId: String): List<QuizQuestion> {
        return localDataSource.getQuestionsForQuiz(quizId).map { it.toDomain() }
    }

    override suspend fun getQuizWithQuestions(quizId: String): Pair<Quiz, List<QuizQuestion>>? {
        val result = localDataSource.getQuizWithQuestions(quizId) ?: return null
        val quiz = result.quiz.toDomain()
        val questions = result.questions.map { it.toDomain() }
        return Pair(quiz, questions)
    }

    override suspend fun deleteQuiz(quizId: String) {
        localDataSource.delete(quizId)
    }

    /**
     * 🚀 STREAMING: Generate quiz with progress tracking.
     * Note: If AIDataSource doesn't have streaming for quiz yet,
     * this wraps the standard call in a flow for API consistency.
     */
    /**
     * 🚀 STREAMING: Generate quiz with progress tracking.
     * Collects from AIDataSource streaming and maps to repository progress states.
     */
    fun generateQuizForConceptStreaming(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        questionCount: Int
    ): Flow<QuizGenerationProgress> = flow {
        emit(QuizGenerationProgress.Loading)

        // Create quiz entity first (immediate feedback)
        val quizId = UUID.randomUUID().toString()
        val quiz = Quiz(
            id = quizId,
            conceptId = conceptId,
            title = "Quiz: $conceptName",
            createdAt = System.currentTimeMillis()
        )

        emit(QuizGenerationProgress.CreatingQuiz(quiz))

        // Collect from streaming data source
        var generatedQuestions: List<GeneratedQuizQuestion>? = null

        aiDataSource.generateQuizStreaming(
            conceptName = conceptName,
            conceptDescription = conceptDescription ?: "",
            questionCount = questionCount
        ).collect { state ->
            when (state) {
                is QuizGenerationState.Loading -> {
                    // Already emitted Loading above, can emit CreatingQuiz again if needed
                    emit(QuizGenerationProgress.CreatingQuiz(quiz))
                }
                is QuizGenerationState.Generating -> {
                    // Forward progress percentage
                    emit(QuizGenerationProgress.Generating(state.percent))
                }
                is QuizGenerationState.Success -> {
                    generatedQuestions = state.questions
                }
                is QuizGenerationState.Error -> {
                    emit(QuizGenerationProgress.Error(state.message))
                    return@collect // Stop collecting on error
                }
            }
        }

        // Check if we got questions
        val questions = generatedQuestions ?: run {
            emit(QuizGenerationProgress.Error("No questions generated"))
            return@flow
        }

        // Convert to domain models
        val domainQuestions = questions.map { gen ->
            QuizQuestion(
                id = UUID.randomUUID().toString(),
                quizId = quizId,
                question = gen.text,
                type = when (gen.type.uppercase()) {
                    "MCQ" -> QuizQuestionType.MCQ
                    "TRUE_FALSE" -> QuizQuestionType.TRUE_FALSE
                    else -> QuizQuestionType.SHORT_ANSWER
                },
                correctAnswer = gen.correctAnswer,
                options = gen.options
            )
        }

        emit(QuizGenerationProgress.Saving(domainQuestions.size))

        // Save to database
        createQuiz(quiz, domainQuestions)

        emit(QuizGenerationProgress.Success(quiz, domainQuestions))
    }

    /**
     * Standard suspend function for backward compatibility.
     */
    override suspend fun generateQuizForConcept(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        questionCount: Int
    ): Pair<Quiz, List<QuizQuestion>> {
        var result: Pair<Quiz, List<QuizQuestion>>? = null

        generateQuizForConceptStreaming(
            conceptId, conceptName, conceptDescription, questionCount
        ).collect { progress ->
            when (progress) {
                is QuizGenerationProgress.Success -> {
                    result = Pair(progress.quiz, progress.questions)
                }
                else -> { /* Ignore intermediate states */ }
            }
        }

        return result ?: throw IllegalStateException("Quiz generation failed")
    }
}

/**
 * Progress states for quiz generation UI.
 */
sealed class QuizGenerationProgress {
    object Loading : QuizGenerationProgress()
    data class CreatingQuiz(val quiz: Quiz) : QuizGenerationProgress()
    data class Generating(val percent: Int) : QuizGenerationProgress() // NEW: Progress from AI
    data class Saving(val questionCount: Int) : QuizGenerationProgress()
    data class Success(val quiz: Quiz, val questions: List<QuizQuestion>) : QuizGenerationProgress()
    data class Error(val message: String) : QuizGenerationProgress()
}