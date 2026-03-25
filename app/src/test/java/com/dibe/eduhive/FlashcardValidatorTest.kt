package com.dibe.eduhive

import com.dibe.eduhive.data.source.ai.FlashcardValidator
import com.dibe.eduhive.data.source.ai.GeneratedFlashcard
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FlashcardValidator.
 *
 * Pure tests — no Android dependencies or mocks needed.
 */
class FlashcardValidatorTest {

    private lateinit var validator: FlashcardValidator

    @Before
    fun setup() {
        validator = FlashcardValidator()
    }

    // ========== VALID FLASHCARD TESTS ==========

    @Test
    fun `valid flashcard passes validation`() {
        val card = GeneratedFlashcard(
            front = "What is photosynthesis?",
            back = "The process by which plants convert light energy into chemical energy."
        )

        val quality = validator.validate(card)

        assertTrue(quality.isValid)
        assertTrue(quality.score >= FlashcardValidator.MIN_QUALITY_SCORE)
        assertTrue(quality.issues.isEmpty())
    }

    // ========== QUESTION FORMAT TESTS ==========

    @Test
    fun `flashcard with question not ending in question mark fails`() {
        val card = GeneratedFlashcard(
            front = "What is photosynthesis",
            back = "The process by which plants convert light energy into chemical energy."
        )

        val quality = validator.validate(card)

        assertFalse(quality.isValid)
        assertTrue(quality.issues.any { it.contains("?") })
    }

    @Test
    fun `flashcard with question mark passes format check`() {
        val card = GeneratedFlashcard(
            front = "How does mitosis work?",
            back = "Cell division producing two identical daughter cells."
        )

        val quality = validator.validate(card)

        assertTrue(quality.issues.none { it.contains("?") })
    }

    // ========== LENGTH TESTS ==========

    @Test
    fun `flashcard with short question fails`() {
        val card = GeneratedFlashcard(
            front = "What?",
            back = "Something about biology."
        )

        val quality = validator.validate(card)

        assertFalse(quality.isValid)
        assertTrue(quality.issues.any { it.contains("too short") })
    }

    @Test
    fun `flashcard with short answer fails`() {
        val card = GeneratedFlashcard(
            front = "What is photosynthesis?",
            back = "It"
        )

        val quality = validator.validate(card)

        assertFalse(quality.isValid)
        assertTrue(quality.issues.any { it.contains("too short") })
    }

    // ========== VAGUE TERM TESTS ==========

    @Test
    fun `flashcard with vague term in question fails`() {
        val card = GeneratedFlashcard(
            front = "What does something do in the cell?",
            back = "It performs metabolic functions in the mitochondria."
        )

        val quality = validator.validate(card)

        assertFalse(quality.isValid)
        assertTrue(quality.issues.any { it.contains("vague term") })
    }

    @Test
    fun `flashcard with undefined in answer fails`() {
        val card = GeneratedFlashcard(
            front = "What is cellular respiration?",
            back = "undefined process in cells"
        )

        val quality = validator.validate(card)

        assertFalse(quality.isValid)
        assertTrue(quality.issues.any { it.contains("vague term") })
    }

    // ========== FILTER TESTS ==========

    @Test
    fun `filterValid keeps only valid flashcards`() {
        val cards = listOf(
            GeneratedFlashcard("What is DNA?", "Deoxyribonucleic acid, the carrier of genetic information."),
            GeneratedFlashcard("What?", "Too short"),
            GeneratedFlashcard("What is RNA?", "Ribonucleic acid involved in protein synthesis.")
        )

        val valid = validator.filterValid(cards)

        assertEquals(2, valid.size)
        assertTrue(valid.all { it.front.length >= 10 })
    }

    @Test
    fun `filterValid returns empty list when all cards invalid`() {
        val cards = listOf(
            GeneratedFlashcard("Q?", "A"),
            GeneratedFlashcard("Short?", "X")
        )

        val valid = validator.filterValid(cards)

        assertTrue(valid.isEmpty())
    }

    // ========== PASS RATE TESTS ==========

    @Test
    fun `passRate returns 0 for empty list`() {
        val rate = validator.passRate(emptyList())

        assertEquals(0f, rate, 0.001f)
    }

    @Test
    fun `passRate returns 1 when all cards valid`() {
        val cards = listOf(
            GeneratedFlashcard("What is photosynthesis?", "Light energy converted to chemical energy by plants."),
            GeneratedFlashcard("What is mitosis?", "Cell division producing two identical daughter cells.")
        )

        val rate = validator.passRate(cards)

        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `passRate returns correct fraction`() {
        val cards = listOf(
            GeneratedFlashcard("What is DNA?", "Deoxyribonucleic acid, the genetic material."),
            GeneratedFlashcard("Q?", "Too short"),
            GeneratedFlashcard("What is RNA?", "Ribonucleic acid, involved in protein synthesis."),
            GeneratedFlashcard("X?", "Y")
        )

        val rate = validator.passRate(cards)

        assertEquals(0.5f, rate, 0.001f)
    }
}
