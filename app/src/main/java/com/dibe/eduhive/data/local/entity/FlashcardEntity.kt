package com.dibe.eduhive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.Flashcard


@Entity(
    tableName = "flashcards",
    indices = [
        Index("conceptId"),
        Index("lastSeenAt"),  // ← New
        Index("leitnerBox")   // ← New
    ]
)
data class FlashcardEntity(
    @PrimaryKey val flashcardId: String,
    val conceptId: String,
    val front: String,
    val back: String,
    val difficulty: Int,
    val leitnerBox: Int,
    val lastSeenAt: Long?
) {
    fun toDomain() = Flashcard(
        id = flashcardId,
        conceptId = conceptId,
        front = front,
        back = back,
        currentBox = leitnerBox,
        lastSeenAt = lastSeenAt,
        nextReviewAt = calculateNextReview(leitnerBox, lastSeenAt)
    )

    companion object {
        fun fromDomain(flashcard: Flashcard) = FlashcardEntity(
            flashcardId = flashcard.id,
            conceptId = flashcard.conceptId,
            front = flashcard.front,
            back = flashcard.back,
            difficulty = 1,  // Default difficulty
            leitnerBox = flashcard.currentBox,
            lastSeenAt = flashcard.lastSeenAt
        )
    }
}

// Helper for calculating next review time
private fun calculateNextReview(box: Int, lastSeen: Long?): Long? {
    if (lastSeen == null) return null

    val daysToAdd = when (box) {
        1 -> 1L
        2 -> 3L
        3 -> 7L
        4 -> 14L
        5 -> 30L
        else -> 1L
    }

    return lastSeen + (daysToAdd * 24L * 60L * 60L * 1000L)
}

