package com.dibe.eduhive.presentation.flashcardStudy.viewmodel

import com.dibe.eduhive.domain.model.Flashcard


// State
data class FlashcardStudyState(
    val flashcards: List<Flashcard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val completedCount: Int = 0,
    val error: String? = null
) {
    val currentFlashcard: Flashcard?
        get() = flashcards.getOrNull(currentIndex)

    val progress: Float
        get() = if (flashcards.isEmpty()) 0f else currentIndex.toFloat() / flashcards.size.toFloat()
}