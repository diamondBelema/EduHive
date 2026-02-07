package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.QuizEntity
import com.dibe.eduhive.data.local.entity.QuizQuestionEntity
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.local.QuizLocalDataSource
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.model.enums.QuizQuestionType
import com.dibe.eduhive.domain.repository.QuizRepository
import java.util.UUID
import jakarta.inject.Inject


class QuizRepositoryImpl @Inject constructor(
    private val localDataSource: QuizLocalDataSource,
    private val aiDataSource: AIDataSource
) : QuizRepository {

    override suspend fun createQuiz(quiz: Quiz, questions: List<QuizQuestion>) {
        // Insert quiz first
        localDataSource.insert(QuizEntity.fromDomain(quiz))

        // Then insert all questions
        val questionEntities = questions.map { QuizQuestionEntity.fromDomain(it) }
        localDataSource.insertQuestions(questionEntities)
    }

    override suspend fun getQuizzesForConcept(conceptId: String): List<Quiz> {
        return localDataSource.getForConcept(conceptId).map { it.toDomain() }
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
     * NEW: Generate quiz for a concept using AI.
     */
    override suspend fun generateQuizForConcept(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        questionCount: Int
    ): Pair<Quiz, List<QuizQuestion>> {
        // Use AI to generate questions
        val result = aiDataSource.generateQuiz(
            conceptName = conceptName,
            conceptDescription = conceptDescription ?: "",
            questionCount = questionCount
        )

        // Create quiz
        val quizId = UUID.randomUUID().toString()
        val quiz = Quiz(
            id = quizId,
            conceptId = conceptId,
            title = "Quiz: $conceptName",
            createdAt = System.currentTimeMillis()
        )

        val extractedQuiz = result.getOrElse { error ->
            // You can log this later if you want
            return Pair(quiz, emptyList())
        }

        // Convert generated questions to domain models
        val questions = extractedQuiz.map { gen ->
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

        // Save to database
        createQuiz(quiz, questions)

        return Pair(quiz, questions)
    }
}