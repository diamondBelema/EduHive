package com.dibe.eduhive.data.source.ai

import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Data Source for MediaPipe with high-performance implementations.
 *
 * Features:
 * - Streaming responses for real-time UI updates
 * - Batch processing for large documents
 * - Parallel chunk processing
 */
@Singleton
class AIDataSource @Inject constructor(
    private val modelManager: AIModelManager,
    private val modelPreferences: ModelPreferences
) {

    /**
     * 🚀 STREAMING: Extract concepts with real-time progress.
     */
    fun extractConceptsStreaming(
        text: String,
        hiveContext: String = ""
    ): Flow<ConceptExtractionState> = channelFlow {
        send(ConceptExtractionState.Loading)

        if (!ensureModelLoaded()) {
            send(ConceptExtractionState.Error("No model available"))
            return@channelFlow
        }

        val prompt = buildConceptExtractionPrompt(text, hiveContext)

        modelManager.generateStreaming(prompt)
            .collect { result ->
                when (result) {
                    is GenerationResult.Progress -> {
                        send(ConceptExtractionState.Progress(
                            percent = (result.completedChunks * 100) / result.totalChunks
                        ))
                    }
                    is GenerationResult.Success -> {
                        val concepts = parseConceptsFast(result.text)
                        send(ConceptExtractionState.Success(concepts))
                    }
                    is GenerationResult.Error -> {
                        send(ConceptExtractionState.Error(
                            result.exception.message ?: "Extraction failed"
                        ))
                    }
                }
            }
    }.flowOn(Dispatchers.IO)

    /**
     * ⚡ STANDARD: Extract concepts (blocking, for compatibility).
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

            // Collect streaming result into single response
            var finalResult: List<ExtractedConcept>? = null
            var finalError: Throwable? = null

            modelManager.generateStreaming(prompt).collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        finalResult = parseConceptsFast(result.text)
                    }
                    is GenerationResult.Error -> {
                        finalError = result.exception
                    }
                    else -> { /* Ignore progress */ }
                }
            }

            finalResult?.let { Result.success(it) }
                ?: Result.failure(finalError ?: IllegalStateException("No result"))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 📄 BATCH PROCESSING: Extract concepts from multiple pages efficiently.
     * Processes pages in parallel batches and deduplicates results.
     */
    suspend fun extractConceptsFromDocument(
        pages: List<String>,
        hiveContext: String = ""
    ): Result<List<ExtractedConcept>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded()) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }

            // Process pages in parallel batches
            val pageResults = modelManager.processDocumentBatched(
                pages = pages,
                operation = { page -> buildConceptExtractionPrompt(page, hiveContext) },
                batchSize = 3
            )

            // Merge and deduplicate concepts from all pages
            val allConcepts = pageResults.flatMap { response ->
                parseConceptsFast(response)
            }

            // Deduplicate by name (case-insensitive)
            val uniqueConcepts = allConcepts
                .distinctBy { it.name.lowercase().trim() }
                // Merge descriptions for duplicates
                .map { concept ->
                    val similar = allConcepts.filter {
                        it.name.lowercase().trim() == concept.name.lowercase().trim()
                    }
                    if (similar.size > 1) {
                        concept.copy(
                            description = similar.map { it.description }.distinct().joinToString(" ")
                        )
                    } else concept
                }

            Result.success(uniqueConcepts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 🚀 STREAMING: Generate flashcards with progress.
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

        modelManager.generateStreaming(prompt)
            .collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        val flashcards = parseFlashcardsFast(result.text)
                        send(FlashcardGenerationState.Success(flashcards))
                    }
                    is GenerationResult.Error -> {
                        send(FlashcardGenerationState.Error(
                            result.exception.message ?: "Generation failed"
                        ))
                    }
                    else -> { /* Ignore progress for flashcards */ }
                }
            }
    }.flowOn(Dispatchers.IO)

    /**
     * ⚡ STANDARD: Generate flashcards (blocking).
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

            var finalResult: List<GeneratedFlashcard>? = null
            var finalError: Throwable? = null

            modelManager.generateStreaming(prompt).collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        finalResult = parseFlashcardsFast(result.text)
                    }
                    is GenerationResult.Error -> {
                        finalError = result.exception
                    }
                    else -> { }
                }
            }

            finalResult?.let { Result.success(it) }
                ?: Result.failure(finalError ?: IllegalStateException("No result"))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 🚀 STREAMING: Generate quiz questions with progress.
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

        modelManager.generateStreaming(prompt)
            .collect { result ->
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
                        send(QuizGenerationState.Error(
                            result.exception.message ?: "Quiz generation failed"
                        ))
                    }
                }
            }
    }.flowOn(Dispatchers.IO)

    /**
     * ⚡ STANDARD: Generate quiz (blocking).
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

            var finalResult: List<GeneratedQuizQuestion>? = null
            var finalError: Throwable? = null

            modelManager.generateStreaming(prompt).collect { result ->
                when (result) {
                    is GenerationResult.Success -> {
                        finalResult = parseQuizFromResponse(result.text)
                    }
                    is GenerationResult.Error -> {
                        finalError = result.exception
                    }
                    else -> { }
                }
            }

            finalResult?.let { Result.success(it) }
                ?: Result.failure(finalError ?: IllegalStateException("No result"))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Simple chat.
     */
    suspend fun chat(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded()) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            modelManager.generate(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== HELPER METHODS ==========

    private suspend fun ensureModelLoaded(): Boolean {
        if (modelManager.isModelLoaded()) return true

        val modelId = modelPreferences.getActiveModel() ?: return false
        return modelManager.loadModel(modelId).isSuccess
    }

    private fun buildConceptExtractionPrompt(text: String, context: String): String {
        return buildString(512) {
            appendLine("Extract 3-10 key concepts from this educational material.")
            if (context.isNotEmpty()) appendLine("Context: $context")
            appendLine()
            appendLine("Material:")
            appendLine(text.take(15000)) // Safety limit
            appendLine()
            appendLine("For each concept, provide:")
            appendLine("1. Name (2-5 words)")
            appendLine("2. Description (1-2 sentences)")
            appendLine()
            appendLine("Format EXACTLY like this:")
            appendLine()
            appendLine("CONCEPT: Mitochondria Function")
            appendLine("DESCRIPTION: Organelles that produce ATP through cellular respiration.")
            appendLine()
            appendLine("CONCEPT: DNA Structure")
            appendLine("DESCRIPTION: Double helix structure containing genetic information.")
        }
    }

    private fun buildFlashcardPrompt(name: String, description: String, count: Int): String {
        return buildString(256) {
            appendLine("Create $count flashcards about: $name")
            appendLine("Description: $description")
            appendLine()
            appendLine("Format EXACTLY like this:")
            appendLine()
            appendLine("FLASHCARD 1")
            appendLine("FRONT: What is the primary function of mitochondria?")
            appendLine("BACK: To produce ATP through cellular respiration.")
            appendLine()
            appendLine("FLASHCARD 2")
            appendLine("FRONT: Why are mitochondria called the powerhouse?")
            appendLine("BACK: Because they generate energy for the cell.")
            appendLine()
            appendLine("Create all $count flashcards.")
        }
    }

    private fun buildQuizPrompt(name: String, description: String, count: Int): String {
        return buildString(512) {
            appendLine("Create $count quiz questions about: $name")
            appendLine("Description: $description")
            appendLine()
            appendLine("Include a mix of:")
            appendLine("- Multiple Choice (MCQ)")
            appendLine("- True/False")
            appendLine()
            appendLine("Format EXACTLY like this:")
            appendLine()
            appendLine("QUESTION 1")
            appendLine("TYPE: MCQ")
            appendLine("TEXT: What molecule does mitochondria produce?")
            appendLine("OPTION A: ATP")
            appendLine("OPTION B: DNA")
            appendLine("OPTION C: RNA")
            appendLine("OPTION D: Glucose")
            appendLine("CORRECT: A")
            appendLine()
            appendLine("QUESTION 2")
            appendLine("TYPE: TRUE_FALSE")
            appendLine("TEXT: Mitochondria contain their own DNA.")
            appendLine("CORRECT: TRUE")
            appendLine()
            appendLine("Create all $count questions now.")
        }
    }

    /**
     * Fast parsing without regex overhead.
     */
    private fun parseConceptsFast(response: String): List<ExtractedConcept> {
        val concepts = mutableListOf<ExtractedConcept>()
        val lines = response.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("CONCEPT:", ignoreCase = true) -> {
                    val name = line.substring(8).trim()
                    var description = ""

                    // Look ahead for description
                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        if (nextLine.startsWith("DESCRIPTION:", ignoreCase = true)) {
                            description = nextLine.substring(12).trim()
                            i++
                        }
                    }

                    if (name.isNotEmpty()) {
                        concepts.add(ExtractedConcept(name, description))
                    }
                }
            }
            i++
        }

        return concepts
    }

    private fun parseFlashcardsFast(response: String): List<GeneratedFlashcard> {
        val flashcards = mutableListOf<GeneratedFlashcard>()
        val lines = response.lines()
        var i = 0
        var currentFront: String? = null

        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("FRONT:", ignoreCase = true) -> {
                    currentFront = line.substring(6).trim()
                }
                line.startsWith("BACK:", ignoreCase = true) && currentFront != null -> {
                    val back = line.substring(5).trim()
                    flashcards.add(GeneratedFlashcard(currentFront, back))
                    currentFront = null
                }
            }
            i++
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
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("TYPE:", ignoreCase = true) -> {
                        type = trimmed.substring(5).trim()
                    }
                    trimmed.startsWith("TEXT:", ignoreCase = true) -> {
                        text = trimmed.substring(5).trim()
                    }
                    trimmed.startsWith("OPTION", ignoreCase = true) -> {
                        val optionText = trimmed.substringAfter(":", "").trim()
                        if (optionText.isNotEmpty()) options.add(optionText)
                    }
                    trimmed.startsWith("CORRECT:", ignoreCase = true) -> {
                        correct = trimmed.substring(8).trim()
                    }
                }
            }

            if (text.isNotEmpty()) {
                questions.add(GeneratedQuizQuestion(
                    type = type,
                    text = text,
                    options = options.ifEmpty { null },
                    correctAnswer = correct
                ))
            }
        }

        return questions
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

/**
 * Generated quiz question from AI.
 */
data class GeneratedQuizQuestion(
    val type: String,
    val text: String,
    val options: List<String>?,
    val correctAnswer: String
)

// ========== STATE CLASSES ==========

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

