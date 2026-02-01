package com.dibe.eduhive.domain.model

/**
 * A Hive represents a focused learning context (e.g., a subject, course, or goal).
 * It's the top-level container for all learning materials, concepts, flashcards, and quizzes.
 *
 * Examples:
 * - "Human Anatomy – 100L"
 * - "Histology"
 * - "Physics: Mechanics"
 */
data class Hive(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
    val lastAccessedAt: Long
)