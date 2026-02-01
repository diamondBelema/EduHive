package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.ConceptEntity
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.local.ConceptLocalDataSource
import com.dibe.eduhive.domain.engine.LearningEngine
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence
import com.dibe.eduhive.domain.repository.ConceptRepository
import java.util.UUID
import jakarta.inject.Inject


class ConceptRepositoryImpl @Inject constructor (
    private val localDataSource: ConceptLocalDataSource,
    private val aiDataSource: AIDataSource,
    private val learningEngine: LearningEngine
) : ConceptRepository {

    override suspend fun addConcepts(concepts: List<Concept>) {
        val entities = concepts.map { ConceptEntity.fromDomain(it) }
        localDataSource.insertAll(entities)
    }

    override suspend fun getConceptsForHive(hiveId: String): List<Concept> {
        return localDataSource.getForHive(hiveId).map { it.toDomain() }
    }

    override suspend fun getConceptById(conceptId: String): Concept? {
        return localDataSource.getById(conceptId)?.toDomain()
    }

    override suspend fun getWeakestConcepts(hiveId: String, limit: Int): List<Concept> {
        return localDataSource.getWeakestConcepts(hiveId, limit).map { it.toDomain() }
    }

    override suspend fun getAverageConfidence(hiveId: String): Double? {
        return localDataSource.getAverageConfidence(hiveId)?.toDouble()
    }

    override suspend fun updateWithFlashcardEvidence(
        conceptId: String,
        evidence: FlashcardEvidence
    ) {
        // Get current concept
        val entity = localDataSource.getById(conceptId) ?: return
        val concept = entity.toDomain()

        // Apply Bayesian update via LearningEngine
        val updatedConcept = learningEngine.applyFlashcardEvidence(
            concept = concept,
            evidence = evidence
        )

        // Save back to database
        localDataSource.updateConfidence(
            id = updatedConcept.id,
            score = updatedConcept.confidence.toFloat(),
            time = updatedConcept.lastReviewedAt ?: System.currentTimeMillis()
        )
    }

    override suspend fun updateWithQuizEvidence(
        conceptId: String,
        evidence: QuizEvidence
    ) {
        // Get current concept
        val entity = localDataSource.getById(conceptId) ?: return
        val concept = entity.toDomain()

        // Apply Bayesian update via LearningEngine
        val updatedConcept = learningEngine.applyQuizEvidence(
            concept = concept,
            evidence = evidence
        )

        // Save back to database
        localDataSource.updateConfidence(
            id = updatedConcept.id,
            score = updatedConcept.confidence.toFloat(),
            time = updatedConcept.lastReviewedAt ?: System.currentTimeMillis()
        )
    }

    override suspend fun deleteConcept(conceptId: String) {
        localDataSource.deleteAllForHive(conceptId)
    }

    /**
     * NEW: Extract concepts from material text using AI.
     */
    override suspend fun extractConceptsFromMaterial(
        materialText: String,
        hiveId: String,
        hiveContext: String,
    ): List<Concept> {
        // Use AI to extract concepts
        val extracted = aiDataSource.extractConcepts(materialText, hiveContext)

        // Convert to domain models
        val concepts = extracted.map {
            Concept(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                name = it.name,
                description = it.description,
                confidence = 0.3 // Initial confidence: 30%
            )
        }

        // Save to database
        addConcepts(concepts)

        return concepts
    }
}