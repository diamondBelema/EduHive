package com.dibe.eduhive.domain.usecase.study

import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * Use case for getting flashcards due for review.
 * Uses Leitner scheduling to determine which cards are due.
 */
class GetNextReviewItemsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(
        limit: Int = 20,
        includeNewCards: Boolean = true
    ): Result<List<Flashcard>> {
        return try {
            val maxBox = if (includeNewCards) 5 else 4

            val dueCards = flashcardRepository.getDueFlashcards(
                maxBox = maxBox,
                limit = limit
            )

            // Sort by priority:
            // 1. Overdue cards (box 1 first)
            // 2. Cards in lower boxes (need more practice)
            val sorted = dueCards.sortedWith(
                compareBy<Flashcard> { it.currentBox }
                    .thenBy { it.lastSeenAt ?: 0 }
            )

            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}