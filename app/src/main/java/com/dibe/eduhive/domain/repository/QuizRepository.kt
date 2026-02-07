package com.dibe.eduhive.domain.repository

import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion

/**
 * Repository interface for Quiz operations.
 */
interface QuizRepository {

    suspend fun createQuiz(quiz: Quiz, questions: List<QuizQuestion>)

    suspend fun getQuizzesForConcept(conceptId: String): List<Quiz>

    suspend fun getQuizById(quizId: String): Quiz?

    suspend fun getQuestionsForQuiz(quizId: String): List<QuizQuestion>

    /**
     * Get quiz with all its questions in a single query.
     * More efficient than separate calls.
     */
    suspend fun getQuizWithQuestions(quizId: String): Pair<Quiz, List<QuizQuestion>>?

    suspend fun deleteQuiz(quizId: String)

    suspend fun generateQuizForConcept(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        questionCount: Int = 5
    ): Pair<Quiz, List<QuizQuestion>>
}