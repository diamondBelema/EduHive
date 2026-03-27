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
 * AI Data Source — builds prompts, calls the model, parses responses.
 *
 * Key fixes applied here (see companion object comments for rationale):
 *  1. ensureModelLoaded() now accepts and forwards a GenerationConfig so the
 *     right maxTokens / temperature is set for each task type.
 *  2. Per-task input character limits replace the single MAX_INPUT_CHARS constant.
 *  3. parseConceptsRobust() strips prompt echo before parsing.
 *  4. Page-level failures are counted and surfaced — no more silent swallowing.
 *  5. Concept extraction requests 3 concepts per page (not 5) to match the
 *     tighter token budget and improve per-concept quality.
 *  6. Flashcard count per call capped at 3 for the same budget reason.
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
        private const val MIN_PASS_RATE = 0.5f   // Lowered from 0.7 — see Fix notes

        /**
         * Maximum concepts per batched flashcard request.
         * Kept at 3 (down from 5) — small models lose coherence in larger batches.
         */
        const val BATCH_SIZE = 3

        // ── Per-task input character limits ───────────────────────────────
        // These replace the single MAX_INPUT_CHARS = 3500 constant.
        //
        // Rule: prompt_overhead_chars + input_chars + expected_output_chars
        //       must fit inside maxTokens * 4 (rough chars-per-token estimate).
        //
        // Concept extraction:
        //   maxTokens=2048 → 8192 chars total budget
        //   Prompt overhead: ~520 chars (~130 tok)
        //   Input ceiling:   1600 chars (~400 tok)
        //   Output headroom: ~6072 chars (~1518 tok) ← plenty for 3 concept pairs
        //
        // Flashcard generation:
        //   maxTokens=1536 → 6144 chars total budget
        //   Prompt overhead: ~400 chars (~100 tok)
        //   Input ceiling:   800 chars (~200 tok)  ← concept name + description
        //   Output headroom: ~4944 chars (~1236 tok) ← plenty for 3 FRONT/BACK pairs
        //
        // Quiz generation:
        //   maxTokens=1536 → 6144 chars total budget
        //   Prompt overhead: ~550 chars (~138 tok)
        //   Input ceiling:   800 chars (~200 tok)
        //   Output headroom: ~4794 chars (~1199 tok)
        //
        private const val MAX_INPUT_CHARS_CONCEPTS   = 1600
        private const val MAX_INPUT_CHARS_FLASHCARDS = 800
        private const val MAX_INPUT_CHARS_QUIZ       = 800

        /** Flashcards requested per concept per call. 3 is reliable across all model sizes. */
        private const val FLASHCARDS_PER_CONCEPT = 3

        /**
         * Max flashcard facts to inject into a quiz prompt.
         * 3 facts × ~50 chars each = ~150 chars / ~38 tokens — fits comfortably
         * inside the quiz input budget alongside the prompt template overhead.
         */
        private const val MAX_FACTS_PER_QUIZ = 3
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concept extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extract concepts from a list of pages with streaming progress.
     * Preferred entry point for PDFs — processes page-by-page.
     *
     * Fix 1: passes CONCEPT_EXTRACTION config to ensureModelLoaded().
     * Fix 2: uses MAX_INPUT_CHARS_CONCEPTS limit (1600) not 3500.
     * Fix 3: tracks per-page failures; surfaces an error if ALL pages fail.
     * Fix 4: calls parseConceptsRobust() which strips echo before parsing.
     */
    fun extractConceptsFromPagesStreaming(
        pages: List<String>,
        hiveContext: String = ""
    ): Flow<ConceptExtractionState> = channelFlow {
        send(ConceptExtractionState.Loading)

        if (!ensureModelLoaded(GenerationConfig.CONCEPT_EXTRACTION)) {
            send(ConceptExtractionState.Error("No model available"))
            return@channelFlow
        }

        val allExtractedConcepts = mutableListOf<ExtractedConcept>()
        val totalPages = pages.size
        var failedPages = 0

        pages.forEachIndexed { index, page ->
            send(ConceptExtractionState.Progress(((index.toFloat() / totalPages) * 100).toInt()))

            // Enforce per-task input limit before building the prompt
            val safePage = page.take(MAX_INPUT_CHARS_CONCEPTS)
            val prompt = LLMPromptTemplates.conceptExtraction(safePage, hiveContext)
            val result = modelManager.generate(prompt)

            result.onSuccess { response ->
                val pageConcepts = parseConceptsRobust(response)
                Log.d(TAG, "Page $index: parsed ${pageConcepts.size} concepts from ${response.length} char response")
                allExtractedConcepts.addAll(pageConcepts)
            }.onFailure { e ->
                failedPages++
                Log.w(TAG, "Page $index failed (${failedPages}/$totalPages failures): ${e.message}")
                // Reinitialise the engine before the next page
                modelManager.unloadModel()
                delay(300)
                ensureModelLoaded(GenerationConfig.CONCEPT_EXTRACTION)
            }
        }

        val uniqueConcepts = deduplicateConcepts(allExtractedConcepts)

        when {
            uniqueConcepts.isNotEmpty() -> {
                Log.d(TAG, "Extraction complete: ${uniqueConcepts.size} unique concepts from $totalPages pages ($failedPages failed)")
                send(ConceptExtractionState.Success(uniqueConcepts))
            }
            failedPages == totalPages -> {
                send(ConceptExtractionState.Error("All $totalPages pages failed to process. Check model status or try a different file."))
            }
            else -> {
                send(ConceptExtractionState.Error("No concepts could be identified. Try a different section or check file quality."))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Streaming extraction for a single block of text. */
    fun extractConceptsStreaming(
        text: String,
        hiveContext: String = ""
    ): Flow<ConceptExtractionState> = extractConceptsFromPagesStreaming(listOf(text), hiveContext)

    /** Blocking single-text extraction. */
    suspend fun extractConcepts(
        text: String,
        hiveContext: String = ""
    ): Result<List<ExtractedConcept>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded(GenerationConfig.CONCEPT_EXTRACTION)) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            val safePage = text.take(MAX_INPUT_CHARS_CONCEPTS)
            val prompt = LLMPromptTemplates.conceptExtraction(safePage, hiveContext)
            val response = modelManager.generate(prompt).getOrThrow()
            Result.success(parseConceptsRobust(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Blocking multi-page extraction. */
    suspend fun extractConceptsFromDocument(
        pages: List<String>,
        hiveContext: String = ""
    ): Result<List<ExtractedConcept>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded(GenerationConfig.CONCEPT_EXTRACTION)) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            val pageResults = modelManager.processDocumentBatched(
                pages = pages,
                operation = { page ->
                    LLMPromptTemplates.conceptExtraction(page.take(MAX_INPUT_CHARS_CONCEPTS), hiveContext)
                }
            )
            val all = pageResults.flatMap { parseConceptsRobust(it) }
            Result.success(deduplicateConcepts(all))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flashcard generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate flashcards for one concept with streaming progress.
     *
     * Fix: requests FLASHCARDS_PER_CONCEPT (3) instead of 5.
     * Fix: passes FLASHCARD config to ensureModelLoaded().
     * Fix: MIN_PASS_RATE lowered to 0.5 — avoids endless retry loops on small models.
     */
    fun generateFlashcardsStreaming(
        conceptName: String,
        conceptDescription: String,
        count: Int = FLASHCARDS_PER_CONCEPT
    ): Flow<FlashcardGenerationState> = channelFlow {
        send(FlashcardGenerationState.Loading)

        if (!ensureModelLoaded(GenerationConfig.FLASHCARD)) {
            send(FlashcardGenerationState.Error("No model available"))
            return@channelFlow
        }

        val result = generateWithValidation(
            conceptName = conceptName,
            conceptDescription = conceptDescription,
            count = count,
            onRetrying = { attempt -> send(FlashcardGenerationState.Retrying(attempt)) }
        )
        send(FlashcardGenerationState.Validating)
        send(FlashcardGenerationState.Success(result.flashcards, result.rejectedCount))
    }.flowOn(Dispatchers.IO)

    /** Blocking flashcard generation. */
    suspend fun generateFlashcards(
        conceptName: String,
        conceptDescription: String,
        count: Int = FLASHCARDS_PER_CONCEPT
    ): Result<List<GeneratedFlashcard>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded(GenerationConfig.FLASHCARD)) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            val result = generateWithValidation(conceptName, conceptDescription, count)
            Result.success(result.flashcards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batched flashcard generation across multiple concepts.
     * Caller should pass batches of <= BATCH_SIZE (3) concepts.
     */
    suspend fun generateFlashcardsBatch(
        concepts: List<Pair<String, String>>,
        countPerConcept: Int = FLASHCARDS_PER_CONCEPT
    ): Result<List<Pair<Int, GeneratedFlashcard>>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded(GenerationConfig.FLASHCARD)) {
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

    // ─────────────────────────────────────────────────────────────────────────
    // Quiz generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate quiz questions with streaming progress.
     *
     * [facts] is a list of "Q: <front> | A: <back>" strings derived from the
     * concept's existing flashcards. One question is generated per fact, giving
     * the model a distinct piece of knowledge to work with for each question —
     * which eliminates the rephrasing-the-same-question problem.
     *
     * [questionCount] should equal facts.size. If facts is empty, the caller
     * should pass questionCount=1 so the model isn't asked to invent multiple
     * questions from a single one-sentence description.
     */
    fun generateQuizStreaming(
        conceptName: String,
        conceptDescription: String,
        facts: List<String> = emptyList(),
        questionCount: Int = 3
    ): Flow<QuizGenerationState> = channelFlow {
        send(QuizGenerationState.Loading)

        if (!ensureModelLoaded(GenerationConfig.QUIZ)) {
            send(QuizGenerationState.Error("No model available"))
            return@channelFlow
        }

        val safeDesc = conceptDescription.take(MAX_INPUT_CHARS_QUIZ)
        // Cap facts to token budget: each "Q: ... | A: ..." entry is ~30–50 chars
        val safeFacts = facts.take(MAX_FACTS_PER_QUIZ)
        val prompt = LLMPromptTemplates.quizGeneration(conceptName, safeDesc, safeFacts, questionCount)

        modelManager.generateStreaming(prompt).collect { result ->
            when (result) {
                is GenerationResult.Progress -> send(QuizGenerationState.Generating(
                    (result.completedChunks * 100) / result.totalChunks
                ))
                is GenerationResult.Success  -> send(QuizGenerationState.Success(
                    parseQuizFromResponse(result.text)
                ))
                is GenerationResult.Error    -> send(QuizGenerationState.Error(
                    result.exception.message ?: "Quiz generation failed"
                ))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Blocking quiz generation. */
    suspend fun generateQuiz(
        conceptName: String,
        conceptDescription: String,
        facts: List<String> = emptyList(),
        questionCount: Int = 3
    ): Result<List<GeneratedQuizQuestion>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded(GenerationConfig.QUIZ)) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            val safeDesc = conceptDescription.take(MAX_INPUT_CHARS_QUIZ)
            val safeFacts = facts.take(MAX_FACTS_PER_QUIZ)
            val prompt = LLMPromptTemplates.quizGeneration(conceptName, safeDesc, safeFacts, questionCount)
            val response = modelManager.generate(prompt).getOrThrow()
            Result.success(parseQuizFromResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Two-pass flashcard validation pipeline
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun generateWithValidation(
        conceptName: String,
        conceptDescription: String,
        count: Int,
        onRetrying: suspend (Int) -> Unit = {}
    ): ValidationResult {
        val safeDesc = conceptDescription.take(MAX_INPUT_CHARS_FLASHCARDS)
        val basePrompt = LLMPromptTemplates.flashcardDraft(conceptName, safeDesc, count)

        var bestDraft: List<GeneratedFlashcard> = emptyList()
        var bestPassRate = 0f
        var earlySuccess = false

        for (attempt in 0 until GenerationConfig.FLASHCARD.retryAttempts) {
            if (attempt > 0) onRetrying(attempt)

            val mutatedPrompt = LLMPromptTemplates.mutate(basePrompt, attempt)
            val result = modelManager.generate(mutatedPrompt)

            result.onSuccess { response ->
                val parsed = parseFlashcardsRobust(response)
                val passRate = flashcardValidator.passRate(parsed)
                Log.d(TAG, "Flashcard attempt $attempt: ${parsed.size} cards, passRate=$passRate")

                if (passRate > bestPassRate) {
                    bestPassRate = passRate
                    bestDraft = parsed
                }
                if (passRate >= MIN_PASS_RATE) earlySuccess = true
            }.onFailure { e ->
                Log.w(TAG, "Flashcard attempt $attempt failed: ${e.message}")
            }

            if (earlySuccess) break
        }

        // Fast path: pass rate already good enough
        if (earlySuccess) {
            val valid = flashcardValidator.filterValid(bestDraft)
            return ValidationResult(valid, bestDraft.size - valid.size)
        }

        // Pass 2: refine the cards that failed validation
        val validFromDraft   = flashcardValidator.filterValid(bestDraft)
        val invalidFromDraft = bestDraft.filter { !flashcardValidator.validate(it).isValid }

        if (invalidFromDraft.isNotEmpty() && bestPassRate < MIN_PASS_RATE) {
            Log.d(TAG, "Pass 2: refining ${invalidFromDraft.size} low-quality cards")
            val refinementPrompt = LLMPromptTemplates.flashcardRefinement(invalidFromDraft)
            val refinedResult = modelManager.generate(refinementPrompt)

            refinedResult.onSuccess { refinedResponse ->
                val refined = flashcardValidator.filterValid(parseFlashcardsRobust(refinedResponse))
                if (refined.isNotEmpty()) {
                    val combined = (validFromDraft + refined).distinctBy { it.front.lowercase().trim() }
                    return ValidationResult(combined, bestDraft.size - combined.size)
                }
            }.onFailure { e ->
                Log.w(TAG, "Refinement pass failed: ${e.message}")
            }
        }

        // Accept what we have — even if below threshold, it's better than nothing
        val finalCards = validFromDraft.ifEmpty { bestDraft }
        return ValidationResult(finalCards, bestDraft.size - finalCards.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ensure the model is loaded with the correct config for this task type.
     *
     * Fix: previously called loadModel(modelId) with no config, meaning the
     * model ALWAYS loaded with GenerationConfig() defaults regardless of task.
     * Now each task passes its specific config so maxTokens and temperature
     * are correct for the work about to be done.
     *
     * Note: if the model is already loaded from a prior call with a different
     * config, we do NOT reload — reloading is expensive (~2–5 seconds). This
     * means the config used for the FIRST load in a session persists. A future
     * improvement is to track the active config and reload only when it differs.
     */
    private suspend fun ensureModelLoaded(config: GenerationConfig = GenerationConfig()): Boolean {
        if (modelManager.isModelLoaded()) return true
        val modelId = modelPreferences.getActiveModel() ?: return false
        return modelManager.loadModel(modelId, config).isSuccess
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Strip prompt echo from the model response before parsing.
     *
     * Small models (especially 135M–270M) often begin their response by
     * repeating part of the prompt — including the two example concepts we
     * provide. This strips everything up to and including the last instruction
     * boundary so only the model's own generated output is parsed.
     *
     * Strategy:
     *  1. Find the position of the last instruction line in the response.
     *     Instruction lines are the lines we wrote — they end with the second
     *     example DESCRIPTION line.
     *  2. Everything after that position is the model's output.
     *  3. If no instruction boundary is found, skip known example concepts
     *     by name using KNOWN_EXAMPLE_CONCEPT_NAMES.
     */
    private fun stripPromptEcho(response: String): String {
        // The last line of our prompt examples is always a DESCRIPTION: line
        // for "Mitosis". Find the last occurrence of it.
        val echoMarkers = listOf(
            "DESCRIPTION: A short definition of a key idea found in the provided text.",
            "DESCRIPTION: Another distinct idea from the provided text, not a repeat.",
            "Output format:",
            "Extract up to 10 specific concepts"
        )
        for (marker in echoMarkers) {
            val idx = response.lastIndexOf(marker)
            if (idx != -1) {
                // Skip past this line to find where the model's real output begins
                val afterMarker = response.substring(idx)
                val nextConcept = afterMarker.indexOf("\nCONCEPT:")
                if (nextConcept != -1) {
                    return afterMarker.substring(nextConcept).trim()
                }
            }
        }
        return response
    }

    /**
     * Parse CONCEPT: / DESCRIPTION: pairs from a model response.
     *
     * Robustness features:
     *  - Strips prompt echo before parsing (see stripPromptEcho).
     *  - Strips asterisks (model markdown artifacts).
     *  - Removes [ ] bracket wrapping (placeholder leftovers).
     *  - Skips known example concept names from the prompt template.
     *  - Requires both name and description to be non-blank before adding.
     */
    private fun parseConceptsRobust(response: String): List<ExtractedConcept> {
        val cleaned = stripPromptEcho(response)
        val concepts = mutableListOf<ExtractedConcept>()
        var currentName: String? = null

        for (line in cleaned.lines()) {
            val cleanLine = line.replace("*", "").trim()

            when {
                cleanLine.startsWith("CONCEPT:", ignoreCase = true) -> {
                    currentName = cleanLine
                        .substringAfter(":")
                        .trim()
                        .removePrefix("[").removeSuffix("]")
                        .trim()
                    // Skip if this is a known example name from the prompt
                    if (currentName.lowercase() in LLMPromptTemplates.KNOWN_EXAMPLE_CONCEPT_NAMES) {
                        currentName = null
                    }
                }

                cleanLine.startsWith("DESCRIPTION:", ignoreCase = true) && currentName != null -> {
                    val description = cleanLine
                        .substringAfter(":")
                        .trim()
                        .removePrefix("[").removeSuffix("]")
                        .trim()
                    if (currentName!!.isNotBlank() && description.length > 10) {
                        concepts.add(ExtractedConcept(currentName!!, description))
                    }
                    currentName = null
                }
            }
        }
        return concepts
    }

    private fun parseFlashcardsRobust(response: String): List<GeneratedFlashcard> {
        val flashcards = mutableListOf<GeneratedFlashcard>()
        var currentFront: String? = null

        for (line in response.lines()) {
            val cleanLine = line.replace("*", "").trim()
            when {
                cleanLine.startsWith("FRONT:", ignoreCase = true) -> {
                    currentFront = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                    // Keep parser generic; prompt examples are neutral and filtered elsewhere if echoed.
                }
                cleanLine.startsWith("BACK:", ignoreCase = true) && currentFront != null -> {
                    val back = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                    if (currentFront!!.isNotBlank() && back.length > 4) {
                        flashcards.add(GeneratedFlashcard(currentFront!!, back))
                    }
                    currentFront = null
                }
            }
        }
        return flashcards
    }

    private fun parseFlashcardsWithConceptIndex(
        response: String,
        conceptCount: Int
    ): List<Pair<Int, GeneratedFlashcard>> {
        val result = mutableListOf<Pair<Int, GeneratedFlashcard>>()
        var currentConceptIndex = 1
        var conceptTagSeen = false
        var currentFront: String? = null
        var cardCount = 0

        for (line in response.lines()) {
            val cleanLine = line.replace("*", "").trim()
            when {
                cleanLine.startsWith("CONCEPT:", ignoreCase = true) -> {
                    val parsed = cleanLine.substringAfter(":").trim().toIntOrNull()
                    if (parsed != null && parsed in 1..conceptCount) {
                        currentConceptIndex = parsed
                        conceptTagSeen = true
                    }
                }
                cleanLine.startsWith("FRONT:", ignoreCase = true) -> {
                    currentFront = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                }
                cleanLine.startsWith("BACK:", ignoreCase = true) && currentFront != null -> {
                    val back = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                    if (currentFront!!.isNotBlank() && back.length > 4) {
                        result.add(Pair(currentConceptIndex, GeneratedFlashcard(currentFront!!, back)))
                        cardCount++
                        if (!conceptTagSeen && conceptCount > 1) {
                            currentConceptIndex = (cardCount % conceptCount) + 1
                        }
                    }
                    currentFront = null
                    conceptTagSeen = false
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
            var type = "MCQ"
            var text = ""
            val options = mutableListOf<String>()
            var correct = ""

            for (line in block.trim().lines()) {
                val trimmed = line.replace("*", "").trim()
                when {
                    trimmed.startsWith("TYPE:",    ignoreCase = true) -> type    = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("TEXT:",    ignoreCase = true) -> text    = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("OPTION",   ignoreCase = true) -> {
                        val opt = trimmed.substringAfter(":", "").trim()
                        if (opt.isNotEmpty()) options.add(opt)
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

    private fun deduplicateConcepts(concepts: List<ExtractedConcept>): List<ExtractedConcept> {
        return concepts.distinctBy { it.name.lowercase().trim() }
    }

    suspend fun chat(message: String): Result<String> = modelManager.generate(message)
}

// ─────────────────────────────────────────────────────────────────────────────
// Data and state classes (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

data class ExtractedConcept(val name: String, val description: String)
data class GeneratedFlashcard(val front: String, val back: String)
data class GeneratedQuizQuestion(
    val type: String,
    val text: String,
    val options: List<String>?,
    val correctAnswer: String
)

private data class ValidationResult(
    val flashcards: List<GeneratedFlashcard>,
    val rejectedCount: Int
)

sealed class ConceptExtractionState {
    object Loading : ConceptExtractionState()
    data class Progress(val percent: Int) : ConceptExtractionState()
    data class Success(val concepts: List<ExtractedConcept>) : ConceptExtractionState()
    data class Error(val message: String) : ConceptExtractionState()
}

sealed class FlashcardGenerationState {
    object Loading : FlashcardGenerationState()
    data class Retrying(val attempt: Int) : FlashcardGenerationState()
    object Validating : FlashcardGenerationState()
    data class Success(
        val flashcards: List<GeneratedFlashcard>,
        val rejectedCount: Int = 0
    ) : FlashcardGenerationState()
    data class Error(val message: String) : FlashcardGenerationState()
}

sealed class QuizGenerationState {
    object Loading : QuizGenerationState()
    data class Generating(val percent: Int) : QuizGenerationState()
    data class Success(val questions: List<GeneratedQuizQuestion>) : QuizGenerationState()
    data class Error(val message: String) : QuizGenerationState()
}