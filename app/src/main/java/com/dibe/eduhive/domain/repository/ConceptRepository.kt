package com.dibe.eduhive.domain.repository

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence

/**
 * Repository interface for Concept operations.
 * This is where the Learning Engine integrates with data persistence.
 */
interface ConceptRepository {

    suspend fun addConcepts(concepts: List<Concept>)

    suspend fun getConceptsForHive(hiveId: String): List<Concept>

    suspend fun getConceptById(conceptId: String): Concept?

    /**
     * Get weakest concepts (lowest confidence) for a hive.
     * Used for dashboards and prioritization.
     */
    suspend fun getWeakestConcepts(hiveId: String, limit: Int = 5): List<Concept>

    /**
     * Get average confidence across all concepts in a hive.
     * Used for overall progress tracking.
     */
    suspend fun getAverageConfidence(hiveId: String): Double?

    /**
     * Update concept confidence based on flashcard evidence.
     * Internally uses LearningEngine.
     */
    suspend fun updateWithFlashcardEvidence(
        conceptId: String,
        evidence: FlashcardEvidence
    )

    /**
     * Update concept confidence based on quiz evidence.
     * Internally uses LearningEngine.
     */
    suspend fun updateWithQuizEvidence(
        conceptId: String,
        evidence: QuizEvidence
    )

    suspend fun deleteConcept(conceptId: String)

    suspend fun extractConceptsFromMaterial(
        materialText: String,
        hiveId: String,
        hiveContext: String = ""
    ): List<Concept>
}