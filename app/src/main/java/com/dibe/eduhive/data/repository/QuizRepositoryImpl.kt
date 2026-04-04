package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.QuizEntity
import com.dibe.eduhive.data.local.entity.QuizQuestionEntity
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.ai.GeneratedQuizQuestion
import com.dibe.eduhive.data.source.ai.QuizGenerationState
import com.dibe.eduhive.data.source.local.FlashcardLocalDataSource
import com.dibe.eduhive.data.source.local.QuizLocalDataSource
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.model.enums.QuizQuestionType
import com.dibe.eduhive.domain.repository.QuizRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject


class QuizRepositoryImpl @Inject constructor(
    private val localDataSource: QuizLocalDataSource,
    private val flashcardLocalDataSource: FlashcardLocalDataSource,
    private val aiDataSource: AIDataSource
) : QuizRepository {

    companion object {
        /** Fallback question count used when a concept has no flashcard facts yet. */
        private const val MIN_QUESTION_COUNT_NO_FACTS = 3

        /** Upper bound on requested questions to stay within the model's token budget. */
        private const val MAX_QUESTION_COUNT = 8
    }

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
     * Generate quiz questions grounded in the concept's existing flashcards.
     */
    fun generateQuizForConceptStreaming(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        questionCount: Int
    ): Flow<QuizGenerationProgress> = flow {
        emit(QuizGenerationProgress.Loading)

        // Fetch existing flashcards for this concept
        val flashcards = flashcardLocalDataSource.getForConcept(conceptId)

        // Build "Q: <front> | A: <back>" fact strings
        val facts = flashcards
            .map { "Q: ${it.front} | A: ${it.back}" }

        // Use the requested count, but fall back to MIN_QUESTION_COUNT_NO_FACTS when no facts are available
        val effectiveCount = if (facts.isEmpty()) minOf(questionCount, MIN_QUESTION_COUNT_NO_FACTS) else questionCount.coerceAtMost(MAX_QUESTION_COUNT)

        // Create quiz entity
        val quizId = UUID.randomUUID().toString()
        val quiz = Quiz(
            id = quizId,
            conceptId = conceptId,
            title = "Quiz: $conceptName",
            createdAt = System.currentTimeMillis()
        )

        emit(QuizGenerationProgress.CreatingQuiz(quiz))

        var generatedQuestions: List<GeneratedQuizQuestion>? = null

        aiDataSource.generateQuizStreaming(
            conceptName = conceptName,
            conceptDescription = conceptDescription ?: "",
            facts = facts,
            questionCount = effectiveCount
        ).collect { state ->
            when (state) {
                is QuizGenerationState.Loading    -> {}
                is QuizGenerationState.Generating -> emit(QuizGenerationProgress.Generating(state.percent))
                is QuizGenerationState.Success    -> generatedQuestions = state.questions
                is QuizGenerationState.Error      -> {
                    emit(QuizGenerationProgress.Error(state.message))
                    return@collect
                }
            }
        }

        // Check if we got questions
        val questions = generatedQuestions?.takeIf { it.isNotEmpty() } ?: run {
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
            if (progress is QuizGenerationProgress.Success) {
                result = Pair(progress.quiz, progress.questions)
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
    data class Generating(val percent: Int) : QuizGenerationProgress()
    data class Saving(val questionCount: Int) : QuizGenerationProgress()
    data class Success(val quiz: Quiz, val questions: List<QuizQuestion>) : QuizGenerationProgress()
    data class Error(val message: String) : QuizGenerationProgress()
}
