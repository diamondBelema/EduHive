package com.dibe.eduhive.domain.usecase.progress

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.repository.ConceptRepository
import javax.inject.Inject

/**
 * Use case for getting concepts that need more study.
 *
 * Identifies concepts with:
 * - Low confidence scores
 * - Not reviewed recently
 * - High priority for improvement
 */
class GetWeakConceptsUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository
) {
    suspend operator fun invoke(
        hiveId: String,
        limit: Int = 10,
        confidenceThreshold: Double = 0.6
    ): Result<List<WeakConceptInfo>> {
        return try {
            // Get weak concepts from repository
            val weakConcepts = conceptRepository.getWeakestConcepts(hiveId, limit)

            // Filter by threshold and add priority info
            val conceptsWithInfo = weakConcepts
                .filter { it.confidence < confidenceThreshold }
                .map { concept ->
                    WeakConceptInfo(
                        concept = concept,
                        priority = calculatePriority(concept),
                        recommendation = getRecommendation(concept)
                    )
                }
                .sortedByDescending { it.priority }

            Result.success(conceptsWithInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate priority score (0.0 - 1.0).
     * Lower confidence = higher priority.
     * Not reviewed recently = higher priority.
     */
    private fun calculatePriority(concept: Concept): Double {
        val confidencePriority = 1.0 - concept.confidence

        // Time priority (not reviewed in 7+ days = higher priority)
        val timePriority = if (concept.lastReviewedAt == null) {
            0.5 // New concepts get medium priority
        } else {
            val daysSinceReview = (System.currentTimeMillis() - concept.lastReviewedAt) / (24 * 60 * 60 * 1000)
            when {
                daysSinceReview > 14 -> 1.0  // Very high priority
                daysSinceReview > 7 -> 0.7   // High priority
                daysSinceReview > 3 -> 0.4   // Medium priority
                else -> 0.1                   // Low priority
            }
        }

        // Weighted average
        return (confidencePriority * 0.7) + (timePriority * 0.3)
    }

    /**
     * Get recommendation for improving this concept.
     */
    private fun getRecommendation(concept: Concept): String {
        return when {
            concept.confidence < 0.2 -> "Review flashcards daily to build foundation"
            concept.confidence < 0.4 -> "Practice flashcards regularly and take quizzes"
            concept.confidence < 0.6 -> "Take quizzes to solidify understanding"
            else -> "Periodic review to maintain mastery"
        }
    }
}

data class WeakConceptInfo(
    val concept: Concept,
    val priority: Double,        // 0.0 (low) to 1.0 (high)
    val recommendation: String
)