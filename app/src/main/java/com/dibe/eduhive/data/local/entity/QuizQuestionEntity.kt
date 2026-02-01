package com.dibe.eduhive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.model.enums.QuizQuestionType
import kotlinx.serialization.json.Json


@Entity(
    tableName = "quiz_questions",
    indices = [Index("quizId")]
)
data class QuizQuestionEntity(
    @PrimaryKey val questionId: String,
    val quizId: String,
    val question: String,
    val type: QuizQuestionType,
    val correctAnswer: String,
    val options: String? // JSON encoded for MCQ
){
    fun toDomain() = QuizQuestion(
        id = questionId,
        quizId = quizId,
        question = question,
        type = type,
        correctAnswer = correctAnswer,
        options = options?.let {
            Json.decodeFromString<List<String>>(it)
        }
    )

    companion object {
        fun fromDomain(question: QuizQuestion) = QuizQuestionEntity(
            questionId = question.id,
            quizId = question.quizId,
            question = question.question,
            type = question.type,
            correctAnswer = question.correctAnswer,
            options = question.options?.let {
                Json.encodeToString(it)
            }
        )
    }
}

