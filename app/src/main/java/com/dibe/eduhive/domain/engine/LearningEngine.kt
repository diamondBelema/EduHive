package com.dibe.eduhive.domain.engine

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence

/**
 * Learning Engine orchestrates confidence updates.
 * Pure business logic, no database or UI dependencies.
 *
 * Responsibilities:
 * - Apply evidence to concepts
 * - Delegate to the configured strategy
 * - Keep confidence calculations consistent
 *
 * Design:
 * - Strategy pattern allows swapping algorithms
 * - Testable in isolation
 * - Offline-friendly (pure computation)
 */
class LearningEngine(
    private val strategy: ConfidenceUpdateStrategy
) {

    /**
     * Apply flashcard evidence to a concept.
     * Returns updated concept with new confidence score.
     */
    fun applyFlashcardEvidence(
        concept: Concept,
        evidence: FlashcardEvidence,
        now: Long = System.currentTimeMillis()
    ): Concept {
        return strategy.updateFromFlashcard(concept, evidence, now)
    }

    /**
     * Apply quiz evidence to a concept.
     * Returns updated concept with new confidence score.
     */
    fun applyQuizEvidence(
        concept: Concept,
        evidence: QuizEvidence,
        now: Long = System.currentTimeMillis()
    ): Concept {
        return strategy.updateFromQuiz(concept, evidence, now)
    }

    /**
     * Get current confidence after decay (without new evidence).
     * Useful for dashboards and scheduling.
     */
    fun getCurrentConfidence(
        concept: Concept,
        now: Long = System.currentTimeMillis()
    ): Double {
        val decayed = strategy.applyDecay(concept, now)
        return decayed.confidence
    }
}