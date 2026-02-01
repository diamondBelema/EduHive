package com.dibe.eduhive.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.Concept
import kotlin.text.toDouble

@Entity(
    tableName = "concepts",
    indices = ([
        Index("hiveId"),
        Index("lastReviewedAt"),     // ← New
        Index("confidenceScore")     // ← New
    ]),
    foreignKeys = [
        ForeignKey(
            entity = HiveEntity::class,
            parentColumns = ["hiveId"],
            childColumns = ["hiveId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ConceptEntity(
    @PrimaryKey val conceptId: String,
    val hiveId: String,
    val name: String,
    val description: String?,
    val confidenceScore: Float,
    val decayRate: Float,
    val lastReviewedAt: Long?
) {
    // Entity → Domain
    fun toDomain() = Concept(
        id = conceptId,
        hiveId = hiveId,
        name = name,
        description = description,
        confidence = confidenceScore.toDouble(),
        lastReviewedAt = lastReviewedAt
    )

    companion object {
        // Domain → Entity
        fun fromDomain(concept: Concept) = ConceptEntity(
            conceptId = concept.id,
            hiveId = concept.hiveId,
            name = concept.name,
            description = concept.description,
            confidenceScore = concept.confidence.toFloat(),
            decayRate = 0.95f,
            lastReviewedAt = concept.lastReviewedAt
        )
    }
}
