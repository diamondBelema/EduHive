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
 * Implements multi-stage generation: draft → validate → refine → store.
 */
@Singleton
class AIDataSource @Inject constructor(
    private val modelManager: AIModelManager,
    private val modelPreferences: ModelPreferences,
    private val flashcardValidator: FlashcardValidator
) {

    companion object {
        private const val TAG = "AIDataSource"

        /** Minimum flashcard pass rate before triggering a refinement pass. */
        private const val MIN_PASS_RATE = 0.7f

        /** Maximum concepts to group in a single batched flashcard request. */
        const val BATCH_SIZE = 5
    }

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

            val prompt = LLMPromptTemplates.conceptExtraction(page, hiveContext)
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

            val prompt = LLMPromptTemplates.conceptExtraction(text, hiveContext)
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
                operation = { page -> LLMPromptTemplates.conceptExtraction(page, hiveContext) }
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
     *
     * Applies two-pass generation: draft → validate → refine if needed.
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

        val flashcards = generateWithValidation(conceptName, conceptDescription, count)
        send(FlashcardGenerationState.Success(flashcards))
    }.flowOn(Dispatchers.IO)

    /**
     * Generate flashcards (blocking).
     *
     * Applies two-pass generation: draft → validate → refine if needed.
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
            val flashcards = generateWithValidation(conceptName, conceptDescription, count)
            Result.success(flashcards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate flashcards for a batch of concepts in a single request.
     *
     * Grouping 3–5 concepts per call improves global context, reduces duplicate
     * questions, and produces more diverse flashcards.
     *
     * Returns a list of pairs: (conceptIndex, GeneratedFlashcard) where conceptIndex
     * is 1-based, matching the concept order in [concepts].
     */
    suspend fun generateFlashcardsBatch(
        concepts: List<Pair<String, String>>,
        countPerConcept: Int = 5
    ): Result<List<Pair<Int, GeneratedFlashcard>>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded()) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            val prompt = LLMPromptTemplates.flashcardBatch(concepts, countPerConcept)
            val response = modelManager.generate(prompt).getOrThrow()
            val parsed = parseFlashcardsWithConceptIndex(response, concepts.size)
            val validated = parsed.filter { (_, card) -> flashcardValidator.validate(card).isValid }
            Result.success(validated)
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

        val prompt = LLMPromptTemplates.quizGeneration(conceptName, conceptDescription, questionCount)
        
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
            val prompt = LLMPromptTemplates.quizGeneration(conceptName, conceptDescription, questionCount)
            val response = modelManager.generate(prompt).getOrThrow()
            Result.success(parseQuizFromResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Two-pass generation with retry and mutation.
     *
     * Pass 1: Generate draft flashcards.
     * Validate: Check quality with [FlashcardValidator].
     * If pass rate < [MIN_PASS_RATE]: Retry with prompt mutations, then refine.
     * Pass 2 (if needed): Refine draft flashcards that failed validation.
     */
    private suspend fun generateWithValidation(
        conceptName: String,
        conceptDescription: String,
        count: Int
    ): List<GeneratedFlashcard> {
        val basePrompt = LLMPromptTemplates.flashcardDraft(conceptName, conceptDescription, count)

        // Retry with prompt mutation if quality is too low
        var bestDraft: List<GeneratedFlashcard> = emptyList()
        var bestPassRate = 0f
        var earlySuccess = false

        for (attempt in 0 until GenerationConfig.FLASHCARD.retryAttempts) {
            val mutatedPrompt = LLMPromptTemplates.mutate(basePrompt, attempt)
            val result = modelManager.generate(mutatedPrompt)

            result.onSuccess { response ->
                val parsed = parseFlashcardsRobust(response)
                val passRate = flashcardValidator.passRate(parsed)
                Log.d(TAG, "Attempt $attempt: ${parsed.size} cards, pass rate=$passRate")

                if (passRate > bestPassRate) {
                    bestPassRate = passRate
                    bestDraft = parsed
                }

                if (passRate >= MIN_PASS_RATE) {
                    earlySuccess = true
                }
            }.onFailure { e ->
                Log.w(TAG, "Generation attempt $attempt failed: ${e.message}")
            }

            if (earlySuccess) break
        }

        if (earlySuccess) {
            return flashcardValidator.filterValid(bestDraft)
        }

        // Pass 2: Refine if pass rate still below threshold
        val validFromDraft = flashcardValidator.filterValid(bestDraft)
        val invalidFromDraft = bestDraft.filter { !flashcardValidator.validate(it).isValid }

        if (invalidFromDraft.isNotEmpty() && bestPassRate < MIN_PASS_RATE) {
            Log.d(TAG, "Pass 2: Refining ${invalidFromDraft.size} low-quality flashcards")
            val refinementPrompt = LLMPromptTemplates.flashcardRefinement(invalidFromDraft)
            val refinedResult = modelManager.generate(refinementPrompt)

            var refinedCards: List<GeneratedFlashcard> = emptyList()
            refinedResult.onSuccess { refinedResponse ->
                refinedCards = flashcardValidator.filterValid(parseFlashcardsRobust(refinedResponse))
            }.onFailure { e ->
                Log.w(TAG, "Refinement pass failed: ${e.message}")
            }

            if (refinedCards.isNotEmpty()) {
                return (validFromDraft + refinedCards).distinctBy { it.front.lowercase().trim() }
            }
        }

        return validFromDraft.ifEmpty { bestDraft }
    }

    private suspend fun ensureModelLoaded(): Boolean {
        if (modelManager.isModelLoaded()) return true
        val modelId = modelPreferences.getActiveModel() ?: return false
        return modelManager.loadModel(modelId).isSuccess
    }

    private fun deduplicateConcepts(concepts: List<ExtractedConcept>): List<ExtractedConcept> {
        return concepts.distinctBy { it.name.lowercase().trim() }
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

    /**
     * Parses flashcards from a batch response that includes CONCEPT: tags.
     *
     * Returns pairs of (1-based conceptIndex, GeneratedFlashcard).
     * Falls back to round-robin distribution if CONCEPT tags are missing.
     */
    private fun parseFlashcardsWithConceptIndex(
        response: String,
        conceptCount: Int
    ): List<Pair<Int, GeneratedFlashcard>> {
        val result = mutableListOf<Pair<Int, GeneratedFlashcard>>()
        val lines = response.lines()
        var currentConceptIndex = 1
        var conceptTagSeenForCurrentCard = false
        var currentFront: String? = null
        var cardCount = 0

        for (line in lines) {
            val cleanLine = line.replace("*", "").trim()
            when {
                cleanLine.startsWith("CONCEPT:", ignoreCase = true) -> {
                    val indexStr = cleanLine.substringAfter(":").trim()
                    val parsed = indexStr.toIntOrNull()
                    if (parsed != null && parsed in 1..conceptCount) {
                        currentConceptIndex = parsed
                        conceptTagSeenForCurrentCard = true
                    }
                }
                cleanLine.startsWith("FRONT:", ignoreCase = true) -> {
                    currentFront = cleanLine.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
                }
                cleanLine.startsWith("BACK:", ignoreCase = true) && currentFront != null -> {
                    val front = currentFront ?: ""
                    val back = cleanLine.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
                    if (front.isNotBlank() && back.isNotEmpty()) {
                        result.add(Pair(currentConceptIndex, GeneratedFlashcard(front, back)))
                        cardCount++
                        // Advance concept index round-robin when no CONCEPT tag was provided
                        if (!conceptTagSeenForCurrentCard && conceptCount > 1) {
                            currentConceptIndex = (cardCount % conceptCount) + 1
                        }
                    }
                    currentFront = null
                    conceptTagSeenForCurrentCard = false
                }
            }
        }
        return result
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

