package com.dibe.eduhive.domain.model

/**
 * A Flashcard is a study tool linked to a Concept.
 * Multiple flashcards can represent different aspects of the same concept.
 *
 * Scheduling:
 * - Uses Leitner box system (1-5)
 * - Box 1 = weakest, reviewed daily
 * - Box 5 = mastered, reviewed every 15-30 days
 * - Boxes update only when the flashcard is reviewed
 *
 * Evidence:
 * - Provides low-signal, frequent evidence to update concept confidence
 * - User grades with ConfidenceLevel (UNKNOWN to MASTERED)
 */
data class Flashcard(
    val id: String,
    val conceptId: String,
    val front: String,
    val back: String,
    val currentBox: Int = 1,  // Leitner box (1-5)
    val lastSeenAt: Long? = null,
    val nextReviewAt: Long? = null
)