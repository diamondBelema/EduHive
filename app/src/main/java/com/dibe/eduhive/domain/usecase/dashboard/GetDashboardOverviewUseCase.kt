package com.dibe.eduhive.domain.usecase.dashboard

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.MaterialRepository
import com.dibe.eduhive.domain.repository.ReviewEventRepository
import javax.inject.Inject

/**
 * Use case for getting a complete dashboard overview for a hive.
 *
 * Provides:
 * - Overall progress metrics
 * - Weak concepts that need attention
 * - Study statistics
 * - Recent activity
 */
class GetDashboardOverviewUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository,
    private val materialRepository: MaterialRepository,
    private val reviewEventRepository: ReviewEventRepository
) {
    suspend operator fun invoke(hiveId: String): Result<DashboardOverview> {
        return try {
            // Get all concepts for the hive
            val concepts = conceptRepository.getConceptsForHive(hiveId)

            // Calculate average confidence
            val averageConfidence = conceptRepository.getAverageConfidence(hiveId) ?: 0.0

            // Get weak concepts (bottom 5)
            val weakConcepts = conceptRepository.getWeakestConcepts(hiveId, limit = 5)

            // Get due flashcards count
            val dueFlashcards = flashcardRepository.getDueFlashcards(maxBox = 5, limit = 100)

            // Get materials count
            val materials = materialRepository.getMaterialsForHive(hiveId)

            // Get recent activity (last 7 days)
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val recentEvents = reviewEventRepository.getEventsInRange(
                startTime = sevenDaysAgo,
                endTime = System.currentTimeMillis()
            )

            // Calculate mastery distribution
            val masteryDistribution = MasteryDistribution(
                beginner = concepts.count { it.confidence < 0.3 },
                learning = concepts.count { it.confidence in 0.3..0.6 },
                proficient = concepts.count { it.confidence in 0.6..0.8 },
                mastered = concepts.count { it.confidence > 0.8 }
            )

            Result.success(
                DashboardOverview(
                    hiveId = hiveId,
                    totalConcepts = concepts.size,
                    averageConfidence = averageConfidence,
                    weakConcepts = weakConcepts,
                    dueFlashcardsCount = dueFlashcards.size,
                    totalMaterials = materials.size,
                    recentReviewsCount = recentEvents.size,
                    masteryDistribution = masteryDistribution
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class DashboardOverview(
    val hiveId: String,
    val totalConcepts: Int,
    val averageConfidence: Double,
    val weakConcepts: List<Concept>,
    val dueFlashcardsCount: Int,
    val totalMaterials: Int,
    val recentReviewsCount: Int,
    val masteryDistribution: MasteryDistribution
)

data class MasteryDistribution(
    val beginner: Int,      // < 30% confidence
    val learning: Int,      // 30-60% confidence
    val proficient: Int,    // 60-80% confidence
    val mastered: Int       // > 80% confidence
)