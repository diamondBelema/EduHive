package com.dibe.eduhive.domain.model

import com.dibe.eduhive.domain.model.enums.ReviewTargetType

/**
 * A record of a review event (flashcard or quiz answer).
 * Used for:
 * - Analytics and dashboards
 * - Learning history
 * - Debugging confidence updates
 * - Future: Advanced scheduling algorithms
 */
data class ReviewEvent(
    val id: String,
    val conceptId: String,
    val targetType: ReviewTargetType,  // FLASHCARD or QUIZ
    val targetId: String,  // ID of the flashcard or quiz question
    val outcome: Float,  // 0.0-1.0 score (for graded responses)
    val responseTimeMs: Long,
    val timestamp: Long
)