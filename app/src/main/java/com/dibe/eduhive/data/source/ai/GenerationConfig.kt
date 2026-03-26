package com.dibe.eduhive.data.source.ai

/**
 * Configuration for LLM generation parameters.
 *
 * Temperature: 0.5 for structured tasks (flashcards), 0.8 for creative tasks.
 * TopK: 20 for focused generation, less randomness.
 * RandomSeed: ensures deterministic results (required alongside temperature/topK).
 * MaxTokens: controls maximum output length.
 * RetryAttempts: how many times to retry with prompt mutation before giving up.
 * ValidateBeforeStore: whether to apply quality validation before storing results.
 */
data class GenerationConfig(
    val maxTokens: Int = 1280,
    val temperature: Float = 0.5f,
    val topK: Int = 20,
    val randomSeed: Int = 42,
    val retryAttempts: Int = 3,
    val validateBeforeStore: Boolean = true
) {
    companion object {
        /** Config for flashcard generation: structured, focused, deterministic. */
        val FLASHCARD = GenerationConfig(
            maxTokens = 1280,
            temperature = 0.5f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 3,
            validateBeforeStore = true
        )

        /** Config for concept extraction: slightly more creative to surface diverse concepts. */
        val CONCEPT_EXTRACTION = GenerationConfig(
            maxTokens = 1280,
            temperature = 0.6f,
            topK = 30,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = false
        )

        /** Config for quiz generation: balanced between creativity and accuracy. */
        val QUIZ = GenerationConfig(
            maxTokens = 1280,
            temperature = 0.5f,
            topK = 20,
            randomSeed = 42,
            retryAttempts = 2,
            validateBeforeStore = false
        )
    }
}
