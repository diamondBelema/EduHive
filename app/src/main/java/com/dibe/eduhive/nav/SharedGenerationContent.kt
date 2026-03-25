package com.dibe.eduhive.nav

import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple in-memory holder to pass generated content from ConceptListViewModel
 * to GenerationPreviewViewModel without serializing large lists through nav args.
 *
 * Injected as a Singleton so both ViewModels share the same instance.
 * Content is cleared after the preview screen reads it.
 */
@Singleton
class SharedGenerationContent @Inject constructor() {
    var flashcards: List<Flashcard> = emptyList()
    var quizPairs: List<Pair<Quiz, List<QuizQuestion>>> = emptyList()

    fun set(flashcards: List<Flashcard>, quizPairs: List<Pair<Quiz, List<QuizQuestion>>>) {
        this.flashcards = flashcards
        this.quizPairs = quizPairs
    }

    fun consume(): Pair<List<Flashcard>, List<Pair<Quiz, List<QuizQuestion>>>> {
        val result = Pair(flashcards, quizPairs)
        flashcards = emptyList()
        quizPairs = emptyList()
        return result
    }
}