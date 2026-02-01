package com.dibe.eduhive.data.source.ai

import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.*
import com.runanywhere.sdk.public.extensions.LLM.LLMGenerationOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AI Data Source using Run Anywhere SDK for offline LLM inference.
 * Handles:
 * - Concept extraction from materials
 * - Flashcard generation from concepts
 * - Quiz question generation from concepts
 */
class AIDataSource(
    private val modelId: String
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Extract concepts from study material text.
     * Returns a list of concept names and descriptions.
     */
    suspend fun extractConcepts(
        materialText: String,
        hiveContext: String = ""
    ): List<ExtractedConcept> {
        val prompt = buildConceptExtractionPrompt(materialText, hiveContext)

        val result = RunAnywhere.generate(
            prompt = prompt,
            options = LLMGenerationOptions(
                maxTokens = 1000,
                temperature = 0.3f  // Lower temperature for more focused extraction
            )
        )

        return parseConceptsFromResponse(result.text)
    }

    /**
     * Generate flashcards for a specific concept.
     * Returns multiple flashcard pairs (front/back).
     */
    suspend fun generateFlashcards(
        conceptName: String,
        conceptDescription: String?,
        count: Int = 5
    ): List<GeneratedFlashcard> {
        val prompt = buildFlashcardPrompt(conceptName, conceptDescription, count)

        val result = RunAnywhere.generate(
            prompt = prompt,
            options = LLMGenerationOptions(
                maxTokens = 800,
                temperature = 0.5f  // Moderate creativity
            )
        )

        return parseFlashcardsFromResponse(result.text)
    }

    /**
     * Generate quiz questions for a concept.
     * Returns MCQ, True/False, and Short Answer questions.
     */
    suspend fun generateQuizQuestions(
        conceptName: String,
        conceptDescription: String?,
        count: Int = 5
    ): List<GeneratedQuizQuestion> {
        val prompt = buildQuizPrompt(conceptName, conceptDescription, count)

        val result = RunAnywhere.generate(
            prompt = prompt,
            options = LLMGenerationOptions(
                maxTokens = 1000,
                temperature = 0.4f
            )
        )

        return parseQuizQuestionsFromResponse(result.text)
    }

    /**
     * Stream concept extraction with progress.
     */
    fun extractConceptsStream(
        materialText: String,
        hiveContext: String = ""
    ): Flow<String> = flow {
        val prompt = buildConceptExtractionPrompt(materialText, hiveContext)

        RunAnywhere.generateStream(prompt).collect { token ->
            emit(token)
        }
    }

    // ========== PROMPT BUILDERS ==========

    private fun buildConceptExtractionPrompt(text: String, context: String): String {
        return """
You are an expert study assistant. Extract key concepts from the following study material.

${if (context.isNotEmpty()) "Context: This material is from a course on $context.\n" else ""}

For each concept, provide:
1. A clear name (2-5 words)
2. A brief description (1-2 sentences)

Format your response as JSON array:
[
  {"name": "Concept Name", "description": "Brief description"},
  ...
]

Study Material:
$text

Respond ONLY with valid JSON array, no additional text.
        """.trimIndent()
    }

    private fun buildFlashcardPrompt(
        conceptName: String,
        description: String?,
        count: Int
    ): String {
        return """
Create $count flashcards to help students learn about: $conceptName

${description?.let { "Description: $it\n" } ?: ""}

Each flashcard should:
- Have a clear question on the front
- Have a concise, accurate answer on the back
- Test different aspects of the concept
- Be suitable for spaced repetition study

Format as JSON array:
[
  {"front": "Question text", "back": "Answer text"},
  ...
]

Respond ONLY with valid JSON array, no additional text.
        """.trimIndent()
    }

    private fun buildQuizPrompt(
        conceptName: String,
        description: String?,
        count: Int
    ): String {
        return """
Create $count quiz questions about: $conceptName

${description?.let { "Description: $it\n" } ?: ""}

Include a mix of:
- Multiple choice questions (MCQ) with 4 options
- True/False questions
- Short answer questions

Format as JSON array:
[
  {
    "type": "MCQ",
    "question": "Question text",
    "correctAnswer": "Correct answer",
    "options": ["Option 1", "Option 2", "Option 3", "Option 4"]
  },
  {
    "type": "TRUE_FALSE",
    "question": "Statement",
    "correctAnswer": "true" or "false"
  },
  {
    "type": "SHORT_ANSWER",
    "question": "Question",
    "correctAnswer": "Expected answer"
  }
]

Respond ONLY with valid JSON array, no additional text.
        """.trimIndent()
    }

    // ========== RESPONSE PARSERS ==========

    private fun parseConceptsFromResponse(response: String): List<ExtractedConcept> {
        return try {
            val cleanResponse = extractJsonArray(response)
            json.decodeFromString<List<ExtractedConcept>>(cleanResponse)
        } catch (e: Exception) {
            // Fallback: manual parsing if JSON fails
            parseConceptsManually(response)
        }
    }

    private fun parseFlashcardsFromResponse(response: String): List<GeneratedFlashcard> {
        return try {
            val cleanResponse = extractJsonArray(response)
            json.decodeFromString<List<GeneratedFlashcard>>(cleanResponse)
        } catch (e: Exception) {
            parseFlashcardsManually(response)
        }
    }

    private fun parseQuizQuestionsFromResponse(response: String): List<GeneratedQuizQuestion> {
        return try {
            val cleanResponse = extractJsonArray(response)
            json.decodeFromString<List<GeneratedQuizQuestion>>(cleanResponse)
        } catch (e: Exception) {
            parseQuizQuestionsManually(response)
        }
    }

    // ========== HELPERS ==========

    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')

        return if (start != -1 && end != -1 && end > start) {
            text.substring(start, end + 1)
        } else {
            text
        }
    }

    private fun parseConceptsManually(text: String): List<ExtractedConcept> {
        // Fallback parser if JSON fails
        val concepts = mutableListOf<ExtractedConcept>()

        // Look for patterns like "1. Concept Name: Description"
        val regex = Regex("""(\d+)\.\s*([^:]+):\s*(.+?)(?=\d+\.|$)""", RegexOption.DOT_MATCHES_ALL)

        regex.findAll(text).forEach { match ->
            val name = match.groupValues[2].trim()
            val description = match.groupValues[3].trim()
            concepts.add(ExtractedConcept(name, description))
        }

        return concepts
    }

    private fun parseFlashcardsManually(text: String): List<GeneratedFlashcard> {
        val flashcards = mutableListOf<GeneratedFlashcard>()

        // Look for Q:/A: patterns or Front:/Back: patterns
        val lines = text.lines()
        var currentFront = ""

        lines.forEach { line ->
            when {
                line.startsWith("Q:") || line.startsWith("Front:") -> {
                    currentFront = line.substringAfter(":").trim()
                }
                line.startsWith("A:") || line.startsWith("Back:") -> {
                    val back = line.substringAfter(":").trim()
                    if (currentFront.isNotEmpty()) {
                        flashcards.add(GeneratedFlashcard(currentFront, back))
                        currentFront = ""
                    }
                }
            }
        }

        return flashcards
    }

    private fun parseQuizQuestionsManually(text: String): List<GeneratedQuizQuestion> {
        // Simplified fallback - just extract questions
        return emptyList()
    }
}

// ========== DATA MODELS ==========

@Serializable
data class ExtractedConcept(
    val name: String,
    val description: String
)

@Serializable
data class GeneratedFlashcard(
    val front: String,
    val back: String
)

@Serializable
data class GeneratedQuizQuestion(
    val type: String,  // "MCQ", "TRUE_FALSE", "SHORT_ANSWER"
    val question: String,
    val correctAnswer: String,
    val options: List<String>? = null
)