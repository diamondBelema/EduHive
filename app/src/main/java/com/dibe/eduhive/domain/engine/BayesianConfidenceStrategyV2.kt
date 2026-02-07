package com.dibe.eduhive.domain.engine

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.enums.ConfidenceLevel
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence
import kotlin.math.pow
import kotlin.math.abs

class BayesianConfidenceStrategyV2(
    private val decayRatePerDay: Double = 0.95,
    private val inertia: Double = 0.8,
    private val maxDailyDelta: Double = 0.08
) : ConfidenceUpdateStrategy {

    private val MIN_CONF = 0.05
    private val MAX_CONF = 0.995

    override fun updateFromFlashcard(
        current: Concept,
        evidence: FlashcardEvidence,
        now: Long
    ): Concept {
        val decayed = applyDecay(current, now)

        val likelihood = mapFlashcardToLikelihood(evidence.confidenceLevel)
        val posterior = bayesianUpdate(decayed.confidence, likelihood)

        val blended = applyInertia(decayed.confidence, posterior)
        val capped = capDelta(decayed.confidence, blended)

        return decayed.copy(
            confidence = capped.coerceIn(MIN_CONF, MAX_CONF),
            lastReviewedAt = now
        )
    }

    override fun updateFromQuiz(
        current: Concept,
        evidence: QuizEvidence,
        now: Long
    ): Concept {
        val decayed = applyDecay(current, now)

        val likelihood = when {
            evidence.wasCorrect -> 0.95
            decayed.confidence > 0.75 -> 0.45 // anomaly tolerance
            else -> 0.3
        }

        val posterior = bayesianUpdate(decayed.confidence, likelihood)
        val blended = applyInertia(decayed.confidence, posterior)
        val capped = capDelta(decayed.confidence, blended)

        return decayed.copy(
            confidence = capped.coerceIn(MIN_CONF, MAX_CONF),
            lastReviewedAt = now
        )
    }

    override fun applyDecay(current: Concept, now: Long): Concept {
        val lastReviewed = current.lastReviewedAt ?: return current

        val days = (now - lastReviewed).toDouble() / (1000 * 60 * 60 * 24)
        if (days <= 0) return current

        val adjustedDecay =
            if (current.confidence > 0.8) decayRatePerDay.pow(days * 0.5)
            else decayRatePerDay.pow(days)

        val decayed = current.confidence * adjustedDecay

        return current.copy(
            confidence = decayed.coerceIn(MIN_CONF, MAX_CONF)
        )
    }

    private fun bayesianUpdate(prior: Double, likelihood: Double): Double {
        val numerator = prior * likelihood
        val denominator = numerator + ((1 - prior) * (1 - likelihood))
        return numerator / denominator
    }

    private fun applyInertia(prior: Double, posterior: Double): Double {
        return (prior * inertia) + (posterior * (1 - inertia))
    }

    private fun capDelta(prior: Double, updated: Double): Double {
        val delta = updated - prior
        val capped = delta.coerceIn(-maxDailyDelta, maxDailyDelta)
        return prior + capped
    }

    private fun mapFlashcardToLikelihood(level: ConfidenceLevel): Double {
        return when (level) {
            ConfidenceLevel.UNKNOWN -> 0.15
            ConfidenceLevel.KNOWN_LITTLE -> 0.35
            ConfidenceLevel.KNOWN_FAIRLY -> 0.6
            ConfidenceLevel.KNOWN_WELL -> 0.8
            ConfidenceLevel.MASTERED -> 0.95
        }
    }
}
