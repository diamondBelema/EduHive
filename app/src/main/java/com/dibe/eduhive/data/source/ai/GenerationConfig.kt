package com.dibe.eduhive.data.source.ai

/**
 * Configuration for LLM generation parameters.
 *
 * maxTokens is the TOTAL context window (input + output combined).
 * It is set on the LlmInference engine at load time — not per-call.
 * This means the config used when loadModel() is called decides how much
 * room the model has for both reading the prompt and writing the response.
 *
 * Budget rule: reserve at least 40% of maxTokens for the response.
 * Concept extraction: 2048 window, ~600 tok input, ~800 tok output headroom.
 * Flashcards:        1536 window, ~300 tok input, ~900 tok output headroom.
 * Quiz:              1536 window, ~350 tok input, ~850 tok output headroom.
 *
 * Temperature guide for small models:
 *   0.1–0.3  → very rigid format compliance, low creativity (use for extraction)
 *   0.4–0.5  → balanced (use for flashcards / quiz)
 *   0.6+     → drifts format on models < 500M, avoid for structured output
 *
 * TopK guide:
 *   10–20  → focused, lower variety (good for structured tasks)
 *   30–40  → more variety (only useful on models >= 500M)
 */
data class GenerationConfig(
    /** Total context window — input + output combined. */
    val maxTokens: Int = 1536,
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
         * Larger window (2048) because we need room to read the page text AND
         * produce 3 concept pairs. Temperature low (0.2) — format compliance
         * matters more than creativity here. TopK 20 keeps the model on-format.
         */
        val CONCEPT_EXTRACTION = GenerationConfig(
            maxTokens = 2048,
            temperature = 0.2f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = false
        )

        /**
         * Flashcard generation.
         *
         * Slightly larger window than quiz because we ask for 3 cards per call
         * and each FRONT+BACK pair takes ~40–60 tokens. Temperature 0.4 gives
         * enough variety to avoid identical cards across concepts while keeping
         * the format stable. We never retry more than twice — if the model
         * can't produce valid cards on attempt 2, accept what we have.
         */
        val FLASHCARD = GenerationConfig(
            maxTokens = 1536,
            temperature = 0.4f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = true
        )

        /**
         * Quiz generation.
         *
         * Temperature 0.3 — quiz questions need the model to stay very close
         * to the MCQ/TRUE_FALSE format. Drift here produces unparseable output.
         */
        val QUIZ = GenerationConfig(
            maxTokens = 1536,
            temperature = 0.3f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = false
        )
    }
}