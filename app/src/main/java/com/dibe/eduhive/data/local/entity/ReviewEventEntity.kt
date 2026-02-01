package com.dibe.eduhive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.ReviewEvent
import com.dibe.eduhive.domain.model.enums.ReviewTargetType


@Entity(
    tableName = "review_events",
    indices = [Index("conceptId"), Index("targetId")]
)
data class ReviewEventEntity(
    @PrimaryKey val reviewId: String,
    val conceptId: String,
    val targetType: ReviewTargetType,
    val targetId: String,
    val outcome: Float,
    val responseTimeMs: Long,
    val timestamp: Long
){
    fun toDomain() = ReviewEvent(
        id = reviewId,
        conceptId = conceptId,
        targetType = targetType,
        targetId = targetId,
        outcome = outcome,
        responseTimeMs = responseTimeMs,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(event: ReviewEvent) = ReviewEventEntity(
            reviewId = event.id,
            conceptId = event.conceptId,
            targetType = event.targetType,
            targetId = event.targetId,
            outcome = event.outcome,
            responseTimeMs = event.responseTimeMs,
            timestamp = event.timestamp
        )
    }
}
