package com.dibe.eduhive.domain.repository

import com.dibe.eduhive.data.repository.ConceptExtractionProgress
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Concept operations.
 */
interface ConceptRepository {

    suspend fun addConcepts(concepts: List<Concept>)

    suspend fun getConceptsForHive(hiveId: String): List<Concept>

    suspend fun getConceptById(conceptId: String): Concept?

    suspend fun getWeakestConcepts(hiveId: String, limit: Int = 5): List<Concept>

    suspend fun getAverageConfidence(hiveId: String): Double?

    /**
     * Extract concepts from material text with streaming progress.
     */
    fun extractConceptsFromMaterialStreaming(
        materialText: String,
        hiveId: String,
        hiveContext: String
    ): Flow<ConceptExtractionProgress>

    /**
     * Extract concepts from a document split into pages.
     * Preferred for PDFs and large documents to maintain context safety.
     */
    fun extractConceptsFromPagesStreaming(
        pages: List<String>,
        hiveId: String,
        hiveContext: String
    ): Flow<ConceptExtractionProgress>

    suspend fun updateWithFlashcardEvidence(
        conceptId: String,
        evidence: FlashcardEvidence
    )

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
