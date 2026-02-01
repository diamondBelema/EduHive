package com.dibe.eduhive.domain.model

/**
 * A Concept represents an idea or unit of knowledge within a Hive.
 * Concepts are the core of EduHive's learning engine.
 *
 * Key properties:
 * - confidence: Bayesian posterior probability (0.0 - 1.0) of user mastery
 * - Multiple flashcards and quiz questions can be linked to one concept
 * - Confidence is updated incrementally based on evidence from reviews
 *
 * Examples:
 * - "Function of mitochondria"
 * - "Structure of epithelial tissue"
 * - "Newton's First Law"
 */
data class Concept(
    val id: String,
    val hiveId: String,
    val name: String,
    val description: String? = null,
    val confidence: Double = 0.3,  // Start at 30% - modest prior
    val lastReviewedAt: Long? = null
)
