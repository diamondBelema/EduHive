package com.dibe.eduhive.domain.usecase.flashcard

import com.dibe.eduhive.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * Use case for deleting a single flashcard.
 */
class DeleteFlashcardUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(flashcardId: String): Result<Unit> {
        return try {
            flashcardRepository.deleteFlashcard(flashcardId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

