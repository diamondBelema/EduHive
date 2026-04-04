package com.dibe.eduhive.data.source.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates the quality of AI-generated flashcards before they are stored.
 *
 * Philosophy: be permissive enough that small models don't get stuck in
 * endless retry loops. The goal is to filter out genuinely broken cards
 * (empty, too short, placeholder text) not to enforce stylistic rules
 * that small models struggle to follow consistently.
 */
@Singleton
class FlashcardValidator @Inject constructor() {

    companion object {
        /** Minimum quality score (0–1) for a flashcard to be accepted. */
        const val MIN_QUALITY_SCORE = 0.5f  // Lowered from 0.7 — small models need more room

        /** Minimum character length for the question (front). */
        private const val MIN_FRONT_LENGTH = 8

        /** Minimum character length for the answer (back). */
        private const val MIN_BACK_LENGTH = 4

        /** Optimal answer length range (characters). */
        private const val OPTIMAL_ANSWER_MIN_LENGTH = 8
        private const val OPTIMAL_ANSWER_MAX_LENGTH = 200

        /** Terms that indicate a vague or broken placeholder response. */
        private val VAGUE_TERMS = listOf(
            "undefined", "null", "n/a", "placeholder",
            "example here", "fill in", "[question", "[answer", "[front", "[back",
            "something"
        )
    }

    fun validate(card: GeneratedFlashcard): FlashcardQuality {
        val issues = mutableListOf<String>()

        // Hard failures — card is genuinely unusable
        if (card.front.isBlank() || card.front.length < MIN_FRONT_LENGTH) {
            issues.add("Question too short")
        }
        if (card.back.isBlank() || card.back.length < MIN_BACK_LENGTH) {
            issues.add("Answer too short")
        }

        // Question must end with a question mark
        if (!card.front.trimEnd().endsWith("?")) {
            issues.add("Question must end with '?'")
        }

        // Check for vague or broken placeholder output
        for (term in VAGUE_TERMS) {
            if (card.front.contains(term, ignoreCase = true)) {
                issues.add("Question contains vague term: '$term'")
            }
            if (card.back.contains(term, ignoreCase = true)) {
                issues.add("Answer contains vague term: '$term'")
            }
        }

        // Soft check: front and back shouldn't be identical
        if (card.front.trim().lowercase() == card.back.trim().lowercase()) {
            issues.add("Question and answer are identical")
        }

        val score = calculateScore(card, issues)
        return FlashcardQuality(
            isValid = issues.isEmpty() && score >= MIN_QUALITY_SCORE,
            score = score,
            issues = issues
        )
    }

    fun filterValid(cards: List<GeneratedFlashcard>): List<GeneratedFlashcard> {
        return cards.filter { validate(it).isValid }
    }

    fun passRate(cards: List<GeneratedFlashcard>): Float {
        if (cards.isEmpty()) return 0f
        val valid = cards.count { validate(it).isValid }
        return valid.toFloat() / cards.size
    }

    private fun calculateScore(card: GeneratedFlashcard, issues: List<String>): Float {
        var score = 0.6f  // Neutral base

        // Hard deduction per issue
        score -= issues.size * 0.25f

        // Bonus for answer being a reasonable length
        if (card.back.length in OPTIMAL_ANSWER_MIN_LENGTH..OPTIMAL_ANSWER_MAX_LENGTH) {
            score += 0.2f
        }

        // Bonus if answer has multiple words (not just a single word response)
        val wordCount = card.back.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (wordCount >= 3) score += 0.2f

        return score.coerceIn(0f, 1f)
    }
}

data class FlashcardQuality(
    val isValid: Boolean,
    val score: Float,
    val issues: List<String>
)