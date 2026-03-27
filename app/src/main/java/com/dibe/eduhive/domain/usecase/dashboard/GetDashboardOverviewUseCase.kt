package com.dibe.eduhive.domain.usecase.dashboard

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.MaterialRepository
import com.dibe.eduhive.domain.repository.ReviewEventRepository
import com.dibe.eduhive.domain.repository.QuizRepository
import javax.inject.Inject

/**
 * Use case for getting a complete dashboard overview for a hive.
 */
class GetDashboardOverviewUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository,
    private val materialRepository: MaterialRepository,
    private val reviewEventRepository: ReviewEventRepository,
    private val quizRepository: QuizRepository
) {
    suspend operator fun invoke(hiveId: String): Result<DashboardOverview> {
        return try {
            val concepts = conceptRepository.getConceptsForHive(hiveId)
            val conceptIds = concepts.map { it.id }.toSet()

            val averageConfidence = conceptRepository.getAverageConfidence(hiveId) ?: 0.0
            val weakConcepts = conceptRepository.getWeakestConcepts(hiveId, limit = 5)

            val dueFlashcards = flashcardRepository.getDueFlashcards(
                maxBox = 5,
                limit = 100,
                hiveId = hiveId
            )

            val materials = materialRepository.getMaterialsForHive(hiveId)

            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val recentEvents = reviewEventRepository.getEventsInRange(
                startTime = sevenDaysAgo,
                endTime = System.currentTimeMillis()
            ).filter { event -> event.conceptId in conceptIds }

            // Fetch total quizzes for this hive
            var totalQuizzes = 0
            concepts.forEach { concept ->
                totalQuizzes += quizRepository.getQuizzesForConcept(concept.id).size
            }

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
                    totalQuizzes = totalQuizzes,
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
    val totalQuizzes: Int,
    val masteryDistribution: MasteryDistribution
)

data class MasteryDistribution(
    val beginner: Int,
    val learning: Int,
    val proficient: Int,
    val mastered: Int
)
