package com.dibe.eduhive.domain.model

/**
 * A Quiz is a collection of questions linked to a Concept.
 * Quizzes provide high-signal evidence for concept mastery.
 *
 * Key differences from Flashcards:
 * - Stronger evidence weight (quiz correct = 0.95 likelihood)
 * - Updates concept confidence only (not scheduled like flashcards)
 * - Used for validation and mastery checks
 */
data class Quiz(
    val id: String,
    val conceptId: String,
    val title: String,
    val createdAt: Long
)