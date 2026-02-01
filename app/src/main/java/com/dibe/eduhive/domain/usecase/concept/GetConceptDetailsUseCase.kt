package com.dibe.eduhive.domain.usecase.concept

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * Use case for getting detailed information about a concept.
 * Includes concept data + associated flashcards.
 */
class GetConceptDetailsUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(conceptId: String): Result<ConceptDetails> {
        return try {
            val concept = conceptRepository.getConceptById(conceptId)
                ?: return Result.failure(IllegalArgumentException("Concept not found"))

            val flashcards = flashcardRepository.getFlashcardsForConcept(conceptId)

            Result.success(
                ConceptDetails(
                    concept = concept,
                    flashcards = flashcards,
                    flashcardCount = flashcards.size,
                    masteredFlashcards = flashcards.count { it.currentBox >= 4 }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ConceptDetails(
    val concept: Concept,
    val flashcards: List<Flashcard>,
    val flashcardCount: Int,
    val masteredFlashcards: Int
)