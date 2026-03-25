package com.dibe.eduhive.data.source.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates the quality of AI-generated flashcards before they are stored.
 *
 * Scores each flashcard on a 0–1 scale and determines whether it passes
 * the minimum quality threshold.
 */
@Singleton
class FlashcardValidator @Inject constructor() {

    companion object {
        /** Minimum quality score (0–1) for a flashcard to be accepted. */
        const val MIN_QUALITY_SCORE = 0.7f

        /** Minimum character length for the question (front). */
        private const val MIN_FRONT_LENGTH = 10

        /** Minimum character length for the answer (back). */
        private const val MIN_BACK_LENGTH = 5

        /** Optimal answer length range for quality scoring (characters). */
        private const val OPTIMAL_ANSWER_MIN_LENGTH = 10
        private const val OPTIMAL_ANSWER_MAX_LENGTH = 150

        /** Optimal word count range for quality scoring. */
        private const val OPTIMAL_WORD_COUNT_MIN = 3
        private const val OPTIMAL_WORD_COUNT_MAX = 30

        /** Terms that indicate a vague or placeholder question/answer. */
        private val VAGUE_TERMS = listOf(
            "something", "undefined", "null", "n/a", "placeholder",
            "example here", "fill in"
        )
    }

    /**
     * Validates a single flashcard and returns a quality report.
     */
    fun validate(card: GeneratedFlashcard): FlashcardQuality {
        val issues = mutableListOf<String>()

        if (card.front.length < MIN_FRONT_LENGTH) {
            issues.add("Question too short (${card.front.length} chars, min $MIN_FRONT_LENGTH)")
        }

        if (card.back.length < MIN_BACK_LENGTH) {
            issues.add("Answer too short (${card.back.length} chars, min $MIN_BACK_LENGTH)")
        }

        if (!card.front.trim().endsWith("?")) {
            issues.add("Question does not end with '?'")
        }

        for (term in VAGUE_TERMS) {
            if (card.front.contains(term, ignoreCase = true)) {
                issues.add("Question contains vague term: '$term'")
            }
            if (card.back.contains(term, ignoreCase = true)) {
                issues.add("Answer contains vague term: '$term'")
            }
        }

        val score = calculateScore(card, issues)
        return FlashcardQuality(
            isValid = issues.isEmpty() && score >= MIN_QUALITY_SCORE,
            score = score,
            issues = issues
        )
    }

    /**
     * Filters a list of flashcards to only valid ones.
     */
    fun filterValid(cards: List<GeneratedFlashcard>): List<GeneratedFlashcard> {
        return cards.filter { validate(it).isValid }
    }

    /**
     * Returns the pass rate of a list of flashcards (0.0–1.0).
     */
    fun passRate(cards: List<GeneratedFlashcard>): Float {
        if (cards.isEmpty()) return 0f
        val valid = cards.count { validate(it).isValid }
        return valid.toFloat() / cards.size
    }

    private fun calculateScore(card: GeneratedFlashcard, issues: List<String>): Float {
        // Start from a neutral base; bonuses can push above 0.7 (passing threshold)
        var score = 0.7f

        // Deduct for each quality issue
        score -= issues.size * 0.2f

        // Reward longer, more informative answers (up to a point)
        if (card.back.length in OPTIMAL_ANSWER_MIN_LENGTH..OPTIMAL_ANSWER_MAX_LENGTH) {
            score += 0.15f
        }

        // Reward diverse vocabulary (rough proxy: word count)
        val wordCount = card.back.split(Regex("\\s+")).size
        if (wordCount in OPTIMAL_WORD_COUNT_MIN..OPTIMAL_WORD_COUNT_MAX) {
            score += 0.15f
        }

        return score.coerceIn(0f, 1f)
    }
}

/**
 * Quality report for a single flashcard.
 */
data class FlashcardQuality(
    val isValid: Boolean,
    val score: Float,
    val issues: List<String>
)
