package com.dibe.eduhive.data.source.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Data Source for MediaPipe.
 *
 * ONLY CHANGE: modelManager.generate() instead of RunAnywhere.generate()
 * Everything else stays the same!
 */
@Singleton
class AIDataSource @Inject constructor(
    private val modelManager: AIModelManager
) {

    /**
     * Extract concepts from text.
     * NO CHANGES to function signature!
     */
    suspend fun extractConcepts(
        text: String,
        hiveContext: String = ""
    ): Result<List<ExtractedConcept>> {
        return try {
            // Ensure model is loaded
            if (!modelManager.isModelLoaded()) {
                val modelId = modelManager.getActiveModel()
                    ?: return Result.failure(IllegalStateException("No model available"))
                modelManager.loadModel(modelId)
            }

            val prompt = buildConceptExtractionPrompt(text, hiveContext)

            // ONLY CHANGE: Use modelManager instead of RunAnywhere
            val response = modelManager.generate(
                prompt = prompt,
                temperature = 0.3f,
                maxTokens = 500
            ).getOrThrow()

            val concepts = parseConceptsFromResponse(response)
            Result.success(concepts)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate flashcards.
     * NO CHANGES to function signature!
     */
    suspend fun generateFlashcards(
        conceptName: String,
        conceptDescription: String,
        count: Int = 5
    ): Result<List<GeneratedFlashcard>> {
        return try {
            if (!modelManager.isModelLoaded()) {
                val modelId = modelManager.getActiveModel()
                    ?: return Result.failure(IllegalStateException("No model available"))
                modelManager.loadModel(modelId)
            }

            val prompt = buildFlashcardPrompt(conceptName, conceptDescription, count)

            // ONLY CHANGE: Use modelManager
            val response = modelManager.generate(
                prompt = prompt,
                temperature = 0.5f,
                maxTokens = 800
            ).getOrThrow()

            val flashcards = parseFlashcardsFromResponse(response)
            Result.success(flashcards)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Simple chat.
     */
    suspend fun chat(message: String): Result<String> {
        return try {
            if (!modelManager.isModelLoaded()) {
                val modelId = modelManager.getActiveModel()
                    ?: return Result.failure(IllegalStateException("No model available"))
                modelManager.loadModel(modelId)
            }

            modelManager.generate(message)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== UNCHANGED METHODS ==========
    // These stay exactly the same!

    private fun buildConceptExtractionPrompt(text: String, context: String): String {
        return """
            Extract 3-10 key concepts from this educational material.
            ${if (context.isNotEmpty()) "Context: $context\n" else ""}
            
            Material:
            $text
            
            For each concept, provide:
            1. Name (2-5 words)
            2. Description (1-2 sentences)
            
            Format EXACTLY like this:
            
            CONCEPT: Mitochondria Function
            DESCRIPTION: Organelles that produce ATP through cellular respiration.
            
            CONCEPT: DNA Structure
            DESCRIPTION: Double helix structure containing genetic information.
        """.trimIndent()
    }

    private fun buildFlashcardPrompt(name: String, description: String, count: Int): String {
        return """
            Create $count flashcards about: $name
            Description: $description
            
            Format EXACTLY like this:
            
            FLASHCARD 1
            FRONT: What is the primary function of mitochondria?
            BACK: To produce ATP through cellular respiration.
            
            FLASHCARD 2
            FRONT: Why are mitochondria called the powerhouse?
            BACK: Because they generate energy for the cell.
            
            Create all $count flashcards.
        """.trimIndent()
    }

    private fun parseConceptsFromResponse(response: String): List<ExtractedConcept> {
        val concepts = mutableListOf<ExtractedConcept>()
        val lines = response.lines()

        var currentName: String? = null

        for (line in lines) {
            when {
                line.startsWith("CONCEPT:", ignoreCase = true) -> {
                    currentName = line.substringAfter(":").trim()
                }
                line.startsWith("DESCRIPTION:", ignoreCase = true) && currentName != null -> {
                    val description = line.substringAfter(":").trim()
                    concepts.add(ExtractedConcept(currentName, description))
                    currentName = null
                }
            }
        }

        return concepts
    }

    private fun parseFlashcardsFromResponse(response: String): List<GeneratedFlashcard> {
        val flashcards = mutableListOf<GeneratedFlashcard>()
        val lines = response.lines()

        var currentFront: String? = null

        for (line in lines) {
            when {
                line.startsWith("FRONT:", ignoreCase = true) -> {
                    currentFront = line.substringAfter(":").trim()
                }
                line.startsWith("BACK:", ignoreCase = true) && currentFront != null -> {
                    val back = line.substringAfter(":").trim()
                    flashcards.add(GeneratedFlashcard(currentFront, back))
                    currentFront = null
                }
            }
        }

        return flashcards
    }
}

// UNCHANGED data classes
data class ExtractedConcept(
    val name: String,
    val description: String
)

data class GeneratedFlashcard(
    val front: String,
    val back: String
)