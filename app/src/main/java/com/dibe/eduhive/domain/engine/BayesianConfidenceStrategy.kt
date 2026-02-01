package com.dibe.eduhive.domain.engine

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.enums.ConfidenceLevel
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence
import kotlin.math.pow


/**
 * Bayesian-lite confidence update strategy.
 * Uses simplified Bayesian updating formula:
 *
 * posterior = (prior * likelihood) / ((prior * likelihood) + ((1 - prior) * (1 - likelihood)))
 *
 * Key features:
 * - Confidence converges smoothly
 * - Strong evidence moves confidence faster
 * - Weak evidence barely affects mastery
 * - Time decay encourages spaced repetition
 */
class BayesianConfidenceStrategy(
    private val decayRatePerDay: Double = 0.95  // 5% decay per day
) : ConfidenceUpdateStrategy {

    override fun updateFromFlashcard(
        current: Concept,
        evidence: FlashcardEvidence,
        now: Long
    ): Concept {
        // Apply decay first
        val decayed = applyDecay(current, now)

        // Map evidence to likelihood
        val likelihood = mapFlashcardToLikelihood(evidence.confidenceLevel)

        // Bayesian update
        val prior = decayed.confidence
        val posterior = bayesianUpdate(prior, likelihood)

        return decayed.copy(
            confidence = posterior.coerceIn(0.0, 1.0),
            lastReviewedAt = now
        )
    }

    override fun updateFromQuiz(
        current: Concept,
        evidence: QuizEvidence,
        now: Long
    ): Concept {
        // Apply decay first
        val decayed = applyDecay(current, now)

        // Quiz evidence is binary and strong
        val likelihood = if (evidence.wasCorrect) 0.95 else 0.1

        // Bayesian update
        val prior = decayed.confidence
        val posterior = bayesianUpdate(prior, likelihood)

        return decayed.copy(
            confidence = posterior.coerceIn(0.0, 1.0),
            lastReviewedAt = now
        )
    }

    override fun applyDecay(current: Concept, now: Long): Concept {
        val lastReviewed = current.lastReviewedAt ?: return current

        val daysSinceReview = (now - lastReviewed).toDouble() / (1000 * 60 * 60 * 24)

        if (daysSinceReview <= 0) return current

        val decayFactor = decayRatePerDay.pow(daysSinceReview)
        val decayedConfidence = current.confidence * decayFactor

        return current.copy(confidence = decayedConfidence.coerceIn(0.0, 1.0))
    }

    /**
     * Simplified Bayesian update formula.
     */
    private fun bayesianUpdate(prior: Double, likelihood: Double): Double {
        val numerator = prior * likelihood
        val denominator = (prior * likelihood) + ((1 - prior) * (1 - likelihood))
        return numerator / denominator
    }

    /**
     * Map 5-level flashcard response to likelihood of mastery.
     */
    private fun mapFlashcardToLikelihood(level: ConfidenceLevel): Double {
        return when (level) {
            ConfidenceLevel.UNKNOWN -> 0.1
            ConfidenceLevel.KNOWN_LITTLE -> 0.3
            ConfidenceLevel.KNOWN_FAIRLY -> 0.6
            ConfidenceLevel.KNOWN_WELL -> 0.8
            ConfidenceLevel.MASTERED -> 0.95
        }
    }
}