package com.dibe.eduhive.domain.repository

import com.dibe.eduhive.data.repository.FlashcardGenerationProgress
import com.dibe.eduhive.domain.model.Flashcard
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Flashcard operations.
 */
interface FlashcardRepository {

    suspend fun addFlashcards(flashcards: List<Flashcard>)

    suspend fun getFlashcardsForConcept(conceptId: String): List<Flashcard>

    suspend fun getFlashcardById(flashcardId: String): Flashcard?

    /**
     * Get flashcards due for review.
     * Uses Leitner scheduling: returns cards where now >= nextReviewAt.
     *
     * @param maxBox Maximum Leitner box to include (default 5 = all boxes)
     * @param limit Maximum number of cards to return
     */
    suspend fun getDueFlashcards(
        maxBox: Int = 5,
        limit: Int = 20,
        hiveId: String? = null
    ): List<Flashcard>

    suspend fun getStudyFallbackFlashcards(
        hiveId: String,
        limit: Int = 20
    ): List<Flashcard>

    /**
     * Update flashcard's Leitner box and schedule.
     * Called after each review.
     *
     * Box progression:
     * - Correct answer → move up one box
     * - Incorrect answer → reset to box 1
     */
    suspend fun updateLeitnerBox(
        flashcardId: String,
        newBox: Int,
        lastSeenAt: Long,
        nextReviewAt: Long
    )

    suspend fun generateFlashcardsForConcept(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        count: Int = 5
    ): List<Flashcard>

    fun generateFlashcardsForConceptStreaming(
        conceptId: String,
        conceptName: String,
        conceptDescription: String?,
        count: Int
    ): Flow<FlashcardGenerationProgress>

    /**
     * Generate flashcards for a batch of concepts in a single AI request.
     *
     * Grouping 3–5 concepts per call improves global context, reduces duplicate
     * questions, and produces more diverse flashcards.
     *
     * @param concepts List of (conceptId, conceptName, conceptDescription) triples.
     * @param countPerConcept Desired number of flashcards per concept.
     */
    suspend fun generateFlashcardsForConceptsBatch(
        concepts: List<Triple<String, String, String?>>,
        countPerConcept: Int = 5
    ): List<Flashcard>

    suspend fun deleteFlashcard(flashcardId: String)
}
