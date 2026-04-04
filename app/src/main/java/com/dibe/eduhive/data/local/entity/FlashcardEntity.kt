package com.dibe.eduhive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.Flashcard


@Entity(
    tableName = "flashcards",
    indices = [
        Index("conceptId"),
        Index("lastSeenAt"),
        Index("leitnerBox"),
        Index("nextReviewAt")   // ← enables efficient due-card queries
    ]
)
data class FlashcardEntity(
    @PrimaryKey val flashcardId: String,
    val conceptId: String,
    val front: String,
    val back: String,
    val difficulty: Int,
    val leitnerBox: Int,
    val lastSeenAt: Long?,
    val nextReviewAt: Long?     // ← persisted; null = never reviewed (always due)
) {
    fun toDomain() = Flashcard(
        id = flashcardId,
        conceptId = conceptId,
        front = front,
        back = back,
        currentBox = leitnerBox,
        lastSeenAt = lastSeenAt,
        nextReviewAt = nextReviewAt
    )

    companion object {
        fun fromDomain(flashcard: Flashcard) = FlashcardEntity(
            flashcardId = flashcard.id,
            conceptId = flashcard.conceptId,
            front = flashcard.front,
            back = flashcard.back,
            difficulty = 1,
            leitnerBox = flashcard.currentBox,
            lastSeenAt = flashcard.lastSeenAt,
            nextReviewAt = flashcard.nextReviewAt
        )
    }
}