package com.dibe.eduhive.domain.usecase.quiz

import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.repository.QuizRepository
import javax.inject.Inject

/**
 * Use case for retrieving an existing quiz for a concept.
 * Returns the most recent quiz if multiple exist.
 */
class GetQuizForConceptUseCase @Inject constructor(
    private val quizRepository: QuizRepository
) {
    suspend operator fun invoke(conceptId: String): Result<Pair<Quiz, List<QuizQuestion>>?> {
        return try {
            val quizzes = quizRepository.getQuizzesForConcept(conceptId)

            if (quizzes.isEmpty()) {
                return Result.success(null)
            }

            // Get the most recent quiz
            val latestQuiz = quizzes.maxByOrNull { it.createdAt }!!

            val questions = quizRepository.getQuestionsForQuiz(latestQuiz.id)

            Result.success(Pair(latestQuiz, questions))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}