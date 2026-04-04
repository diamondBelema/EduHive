package com.dibe.eduhive.domain.usecase.review

import com.dibe.eduhive.domain.model.ReviewEvent
import com.dibe.eduhive.domain.model.enums.ReviewTargetType
import com.dibe.eduhive.domain.model.evidence.QuizEvidence
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.ReviewEventRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for submitting quiz results.
 *
 * Uses a single aggregated Bayesian update per concept rather than one
 * update per question. Applying N separate updates for N questions
 * compounds confidence incorrectly — a concept with 3/3 correct would
 * receive three multiplied posteriors instead of one calibrated signal.
 *
 * Instead: score = correctCount / totalQuestions → one QuizEvidence → one update.
 */
class SubmitQuizResultUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository,
    private val reviewEventRepository: ReviewEventRepository
) {

    /**
     * Submit a single question result.
     * Prefer [submitMultiple] when you have all results for a concept —
     * it produces a single, properly calibrated Bayesian update.
     */
    suspend operator fun invoke(
        conceptId: String,
        questionId: String,
        wasCorrect: Boolean,
        responseTimeMs: Long = 0
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val evidence = QuizEvidence(wasCorrect = wasCorrect, responseTimeMs = responseTimeMs)
            conceptRepository.updateWithQuizEvidence(conceptId = conceptId, evidence = evidence)
            val reviewEvent = ReviewEvent(
                id = UUID.randomUUID().toString(),
                conceptId = conceptId,
                targetType = ReviewTargetType.QUIZ,
                targetId = questionId,
                outcome = if (wasCorrect) 1.0f else 0.0f,
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
     * Batch submit all questions for a single concept.
     *
     * Aggregates all question results into one score (correct / total),
     * then performs a SINGLE Bayesian update. This correctly reflects
     * the student's overall mastery signal without compounding.
     *
     * Each question is still logged individually as a ReviewEvent for analytics.
     */
    suspend fun submitMultiple(
        conceptId: String,
        results: List<QuizQuestionResult>
    ): Result<QuizSubmissionSummary> {
        if (results.isEmpty()) return Result.failure(IllegalArgumentException("No results to submit"))
        return try {
            val now = System.currentTimeMillis()
            val correctCount = results.count { it.wasCorrect }
            val totalTime = results.sumOf { it.responseTimeMs }
            val scoreRatio = correctCount.toDouble() / results.size.toDouble()

            // Single aggregated Bayesian update for this concept.
            // wasCorrect = true when score >= 60% (passing threshold).
            val aggregatedEvidence = QuizEvidence(
                wasCorrect = scoreRatio >= 0.6,
                responseTimeMs = if (results.isEmpty()) 0L else totalTime / results.size
            )
            conceptRepository.updateWithQuizEvidence(
                conceptId = conceptId,
                evidence = aggregatedEvidence
            )

            // Log each individual question as a ReviewEvent for analytics.
            results.forEach { result ->
                val reviewEvent = ReviewEvent(
                    id = UUID.randomUUID().toString(),
                    conceptId = conceptId,
                    targetType = ReviewTargetType.QUIZ,
                    targetId = result.questionId,
                    outcome = if (result.wasCorrect) 1.0f else 0.0f,
                    responseTimeMs = result.responseTimeMs,
                    timestamp = now
                )
                reviewEventRepository.logReviewEvent(reviewEvent)
            }

            Result.success(
                QuizSubmissionSummary(
                    totalQuestions = results.size,
                    correctAnswers = correctCount,
                    scorePercentage = (scoreRatio * 100).toFloat(),
                    totalTimeMs = totalTime
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class QuizQuestionResult(
    val questionId: String,
    val wasCorrect: Boolean,
    val responseTimeMs: Long
)

data class QuizSubmissionSummary(
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercentage: Float,
    val totalTimeMs: Long
)