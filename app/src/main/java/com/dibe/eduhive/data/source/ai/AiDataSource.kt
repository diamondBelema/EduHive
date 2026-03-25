package com.dibe.eduhive.data.source.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Data Source for MediaPipe.
 *
 * Handles the logic of building prompts and parsing responses.
 * Optimized for large documents by supporting page-by-page processing.
 */
@Singleton
class AIDataSource @Inject constructor(
    private val modelManager: AIModelManager,
    private val modelPreferences: ModelPreferences
) {

    /**
     * Extract concepts from a list of pages with streaming progress.
     * This is the preferred method for PDFs.
     */
    fun extractConceptsFromPagesStreaming(
        pages: List<String>,
        hiveContext: String = ""
    ): Flow<ConceptExtractionState> = channelFlow {
        send(ConceptExtractionState.Loading)

        if (!ensureModelLoaded()) {
            send(ConceptExtractionState.Error("No model available"))
            return@channelFlow
        }

        val allExtractedConcepts = mutableListOf<ExtractedConcept>()
        val totalPages = pages.size

        pages.forEachIndexed { index, page ->
            send(ConceptExtractionState.Progress(((index.toFloat() / totalPages) * 100).toInt()))

            val prompt = buildConceptExtractionPrompt(page, hiveContext)
            val result = modelManager.generate(prompt)

            result.onSuccess { response ->
                val pageConcepts = parseConceptsRobust(response)
                allExtractedConcepts.addAll(pageConcepts)
            }.onFailure {
                // reinitialize before next page if native engine failed
                modelManager.unloadModel()
                delay(300)
                ensureModelLoaded()
            }
        }

        // Deduplicate and finish
        val uniqueConcepts = deduplicateConcepts(allExtractedConcepts)
        if (uniqueConcepts.isEmpty()) {
            send(ConceptExtractionState.Error("No concepts could be identified from the material. Try a different section or check file quality."))
        } else {
            send(ConceptExtractionState.Success(uniqueConcepts))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Legacy streaming for a single block of text.
     */
    fun extractConceptsStreaming(
        text: String,
        hiveContext: String = ""
    ): Flow<ConceptExtractionState> = extractConceptsFromPagesStreaming(listOf(text), hiveContext)

    /**
     * Extract concepts (blocking version).
     */
    suspend fun extractConcepts(
        text: String,
        hiveContext: String = ""
    ): Result<List<ExtractedConcept>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded()) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }

            val prompt = buildConceptExtractionPrompt(text, hiveContext)
            val response = modelManager.generate(prompt).getOrThrow()
            Result.success(parseConceptsRobust(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract concepts from multiple pages (blocking version).
     */
    suspend fun extractConceptsFromDocument(
        pages: List<String>,
        hiveContext: String = ""
    ): Result<List<ExtractedConcept>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded()) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }

            val pageResults = modelManager.processDocumentBatched(
                pages = pages,
                operation = { page -> buildConceptExtractionPrompt(page, hiveContext) }
            )

            val allConcepts = pageResults.flatMap {
                parseConceptsRobust(it)
            }
            Result.success(deduplicateConcepts(allConcepts))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate flashcards with streaming.
     */
    fun generateFlashcardsStreaming(
        conceptName: String,
        conceptDescription: String,
        count: Int = 5
    ): Flow<FlashcardGenerationState> = channelFlow {
        send(FlashcardGenerationState.Loading)

        if (!ensureModelLoaded()) {
            send(FlashcardGenerationState.Error("No model available"))
            return@channelFlow
        }

        val prompt = buildFlashcardPrompt(conceptName, conceptDescription, count)
        val result = modelManager.generate(prompt)

        result.onSuccess { response ->
            send(FlashcardGenerationState.Success(parseFlashcardsRobust(response)))
        }.onFailure { e ->
            send(FlashcardGenerationState.Error(e.message ?: "Failed to generate flashcards"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generate flashcards (blocking).
     */
    suspend fun generateFlashcards(
        conceptName: String,
        conceptDescription: String,
        count: Int = 5
    ): Result<List<GeneratedFlashcard>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded()) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            val prompt = buildFlashcardPrompt(conceptName, conceptDescription, count)
            val response = modelManager.generate(prompt).getOrThrow()
            Result.success(parseFlashcardsRobust(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate quiz questions with streaming progress.
     */
    fun generateQuizStreaming(
        conceptName: String,
        conceptDescription: String,
        questionCount: Int = 5
    ): Flow<QuizGenerationState> = channelFlow {
        send(QuizGenerationState.Loading)

        if (!ensureModelLoaded()) {
            send(QuizGenerationState.Error("No model available"))
            return@channelFlow
        }

        val prompt = buildQuizPrompt(conceptName, conceptDescription, questionCount)
        
        modelManager.generateStreaming(prompt).collect { result ->
            when (result) {
                is GenerationResult.Progress -> {
                    send(QuizGenerationState.Generating(
                        percent = (result.completedChunks * 100) / result.totalChunks
                    ))
                }
                is GenerationResult.Success -> {
                    val questions = parseQuizFromResponse(result.text)
                    send(QuizGenerationState.Success(questions))
                }
                is GenerationResult.Error -> {
                    send(QuizGenerationState.Error(result.exception.message ?: "Quiz generation failed"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generate quiz (blocking version).
     */
    suspend fun generateQuiz(
        conceptName: String,
        conceptDescription: String,
        questionCount: Int = 5
    ): Result<List<GeneratedQuizQuestion>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded()) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            val prompt = buildQuizPrompt(conceptName, conceptDescription, questionCount)
            val response = modelManager.generate(prompt).getOrThrow()
            Result.success(parseQuizFromResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureModelLoaded(): Boolean {
        if (modelManager.isModelLoaded()) return true
        val modelId = modelPreferences.getActiveModel() ?: return false
        return modelManager.loadModel(modelId).isSuccess
    }

    private fun deduplicateConcepts(concepts: List<ExtractedConcept>): List<ExtractedConcept> {
        return concepts.distinctBy { it.name.lowercase().trim() }
    }

    private fun buildConceptExtractionPrompt(text: String, context: String): String {
        val templateOverhead = 200
        val maxTextChars = AIModelManager.MAX_INPUT_CHARS - templateOverhead

        val safeText = if (text.length > maxTextChars) {
            text.take(maxTextChars).substringBeforeLast(' ')
        } else text

        return """
        Extract important educational concepts from the following text.
        ${if (context.isNotEmpty()) "Goal: $context\n" else ""}
        Text:
        $safeText
        
        Output format:
        CONCEPT: [Simple Name]
        DESCRIPTION: [Clear Explanation]
        
        Provide 3 to 8 concepts.
    """.trimIndent()
    }

    private fun buildFlashcardPrompt(name: String, description: String, count: Int): String {
        return """
            Create $count flashcards for: $name
            Information: $description
            
            Format:
            FRONT: [Short Question]
            BACK: [Concise Answer]
        """.trimIndent()
    }

    private fun buildQuizPrompt(name: String, description: String, count: Int): String {
        return """
            Create $count quiz questions about: $name
            Description: $description
            
            Types: MCQ or TRUE_FALSE.
            
            Format:
            QUESTION 1
            TYPE: MCQ
            TEXT: [Question]
            OPTION A: [Option]
            OPTION B: [Option]
            OPTION C: [Option]
            OPTION D: [Option]
            CORRECT: A
        """.trimIndent()
    }

    private fun parseConceptsRobust(response: String): List<ExtractedConcept> {
        val concepts = mutableListOf<ExtractedConcept>()
        val lines = response.lines()
        var currentName: String? = null

        for (line in lines) {
            val cleanLine = line.replace("*", "").trim()
            if (cleanLine.startsWith("CONCEPT:", ignoreCase = true)) {
                currentName = cleanLine.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
            } else if (cleanLine.startsWith("DESCRIPTION:", ignoreCase = true) && currentName != null) {
                val description = cleanLine.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
                if (currentName.isNotBlank() && description.isNotEmpty()) {
                    concepts.add(ExtractedConcept(currentName, description))
                }
                currentName = null
            }
        }
        return concepts
    }

    private fun parseFlashcardsRobust(response: String): List<GeneratedFlashcard> {
        val flashcards = mutableListOf<GeneratedFlashcard>()
        val lines = response.lines()
        var currentFront: String? = null

        for (line in lines) {
            val cleanLine = line.replace("*", "").trim()
            if (cleanLine.startsWith("FRONT:", ignoreCase = true)) {
                currentFront = cleanLine.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
            } else if (cleanLine.startsWith("BACK:", ignoreCase = true) && currentFront != null) {
                val back = cleanLine.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
                if (currentFront.isNotBlank() && back.isNotEmpty()) {
                    flashcards.add(GeneratedFlashcard(currentFront, back))
                }
                currentFront = null
            }
        }
        return flashcards
    }

    private fun parseQuizFromResponse(response: String): List<GeneratedQuizQuestion> {
        val questions = mutableListOf<GeneratedQuizQuestion>()
        val blocks = response.split(Regex("QUESTION\\s*\\d+", RegexOption.IGNORE_CASE))

        for (block in blocks) {
            if (block.isBlank()) continue

            val lines = block.trim().lines()
            var type = "MCQ"
            var text = ""
            val options = mutableListOf<String>()
            var correct = ""

            for (line in lines) {
                val trimmed = line.replace("*", "").trim()
                when {
                    trimmed.startsWith("TYPE:", ignoreCase = true) -> type = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("TEXT:", ignoreCase = true) -> text = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("OPTION", ignoreCase = true) -> {
                        val optionText = trimmed.substringAfter(":", "").trim()
                        if (optionText.isNotEmpty()) options.add(optionText)
                    }
                    trimmed.startsWith("CORRECT:", ignoreCase = true) -> correct = trimmed.substringAfter(":").trim()
                }
            }

            if (text.isNotEmpty()) {
                questions.add(GeneratedQuizQuestion(type, text, options.ifEmpty { null }, correct))
            }
        }
        return questions
    }

    suspend fun chat(message: String): Result<String> = modelManager.generate(message)
}

// ========== DATA & STATE CLASSES ==========

data class ExtractedConcept(val name: String, val description: String)
data class GeneratedFlashcard(val front: String, val back: String)
data class GeneratedQuizQuestion(val type: String, val text: String, val options: List<String>?, val correctAnswer: String)

sealed class ConceptExtractionState {
    object Loading : ConceptExtractionState()
    data class Progress(val percent: Int) : ConceptExtractionState()
    data class Success(val concepts: List<ExtractedConcept>) : ConceptExtractionState()
    data class Error(val message: String) : ConceptExtractionState()
}

sealed class FlashcardGenerationState {
    object Loading : FlashcardGenerationState()
    data class Success(val flashcards: List<GeneratedFlashcard>) : FlashcardGenerationState()
    data class Error(val message: String) : FlashcardGenerationState()
}

sealed class QuizGenerationState {
    object Loading : QuizGenerationState()
    data class Generating(val percent: Int) : QuizGenerationState()
    data class Success(val questions: List<GeneratedQuizQuestion>) : QuizGenerationState()
    data class Error(val message: String) : QuizGenerationState()
}
