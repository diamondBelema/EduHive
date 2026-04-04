package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.FlashcardEntity
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.ai.FlashcardGenerationState
import com.dibe.eduhive.data.source.local.FlashcardLocalDataSource
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject


class FlashcardRepositoryImpl @Inject constructor(
    private val localDataSource: FlashcardLocalDataSource,
    private val aiDataSource: AIDataSource
) : FlashcardRepository {

    override suspend fun addFlashcards(flashcards: List<Flashcard>) {
        val entities = flashcards.map { FlashcardEntity.fromDomain(it) }
        localDataSource.insertAll(entities)
    }

    override suspend fun getFlashcardsForConcept(conceptId: String): List<Flashcard> {
        return localDataSource.getForConcept(conceptId).map { it.toDomain() }
    }

    override suspend fun getFlashcardById(flashcardId: String): Flashcard? {
        return localDataSource.getById(flashcardId)?.toDomain()
    }

    override suspend fun getDueFlashcards(maxBox: Int, limit: Int, hiveId: String?): List<Flashcard> {
        val now = System.currentTimeMillis()
        val due = if (hiveId.isNullOrBlank()) {
            localDataSource.getDue(maxBox, now)
        } else {
            localDataSource.getDueForHive(hiveId, maxBox, now)
        }
        return due.take(limit).map { it.toDomain() }
    }

    override suspend fun getStudyFallbackFlashcards(hiveId: String, limit: Int): List<Flashcard> {
        return localDataSource.getAllForHiveStudy(hiveId)
            .take(limit)
            .map { it.toDomain() }
    }

    override suspend fun getAllFlashcardsForStudy(limit: Int): List<Flashcard> {
        return localDataSource.getAllForStudy()
            .take(limit)
            .map { it.toDomain() }
    }

    override suspend fun updateLeitnerBox(
        flashcardId: String,
        newBox: Int,
        lastSeenAt: Long,
        nextReviewAt: Long
    ) {
        localDataSource.updateLeitner(
            id = flashcardId,
            box = newBox.coerceIn(1, 5),
            lastSeenAt = lastSeenAt,
            nextReviewAt = nextReviewAt
        )
    }

    override suspend fun deleteFlashcard(flashcardId: String) {
        localDataSource.deleteById(flashcardId)
    }

    override suspend fun deleteAllFlashcardsForConcept(conceptId: String) {
        localDataSource.deleteAllForConcept(conceptId)
    }

    /**
     * 🚀 STREAMING: Generate flashcards with real-time status updates.
     */
    override fun generateFlashcardsForConceptStreaming(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        count: Int,
        skipValidation: Boolean
    ): Flow<FlashcardGenerationProgress> = flow {
        emit(FlashcardGenerationProgress.Loading)

        aiDataSource.generateFlashcardsStreaming(
            conceptName = conceptName,
            conceptDescription = conceptDescription ?: "",
            count = count,
            skipValidation = skipValidation
        ).collect { state ->
            when (state) {
                is FlashcardGenerationState.Loading -> {
                    emit(FlashcardGenerationProgress.Loading)
                }
                is FlashcardGenerationState.Retrying -> {
                    emit(FlashcardGenerationProgress.Retrying(state.attempt))
                }
                is FlashcardGenerationState.Validating -> {
                    emit(FlashcardGenerationProgress.Validating)
                }
                is FlashcardGenerationState.Success -> {
                    val flashcards = state.flashcards.map { generated ->
                        Flashcard(
                            id = UUID.randomUUID().toString(),
                            conceptId = conceptId,
                            front = generated.front,
                            back = generated.back,
                            currentBox = 1,
                            lastSeenAt = null,
                            nextReviewAt = System.currentTimeMillis()
                        )
                    }

                    // Save to database
                    addFlashcards(flashcards)

                    emit(FlashcardGenerationProgress.Success(flashcards, state.rejectedCount))
                }
                is FlashcardGenerationState.Error -> {
                    emit(FlashcardGenerationProgress.Error(state.message))
                }
            }
        }
    }

    /**
     * Standard suspend function (delegates to streaming internally).
     */
    override suspend fun generateFlashcardsForConcept(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        count: Int
    ): List<Flashcard> {
        var result: List<Flashcard> = emptyList()

        generateFlashcardsForConceptStreaming(
            conceptId, conceptName, conceptDescription, count, skipValidation = false
        ).collect { progress ->
            when (progress) {
                is FlashcardGenerationProgress.Success -> {
                    result = progress.flashcards
                }
                is FlashcardGenerationProgress.Error -> {
                    // Return empty list on error
                }
                else -> { /* Ignore loading states */ }
            }
        }

        return result
    }

    /**
     * Generate flashcards for a batch of concepts in a single AI request.
     *
     * Groups up to [AIDataSource.BATCH_SIZE] concepts per call to improve
     * model context, reduce duplicate questions, and increase flashcard diversity.
     * Each flashcard is attributed to the correct concept via CONCEPT tags.
     * Results are deduplicated by question text before being saved.
     */
    override suspend fun generateFlashcardsForConceptsBatch(
        concepts: List<Triple<String, String, String?>>,
        countPerConcept: Int
    ): List<Flashcard> {
        val allFlashcards = mutableListOf<Flashcard>()

        concepts.chunked(AIDataSource.BATCH_SIZE).forEach { batch ->
            val conceptPairs = batch.map { (_, name, description) ->
                Pair(name, description ?: "")
            }

            val result = aiDataSource.generateFlashcardsBatch(conceptPairs, countPerConcept)
            result.onSuccess { indexedCards ->
                indexedCards.forEach { (conceptIndex, generated) ->
                    // conceptIndex is 1-based; clamp to batch size for safety
                    val safeIndex = (conceptIndex - 1).coerceIn(0, batch.size - 1)
                    val (conceptId, _, _) = batch[safeIndex]
                    allFlashcards.add(
                        Flashcard(
                            id = UUID.randomUUID().toString(),
                            conceptId = conceptId,
                            front = generated.front,
                            back = generated.back,
                            currentBox = 1,
                            lastSeenAt = null,
                            nextReviewAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        // Deduplicate across all batches by front text
        val deduped = allFlashcards.distinctBy { it.front.lowercase().trim() }
        addFlashcards(deduped)
        return deduped
    }
}

/**
 * Progress states for flashcard generation UI.
 */
sealed class FlashcardGenerationProgress {
    object Loading : FlashcardGenerationProgress()
    data class Retrying(val attempt: Int) : FlashcardGenerationProgress()
    object Validating : FlashcardGenerationProgress()
    data class Success(
        val flashcards: List<Flashcard>,
        val rejectedCount: Int = 0
    ) : FlashcardGenerationProgress()
    data class Error(val message: String) : FlashcardGenerationProgress()
}