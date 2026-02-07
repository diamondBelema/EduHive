package com.dibe.eduhive.domain.usecase.quiz

import com.dibe.eduhive.data.repository.QuizRepositoryImpl
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.QuizRepository
import javax.inject.Inject

/**
 * Use case for generating a quiz for a concept using AI.
 */
class GenerateQuizUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
    private val conceptRepository: ConceptRepository
) {
    suspend operator fun invoke(
        conceptId: String,
        questionCount: Int = 5
    ): Result<Pair<Quiz, List<QuizQuestion>>> {
        return try {
            // Get concept details
            val concept = conceptRepository.getConceptById(conceptId)
                ?: return Result.failure(IllegalArgumentException("Concept not found"))

            // Generate quiz using AI
            val result = quizRepository.generateQuizForConcept(
                conceptId = conceptId,
                conceptName = concept.name,
                conceptDescription = concept.description,
                questionCount = questionCount
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}