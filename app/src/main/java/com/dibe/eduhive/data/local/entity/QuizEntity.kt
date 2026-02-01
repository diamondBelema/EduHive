package com.dibe.eduhive.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.dibe.eduhive.domain.model.Quiz


@Entity(
    tableName = "quizzes",
    indices = [Index("conceptId")]
)
data class QuizEntity(
    @PrimaryKey val quizId: String,
    val conceptId: String,
    val title: String,
    val createdAt: Long
){
    // Entity → Domain
    fun toDomain() = Quiz(
        id = quizId,
        conceptId = conceptId,
        title = title,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(quiz: Quiz) = QuizEntity(
            quizId = quiz.id,
            conceptId = quiz.conceptId,
            title = quiz.title,
            createdAt = quiz.createdAt
        )
    }
}

data class QuizWithQuestions(
    @Embedded val quiz: QuizEntity,
    @Relation(
        parentColumn = "quizId",
        entityColumn = "quizId"
    )
    val questions: List<QuizQuestionEntity>
)
