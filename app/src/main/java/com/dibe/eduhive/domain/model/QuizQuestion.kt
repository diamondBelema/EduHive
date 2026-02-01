package com.dibe.eduhive.domain.model

import com.dibe.eduhive.domain.model.enums.QuizQuestionType

/**
 * A single question within a Quiz.
 * Supports multiple question types (MCQ, True/False, Short Answer).
 */
data class QuizQuestion(
    val id: String,
    val quizId: String,
    val question: String,
    val type: QuizQuestionType,
    val correctAnswer: String,
    val options: List<String>? = null  // For MCQ only
)