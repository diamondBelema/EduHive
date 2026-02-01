package com.dibe.eduhive.domain.engine

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence

/**
 * Strategy interface for updating concept confidence based on evidence.
 * Allows swapping between different algorithms (Bayesian, Converging, Half-Life, etc.)
 * without changing the rest of the system.
 *
 * Follows the Strategy Pattern and Open/Closed Principle.
 */
interface ConfidenceUpdateStrategy {

    /**
     * Update concept confidence based on flashcard evidence.
     */
    fun updateFromFlashcard(
        current: Concept,
        evidence: FlashcardEvidence,
        now: Long
    ): Concept

    /**
     * Update concept confidence based on quiz evidence.
     */
    fun updateFromQuiz(
        current: Concept,
        evidence: QuizEvidence,
        now: Long
    ): Concept

    /**
     * Apply time-based decay to concept confidence.
     * Called before applying new evidence.
     */
    fun applyDecay(
        current: Concept,
        now: Long
    ): Concept
}