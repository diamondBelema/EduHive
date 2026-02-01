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
 * Quiz evidence has higher signal than flashcard evidence,
 * so it updates concept confidence more strongly.
 */
class SubmitQuizResultUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository,
    private val reviewEventRepository: ReviewEventRepository
) {
    suspend operator fun invoke(
        conceptId: String,
        questionId: String,
        wasCorrect: Boolean,
        responseTimeMs: Long = 0
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()

            // 1. Create quiz evidence (high signal!)
            val evidence = QuizEvidence(
                wasCorrect = wasCorrect,
                responseTimeMs = responseTimeMs
            )

            // 2. Update concept confidence (Bayesian learning engine!)
            conceptRepository.updateWithQuizEvidence(
                conceptId = conceptId,
                evidence = evidence
            )

            // 3. Log review event
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
     * Batch submit for multiple quiz questions.
     */
    suspend fun submitMultiple(
        conceptId: String,
        results: List<QuizQuestionResult>
    ): Result<QuizSubmissionSummary> {
        return try {
            var correctCount = 0
            var totalTime = 0L

            results.forEach { result ->
                invoke(
                    conceptId = conceptId,
                    questionId = result.questionId,
                    wasCorrect = result.wasCorrect,
                    responseTimeMs = result.responseTimeMs
                ).getOrThrow()

                if (result.wasCorrect) correctCount++
                totalTime += result.responseTimeMs
            }

            val score = (correctCount.toFloat() / results.size.toFloat()) * 100

            Result.success(
                QuizSubmissionSummary(
                    totalQuestions = results.size,
                    correctAnswers = correctCount,
                    scorePercentage = score,
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