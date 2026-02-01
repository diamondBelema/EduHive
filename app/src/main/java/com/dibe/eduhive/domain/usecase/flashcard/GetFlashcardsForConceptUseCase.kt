package com.dibe.eduhive.domain.usecase.flashcard

import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * Use case for retrieving all flashcards for a specific concept.
 */
class GetFlashcardsForConceptUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(conceptId: String): Result<List<Flashcard>> {
        return try {
            val flashcards = flashcardRepository.getFlashcardsForConcept(conceptId)
                .sortedBy { it.currentBox } // Weakest (box 1) first

            Result.success(flashcards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}