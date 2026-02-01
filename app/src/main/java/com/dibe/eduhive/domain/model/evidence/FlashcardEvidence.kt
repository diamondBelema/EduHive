package com.dibe.eduhive.domain.model.evidence

import com.dibe.eduhive.domain.model.enums.ConfidenceLevel

/**
 * Evidence generated from a flashcard review.
 * Provides low-signal, frequent input for Bayesian confidence updates.
 *
 * Properties:
 * - confidenceLevel: User's self-reported understanding (5-point scale)
 * - responseTimeMs: Time taken to answer (optional future weighting)
 * - wasCorrect: Whether user got it right (based on self-grading)
 */
data class FlashcardEvidence(
    val confidenceLevel: ConfidenceLevel,
    val responseTimeMs: Long = 0,
    val wasCorrect: Boolean = true  // Self-graded, usually true for KNOWN_FAIRLY+
)