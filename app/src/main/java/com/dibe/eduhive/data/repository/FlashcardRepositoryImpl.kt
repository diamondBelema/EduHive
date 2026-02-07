package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.FlashcardEntity
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.local.FlashcardLocalDataSource
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.repository.FlashcardRepository
import java.util.UUID
import jakarta.inject.Inject


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

    override suspend fun getDueFlashcards(maxBox: Int, limit: Int): List<Flashcard> {
        return localDataSource.getDue(maxBox).take(limit).map { it.toDomain() }
    }

    override suspend fun updateLeitnerBox(
        flashcardId: String,
        newBox: Int,
        lastSeenAt: Long,
        nextReviewAt: Long
    ) {
        localDataSource.updateLeitner(
            id = flashcardId,
            box = newBox.coerceIn(1, 5), // Keep within 1-5 range
            time = lastSeenAt
        )
    }

    override suspend fun deleteFlashcard(flashcardId: String) {
        localDataSource.deleteAllForConcept(flashcardId)
    }

    /**
     * NEW: Generate flashcards for a concept using AI.
     */
    override suspend fun generateFlashcardsForConcept(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        count: Int
    ): List<Flashcard> {
        // Use AI to generate flashcards
        val result= aiDataSource.generateFlashcards(
            conceptName = conceptName,
            conceptDescription = conceptDescription ?: "",
            count = count
        )

        val extractedFlashcard = result.getOrElse { error ->
            // You can log this later if you want
            return emptyList()
        }

        // Convert to domain models
        val flashcards = extractedFlashcard.map { flashcard ->
            Flashcard(
                id = UUID.randomUUID().toString(),
                conceptId = conceptId,
                front = flashcard.front,
                back = flashcard.back,
                currentBox = 1, // Start in box 1
                lastSeenAt = null,
                nextReviewAt = System.currentTimeMillis() // Due now
            )
        }

        // Save to database
        addFlashcards(flashcards)

        return flashcards
    }
}