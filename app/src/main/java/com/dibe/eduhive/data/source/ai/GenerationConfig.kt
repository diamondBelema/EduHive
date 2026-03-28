package com.dibe.eduhive.data.source.ai

/**
 * Configuration for LLM generation parameters.
 *
 * maxTokens is the TOTAL context window (input + output combined).
 * It is set on the LlmInference engine at load time — not per-call.
 * 
 * Stability Note: Many mobile-optimized models (like Qwen/SmolLM) are compiled
 * with a fixed context window (often 1280 tokens). Setting maxTokens higher
 * than the model's compiled capacity will cause a TFLite execution error
 * and crash the application.
 *
 * Budget rule: reserve at least 40% of maxTokens for the response.
 */
data class GenerationConfig(
    /** Total context window — input + output combined. */
    val maxTokens: Int = 1280,
    val temperature: Float = 0.3f,
    val topK: Int = 20,
    val randomSeed: Int = 42,
    val retryAttempts: Int = 2,
    val validateBeforeStore: Boolean = true
) {
    companion object {

        /**
         * Concept extraction.
         *
         * Window 1280: ~400 tok input, ~800 tok output headroom.
         * Temperature low (0.2) — format compliance matters more than creativity.
         */
        val CONCEPT_EXTRACTION = GenerationConfig(
            maxTokens = 1280,
            temperature = 0.2f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = false
        )

        /**
         * Flashcard generation.
         *
         * Temperature 0.4 gives enough variety to avoid identical cards.
         */
        val FLASHCARD = GenerationConfig(
            maxTokens = 1280,
            temperature = 0.4f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = true
        )

        /**
         * Quiz generation.
         *
         * Temperature 0.3 — quiz questions need the model to stay close
         * to the MCQ/TRUE_FALSE format.
         */
        val QUIZ = GenerationConfig(
            maxTokens = 1280,
            temperature = 0.3f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = false
        )

        /**
         * Fast-track flashcard generation for small/simple files (< 5 pages).
         *
         * Single attempt, no validation refinement pass — small models produce
         * acceptable output on the first attempt for short documents, and the
         * refinement pass rarely improves quality enough to justify the extra
         * generation cost (~2–3 seconds per concept).
         */
        val FAST_TRACK_FLASHCARD = GenerationConfig(
            maxTokens = 1280,
            temperature = 0.4f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 1,
            validateBeforeStore = false
        )
    }
}