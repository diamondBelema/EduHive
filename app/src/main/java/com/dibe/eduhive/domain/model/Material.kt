package com.dibe.eduhive.domain.model

/**
 * Represents uploaded study material within a Hive.
 * Materials are processed to extract concepts, which then generate flashcards and quizzes.
 */
data class Material(
    val id: String,
    val hiveId: String,
    val title: String,
    val type: MaterialType,
    val localPath: String,
    val processed: Boolean = false,
    val createdAt: Long
)