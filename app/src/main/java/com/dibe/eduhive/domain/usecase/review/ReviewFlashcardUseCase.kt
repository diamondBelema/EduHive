package com.dibe.eduhive.domain.usecase.review

import com.dibe.eduhive.domain.model.ReviewEvent
import com.dibe.eduhive.domain.model.enums.ConfidenceLevel
import com.dibe.eduhive.domain.model.enums.ReviewTargetType
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.ReviewEventRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for reviewing a flashcard.
 *
 * This is where the learning magic happens:
 * 1. User grades their confidence (5-level scale)
 * 2. Concept confidence is updated via Bayesian learning engine
 * 3. Flashcard's Leitner box is updated
 * 4. Review event is logged for analytics
 */
class ReviewFlashcardUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val conceptRepository: ConceptRepository,
    private val reviewEventRepository: ReviewEventRepository
) {
    suspend operator fun invoke(
        flashcardId: String,
        confidenceLevel: ConfidenceLevel,
        responseTimeMs: Long = 0
    ): Result<Unit> {
        return try {
            // Get flashcard
            val flashcard = flashcardRepository.getFlashcardById(flashcardId)
                ?: return Result.failure(IllegalArgumentException("Flashcard not found"))

            val now = System.currentTimeMillis()

            // 1. Create evidence
            val evidence = FlashcardEvidence(
                confidenceLevel = confidenceLevel,
                responseTimeMs = responseTimeMs,
                wasCorrect = confidenceLevel >= ConfidenceLevel.KNOWN_FAIRLY
            )

            // 2. Update concept confidence (Bayesian learning engine!)
            conceptRepository.updateWithFlashcardEvidence(
                conceptId = flashcard.conceptId,
                evidence = evidence
            )

            // 3. Update Leitner box
            val newBox = calculateNewLeitnerBox(flashcard.currentBox, confidenceLevel)
            val nextReviewAt = calculateNextReview(newBox, now)

            flashcardRepository.updateLeitnerBox(
                flashcardId = flashcardId,
                newBox = newBox,
                lastSeenAt = now,
                nextReviewAt = nextReviewAt
            )

            // 4. Log review event for analytics
            val reviewEvent = ReviewEvent(
                id = UUID.randomUUID().toString(),
                conceptId = flashcard.conceptId,
                targetType = ReviewTargetType.FLASHCARD,
                targetId = flashcardId,
                outcome = confidenceLevelToScore(confidenceLevel),
                responseTimeMs = responseTimeMs,
                timestamp = now
            )
            reviewEventRepository.logReviewEvent(reviewEvent)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate new Leitner box based on user's confidence.
     *
     * Rules:
     * - UNKNOWN, KNOWN_LITTLE → Reset to box 1
     * - KNOWN_FAIRLY → Stay in current box
     * - KNOWN_WELL, MASTERED → Move up one box (max 5)
     */
    private fun calculateNewLeitnerBox(currentBox: Int, level: ConfidenceLevel): Int {
        return when (level) {
            ConfidenceLevel.UNKNOWN,
            ConfidenceLevel.KNOWN_LITTLE -> 1 // Reset to beginning

            ConfidenceLevel.KNOWN_FAIRLY -> currentBox // Stay same

            ConfidenceLevel.KNOWN_WELL,
            ConfidenceLevel.MASTERED -> (currentBox + 1).coerceIn(1, 5) // Move up
        }
    }

    /**
     * Calculate next review time based on Leitner box.
     */
    private fun calculateNextReview(box: Int, now: Long): Long {
        val daysToAdd = when (box) {
            1 -> 1      // Daily
            2 -> 3      // Every 3 days
            3 -> 7      // Weekly
            4 -> 14     // Bi-weekly
            5 -> 30     // Monthly
            else -> 1
        }

        return now + (daysToAdd * 24 * 60 * 60 * 1000)
    }

    /**
     * Convert confidence level to numeric score (0.0 - 1.0).
     */
    private fun confidenceLevelToScore(level: ConfidenceLevel): Float {
        return when (level) {
            ConfidenceLevel.UNKNOWN -> 0.0f
            ConfidenceLevel.KNOWN_LITTLE -> 0.25f
            ConfidenceLevel.KNOWN_FAIRLY -> 0.5f
            ConfidenceLevel.KNOWN_WELL -> 0.75f
            ConfidenceLevel.MASTERED -> 1.0f
        }
    }
}