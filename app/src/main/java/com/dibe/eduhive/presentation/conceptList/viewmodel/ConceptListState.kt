package com.dibe.eduhive.presentation.conceptList.viewmodel

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion

data class ConceptListState(
    val isLoading: Boolean = false,
    val concepts: List<Concept> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isGenerating: Boolean = false,
    val generationProgress: String? = null,
    val generationProgressFloat: Float = 0f,
    val generationCompleted: Int = 0,
    val generationTotal: Int = 0,
    val currentConceptName: String? = null,
    val generatedFlashcards: List<Flashcard> = emptyList(),
    val generatedQuizPairs: List<Pair<Quiz, List<QuizQuestion>>> = emptyList(),
    val generationMode: GenerationMode? = null,
    val error: String? = null
) {
    val isSelectionActive: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size
}
