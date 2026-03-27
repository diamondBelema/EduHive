package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.ConceptEntity
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.ai.ConceptExtractionState
import com.dibe.eduhive.data.source.local.ConceptLocalDataSource
import com.dibe.eduhive.domain.engine.LearningEngine
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.evidence.FlashcardEvidence
import com.dibe.eduhive.domain.model.evidence.QuizEvidence
import com.dibe.eduhive.domain.repository.ConceptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject


class ConceptRepositoryImpl @Inject constructor(
    private val localDataSource: ConceptLocalDataSource,
    private val aiDataSource: AIDataSource,
    private val learningEngine: LearningEngine
) : ConceptRepository {

    companion object {
        private const val MIN_TARGET_CONCEPTS = 10
    }

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
        val entity = localDataSource.getById(conceptId) ?: return
        val concept = entity.toDomain()

        val updatedConcept = learningEngine.applyFlashcardEvidence(
            concept = concept,
            evidence = evidence
        )

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
        val entity = localDataSource.getById(conceptId) ?: return
        val concept = entity.toDomain()

        val updatedConcept = learningEngine.applyQuizEvidence(
            concept = concept,
            evidence = evidence
        )

        localDataSource.updateConfidence(
            id = updatedConcept.id,
            score = updatedConcept.confidence.toFloat(),
            time = updatedConcept.lastReviewedAt ?: System.currentTimeMillis()
        )
    }

    override suspend fun deleteConcept(conceptId: String) {
        localDataSource.deleteById(conceptId)
    }

    override fun extractConceptsFromMaterialStreaming(
        materialText: String,
        hiveId: String,
        hiveContext: String
    ): Flow<ConceptExtractionProgress> = extractConceptsFromPagesStreaming(listOf(materialText), hiveId, hiveContext)

    override fun extractConceptsFromPagesStreaming(
        pages: List<String>,
        hiveId: String,
        hiveContext: String
    ): Flow<ConceptExtractionProgress> = flow {
        emit(ConceptExtractionProgress.Loading)

        aiDataSource.extractConceptsFromPagesStreaming(
            pages = pages,
            hiveContext = hiveContext
        ).collect { state ->
            when (state) {
                is ConceptExtractionState.Loading -> {
                    emit(ConceptExtractionProgress.Loading)
                }
                is ConceptExtractionState.Progress -> {
                    emit(ConceptExtractionProgress.Processing(state.percent))
                }
                is ConceptExtractionState.Success -> {
                    val domainConcepts = state.concepts.map { aiConcept ->
                        Concept(
                            id = UUID.randomUUID().toString(),
                            hiveId = hiveId,
                            name = aiConcept.name,
                            description = aiConcept.description,
                            confidence = 0.3,
                            lastReviewedAt = null
                        )
                    }

                    // Save to database
                    addConcepts(domainConcepts)

                    emit(ConceptExtractionProgress.Success(domainConcepts))
                }
                is ConceptExtractionState.Error -> {
                    emit(ConceptExtractionProgress.Error(state.message))
                }
            }
        }
    }

    override suspend fun extractConceptsFromMaterial(
        materialText: String,
        hiveId: String,
        hiveContext: String,
    ): List<Concept> {
        val pages = materialText.chunked(4000)
        return extractConceptsFromDocumentPages(pages, hiveId, hiveContext)
    }

    private suspend fun extractConceptsSingle(
        text: String,
        hiveId: String,
        hiveContext: String
    ): List<Concept> {
        val result = aiDataSource.extractConcepts(text, hiveContext)

        val extractedConcepts = result.getOrElse {
            return emptyList()
        }

        val domainConcepts = extractedConcepts.map { aiConcept ->
            Concept(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                name = aiConcept.name,
                description = aiConcept.description,
                confidence = 0.3,
                lastReviewedAt = null
            )
        }

        addConcepts(domainConcepts)
        return domainConcepts
    }

    private suspend fun extractConceptsFromDocumentPages(
        pages: List<String>,
        hiveId: String,
        hiveContext: String
    ): List<Concept> {
        val result = aiDataSource.extractConceptsFromDocument(
            pages = pages,
            hiveContext = hiveContext
        )

        val concepts = result.getOrElse {
            return emptyList()
        }

        val boostedConcepts = if (concepts.size < MIN_TARGET_CONCEPTS) {
            val mergedText = pages.joinToString("\n\n").take(8000)
            val retry = aiDataSource.extractConcepts(mergedText, hiveContext).getOrDefault(emptyList())
            (concepts + retry).distinctBy { it.name.trim().lowercase() }
        } else {
            concepts
        }

        val domainConcepts = boostedConcepts.map { aiConcept ->
            Concept(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                name = aiConcept.name,
                description = aiConcept.description,
                confidence = 0.3,
                lastReviewedAt = null
            )
        }

        addConcepts(domainConcepts)
        return domainConcepts
    }
}

/**
 * Progress states for concept extraction UI.
 */
sealed class ConceptExtractionProgress {
    object Loading : ConceptExtractionProgress()
    data class Processing(val percent: Int) : ConceptExtractionProgress()
    data class Success(val concepts: List<Concept>) : ConceptExtractionProgress()
    data class Error(val message: String) : ConceptExtractionProgress()
}
