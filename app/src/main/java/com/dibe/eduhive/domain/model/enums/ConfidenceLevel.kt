package com.dibe.eduhive.domain.model.enums

/**
 * Represents the user's self-reported confidence level when reviewing a flashcard.
 * Used to generate evidence for Bayesian confidence updates.
 */
enum class ConfidenceLevel {
    UNKNOWN,        // 0.1 likelihood - User has no idea
    KNOWN_LITTLE,   // 0.3 likelihood - Vague understanding
    KNOWN_FAIRLY,   // 0.6 likelihood - Decent grasp
    KNOWN_WELL,     // 0.8 likelihood - Strong understanding
    MASTERED        // 0.95 likelihood - Complete mastery
}