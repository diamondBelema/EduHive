package com.dibe.eduhive.domain.model.evidence

/**
 * Evidence generated from a quiz answer.
 * Provides high-signal input for Bayesian confidence updates.
 *
 * Key differences from FlashcardEvidence:
 * - Binary correctness (wasCorrect is objectively determined)
 * - Stronger likelihood weighting (correct = 0.95, incorrect = 0.1)
 * - Updates concept confidence only (doesn't affect flashcard scheduling)
 */
data class QuizEvidence(
    val wasCorrect: Boolean,
    val responseTimeMs: Long = 0
)