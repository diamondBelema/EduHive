package com.dibe.eduhive.domain.model.enums

/**
 * Indicates whether a review event was for a flashcard or a quiz.
 * Used in ReviewEvent for tracking evidence source.
 */
enum class ReviewTargetType {
    FLASHCARD,
    QUIZ
}