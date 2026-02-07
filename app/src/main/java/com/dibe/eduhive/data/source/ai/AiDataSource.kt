package com.dibe.eduhive.data.source.ai

import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.LLM.LLMGenerationOptions
import com.runanywhere.sdk.public.extensions.chat
import com.runanywhere.sdk.public.extensions.generate
import com.runanywhere.sdk.public.extensions.generateStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Data Source using Run Anywhere SDK.
 *
 * Handles:
 * - Concept extraction from text
 * - Flashcard generation
 * - Quiz generation
 */
@Singleton
class AIDataSource @Inject constructor(
    private val modelManager: AIModelManager
) {

    /**
     * Extract concepts from material text.
     * Uses streaming for progress tracking.
     */
    suspend fun extractConcepts(
        text: String,
        hiveContext: String = ""
    ): Result<List<ExtractedConcept>> {
        return try {
            // Check if model is loaded
            if (!modelManager.isModelLoaded()) {
                val modelId = modelManager.getActiveModel()
                    ?: return Result.failure(IllegalStateException("No model available"))
                modelManager.loadModel(modelId)
            }

            val prompt = buildConceptExtractionPrompt(text, hiveContext)

            val result = RunAnywhere.generate(
                prompt = prompt,
                options = LLMGenerationOptions(
                    maxTokens = 500,
                    temperature = 0.3f,  // Lower for more precise extraction
                    systemPrompt = """
                        You are an educational content analyzer.
                        Extract only the most important concepts from the text.
                        Be precise and concise.
                    """.trimIndent()
                )
            )

            val concepts = parseConceptsFromResponse(result.text)
            Result.success(concepts)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract concepts with streaming progress.
     */
    fun extractConceptsStream(
        text: String,
        hiveContext: String = ""
    ): Flow<String> {
        val prompt = buildConceptExtractionPrompt(text, hiveContext)

        return RunAnywhere.generateStream(prompt)
    }

    /**
     * Generate flashcards for a concept.
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

            val result = RunAnywhere.generate(
                prompt = prompt,
                options = LLMGenerationOptions(
                    maxTokens = 800,
                    temperature = 0.5f,
                    systemPrompt = """
                        You are a flashcard creator.
                        Create clear, educational flashcards.
                        Questions should test understanding, not just memory.
                    """.trimIndent()
                )
            )

            val flashcards = parseFlashcardsFromResponse(result.text)
            Result.success(flashcards)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate quiz questions for a concept.
     */
    suspend fun generateQuiz(
        conceptName: String,
        conceptDescription: String,
        questionCount: Int = 5
    ): Result<List<GeneratedQuizQuestion>> {
        return try {
            if (!modelManager.isModelLoaded()) {
                val modelId = modelManager.getActiveModel()
                    ?: return Result.failure(IllegalStateException("No model available"))
                modelManager.loadModel(modelId)
            }

            val prompt = buildQuizPrompt(conceptName, conceptDescription, questionCount)

            val result = RunAnywhere.generate(
                prompt = prompt,
                options = LLMGenerationOptions(
                    maxTokens = 1000,
                    temperature = 0.4f,
                    systemPrompt = """
                        You are a quiz creator.
                        Create challenging but fair quiz questions.
                        Include MCQ, True/False, and short answer questions.
                    """.trimIndent()
                )
            )

            val questions = parseQuizFromResponse(result.text)
            Result.success(questions)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Simple chat (for testing or simple queries).
     */
    suspend fun chat(message: String): Result<String> {
        return try {
            if (!modelManager.isModelLoaded()) {
                val modelId = modelManager.getActiveModel()
                    ?: return Result.failure(IllegalStateException("No model available"))
                modelManager.loadModel(modelId)
            }

            val response = RunAnywhere.chat(message)
            Result.success(response)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private fun buildConceptExtractionPrompt(text: String, context: String): String {
        return """
            Extract 3-10 key concepts from this educational material.
            ${if (context.isNotEmpty()) "Context: $context\n" else ""}
            
            Material:
            $text
            
            For each concept, provide:
            1. Name (2-5 words)
            2. Description (1-2 sentences)
            
            Format your response EXACTLY like this:
            
            CONCEPT: Mitochondria Function
            DESCRIPTION: Organelles that produce ATP through cellular respiration, known as the powerhouse of the cell.
            
            CONCEPT: DNA Structure
            DESCRIPTION: Double helix structure containing genetic information made of nucleotides.
            
            Do not include any other text.
        """.trimIndent()
    }

    private fun buildFlashcardPrompt(name: String, description: String, count: Int): String {
        return """
            Create $count flashcards about: $name
            Description: $description
            
            Each flashcard should have:
            - Front: A clear question
            - Back: A concise answer (1-3 sentences)
            
            Format EXACTLY like this:
            
            FLASHCARD 1
            FRONT: What is the primary function of mitochondria?
            BACK: To produce ATP through cellular respiration.
            
            FLASHCARD 2
            FRONT: Why are mitochondria called the powerhouse of the cell?
            BACK: Because they generate most of the cell's energy in the form of ATP.
            
            Create all $count flashcards now.
        """.trimIndent()
    }

    private fun buildQuizPrompt(name: String, description: String, count: Int): String {
        return """
            Create $count quiz questions about: $name
            Description: $description
            
            Format EXACTLY like this:
            
            QUESTION 1
            TYPE: MCQ
            TEXT: What molecule does mitochondria produce?
            OPTION A: ATP
            OPTION B: DNA
            OPTION C: RNA
            OPTION D: Glucose
            CORRECT: A
            
            QUESTION 2
            TYPE: TRUE_FALSE
            TEXT: Mitochondria contain their own DNA.
            CORRECT: TRUE
            
            Create all $count questions now.
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

    private fun parseQuizFromResponse(response: String): List<GeneratedQuizQuestion> {
        // Simplified parsing - can be improved
        return emptyList()  // TODO: Implement quiz parsing
    }
}

// ========== DATA CLASSES ==========

data class ExtractedConcept(
    val name: String,
    val description: String
)

data class GeneratedFlashcard(
    val front: String,
    val back: String
)

data class GeneratedQuizQuestion(
    val type: String,
    val text: String,
    val options: List<String>?,
    val correctAnswer: String
)