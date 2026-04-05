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
 */
@Singleton
class AIDataSource @Inject constructor(
    private val modelManager: AIModelManager,
    private val modelPreferences: ModelPreferences,
    private val flashcardValidator: FlashcardValidator
) {

    companion object {
        private const val TAG = "AIDataSource"

        /**
         * Minimum flashcard pass rate before triggering a refinement pass.
         * Lowered to 0.4 — more realistic for small models while maintaining quality.
         */
        private const val MIN_PASS_RATE = 0.4f

        /**
         * Maximum concepts per batched flashcard request.
         * Kept at 3 — small models lose coherence in larger batches.
         */
        const val BATCH_SIZE = 3

        // ── Per-task input character limits ───────────────────────────────
        // MAX_INPUT_CHARS_CONCEPTS = 800:
        // Token budget: 1280 total window
        //   prompt overhead (template + examples): ~200 tokens (~800 chars)
        //   input text: ~200 tokens (~800 chars)
        //   output headroom: ~880 tokens — enough for 10+ concept pairs at ~20 tok each
        // Kept at 800 to reliably stay under the window even on dense academic text.
        // Each batch of 800 chars = roughly 1 PDF page after cleaning.
        private const val MAX_INPUT_CHARS_CONCEPTS   = 800
        private const val MAX_INPUT_CHARS_FLASHCARDS = 800
        private const val MAX_INPUT_CHARS_QUIZ       = 800

        /** Flashcards requested per concept per call. 3 is reliable across all model sizes. */
        private const val FLASHCARDS_PER_CONCEPT = 3

        /**
         * Max unique concepts to accept from a single batch.
         * Small models loop — they'll repeat the same concept 40+ times in one response.
         * Capping at 8 per batch to allow more diversity while still stopping runaway loops.
         */
        private const val MAX_CONCEPTS_PER_BATCH = 8

        /**
         * Max flashcard facts to inject into a quiz prompt.
         */
        private const val MAX_FACTS_PER_QUIZ = 3
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Grounded document chat (Phase 1)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun answerQuestionFromContext(
        question: String,
        contextChunks: List<GroundedContextChunk>
    ): Result<GroundedAnswer> = withContext(Dispatchers.IO) {
        try {
            if (!ensureModelLoaded(GenerationConfig.QUIZ)) {
                return@withContext Result.failure(IllegalStateException("No model available"))
            }
            if (contextChunks.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No context chunks provided"))
            }

            val prompt = LLMPromptTemplates.groundedChat(question, contextChunks)
            val response = modelManager.generate(prompt).getOrThrow()
            Result.success(parseGroundedAnswer(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retain the model for a multi-step processing pipeline.
     * Prevents mid-pipeline unloads that would require an expensive reload.
     * Must be paired with exactly one [releaseModelRef] call.
     */
    fun retainModelForPipeline() = modelManager.retainModelRef()

    /**
     * Release the pipeline model reference acquired via [retainModelForPipeline].
     * If no other references are held and an unload was requested during the
     * pipeline, the model will be unloaded now.
     */
    suspend fun releaseModelRef() = modelManager.releaseModelRef()


    // ─────────────────────────────────────────────────────────────────────────
    // Concept extraction
    // ─────────────────────────────────────────────────────────────────────────

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
        // Track seen names across ALL batches to stop duplicates accumulating
        val seenNames = mutableSetOf<String>()

        val batches = mutableListOf<String>()
        val currentBatch = StringBuilder()
        for (page in pages) {
            val trimmed = page.trim()
            if (trimmed.isBlank()) continue
            if (currentBatch.isNotEmpty() && currentBatch.length + trimmed.length + 2 > MAX_INPUT_CHARS_CONCEPTS) {
                batches.add(currentBatch.toString().trim())
                currentBatch.clear()
            }
            if (currentBatch.isNotEmpty()) currentBatch.append("\n\n")
            currentBatch.append(trimmed)
        }
        if (currentBatch.isNotEmpty()) batches.add(currentBatch.toString().trim())

        val totalBatches = batches.size
        var failedBatches = 0

        Log.d(TAG, "Extraction: ${pages.size} pages → $totalBatches batches")

        batches.forEachIndexed { index, batch ->
            send(ConceptExtractionState.Progress(((index.toFloat() / totalBatches) * 100).toInt()))

            val prompt = LLMPromptTemplates.conceptExtraction(batch, hiveContext)
            val result = modelManager.generate(prompt)

            result.onSuccess { response ->
                Log.d(TAG, "╔══ CONCEPT BATCH $index RAW (${response.length} chars): ${response.take(300).replace('\n','↵')}")
                val batchConcepts = parseConceptsRobust(response)
                Log.d(TAG, "╚══ CONCEPT BATCH $index: parsed ${batchConcepts.size} concepts: ${batchConcepts.map { it.name }}")

                // Deduplicate within this batch AND against all previous batches.
                // Small models loop — a single batch can return the same concept 40 times.
                // Cap at MAX_CONCEPTS_PER_BATCH unique names per batch to stop runaway loops.
                var addedThisBatch = 0
                val newUniqueConcepts = mutableListOf<ExtractedConcept>()
                for (concept in batchConcepts) {
                    val key = concept.name.lowercase().trim()
                    if (key !in seenNames && addedThisBatch < MAX_CONCEPTS_PER_BATCH) {
                        seenNames.add(key)
                        allExtractedConcepts.add(concept)
                        newUniqueConcepts.add(concept)
                        addedThisBatch++
                    }
                }
                Log.d(TAG, "Batch $index: parsed ${batchConcepts.size} concepts, added $addedThisBatch unique")
                // Emit after each batch so the repository can save incrementally.
                // If the process is killed mid-extraction, already-saved batches survive.
                if (newUniqueConcepts.isNotEmpty()) {
                    send(ConceptExtractionState.BatchComplete(newUniqueConcepts))
                }
            }.onFailure { e ->
                failedBatches++
                Log.w(TAG, "Batch $index failed ($failedBatches/$totalBatches): ${e.message}")
                if (!modelManager.isModelLoaded()) {
                    delay(300)
                    ensureModelLoaded(GenerationConfig.CONCEPT_EXTRACTION)
                }
            }

            // Report completion of this batch so the progress bar advances during extraction,
            // not only at the start of the next one. This prevents the indicator from appearing
            // stuck at 15% while a single large batch is being processed.
            send(ConceptExtractionState.Progress((((index + 1).toFloat() / totalBatches) * 100).toInt()))
        }

        // Already deduplicated above, but run once more to be safe
        val uniqueConcepts = allExtractedConcepts.distinctBy { it.name.lowercase().trim() }

        when {
            uniqueConcepts.isNotEmpty() -> {
                Log.d(TAG, "Extraction complete: ${uniqueConcepts.size} unique concepts from $totalBatches batches ($failedBatches failed)")
                send(ConceptExtractionState.Success(uniqueConcepts))
            }
            failedBatches == totalBatches -> {
                send(ConceptExtractionState.Error("All $totalBatches batches failed to process. Check model status or try a different file."))
            }
            else -> {
                send(ConceptExtractionState.Error("No concepts could be identified. Try a different section or check file quality."))
            }
        }
    }.flowOn(Dispatchers.IO)

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
        count: Int = FLASHCARDS_PER_CONCEPT,
        skipValidation: Boolean = false
    ): Flow<FlashcardGenerationState> = channelFlow {
        send(FlashcardGenerationState.Loading)

        val config = if (skipValidation) GenerationConfig.FAST_TRACK_FLASHCARD else GenerationConfig.FLASHCARD
        if (!ensureModelLoaded(config)) {
            send(FlashcardGenerationState.Error("No model available"))
            return@channelFlow
        }

        val result = generateWithValidation(
            conceptName = conceptName,
            conceptDescription = conceptDescription,
            count = count,
            skipValidation = skipValidation,
            onRetrying = { attempt -> send(FlashcardGenerationState.Retrying(attempt)) }
        )
        send(FlashcardGenerationState.Validating)
        send(FlashcardGenerationState.Success(result.flashcards, result.rejectedCount))
    }.flowOn(Dispatchers.IO)

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
        val safeFacts = facts.take(MAX_FACTS_PER_QUIZ)
        val prompt = LLMPromptTemplates.quizGeneration(conceptName, safeDesc, safeFacts, questionCount)

        Log.d(TAG, "╔══ QUIZ GEN: '$conceptName' count=$questionCount facts=${safeFacts.size}")
        Log.d(TAG, "║ Prompt (${prompt.length} chars): ${prompt.take(150).replace('\n','↵')}")

        modelManager.generateStreaming(prompt).collect { result ->
            when (result) {
                is GenerationResult.Progress -> send(QuizGenerationState.Generating(
                    (result.completedChunks * 100) / result.totalChunks
                ))
                is GenerationResult.Success  -> {
                    Log.d(TAG, "║ RAW quiz response (${result.text.length} chars): ${result.text.take(300).replace('\n','↵')}")
                    val questions = parseQuizFromResponse(result.text)
                    Log.d(TAG, "╚══ QUIZ GEN: parsed ${questions.size} questions for '$conceptName'")
                    questions.forEachIndexed { i, q -> Log.d(TAG, "    [$i] type=${q.type} text='${q.text.take(60)}' correct=${q.correctAnswer} opts=${q.options?.size}") }
                    if (questions.isEmpty()) Log.e(TAG, "✗ ZERO quiz questions parsed — check raw response above")
                    send(QuizGenerationState.Success(questions))
                }
                is GenerationResult.Error    -> {
                    Log.e(TAG, "╚══ QUIZ GEN FAILED for '$conceptName': ${result.exception.message}")
                    send(QuizGenerationState.Error(result.exception.message ?: "Quiz generation failed"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

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
        skipValidation: Boolean = false,
        onRetrying: suspend (Int) -> Unit = {}
    ): ValidationResult {
        val safeDesc = conceptDescription.take(MAX_INPUT_CHARS_FLASHCARDS)
        val basePrompt = LLMPromptTemplates.flashcardDraft(conceptName, safeDesc, count)

        Log.d(TAG, "╔══ FLASHCARD GEN: '$conceptName' count=$count skipValidation=$skipValidation")
        Log.d(TAG, "║ Prompt (${basePrompt.length} chars): ${basePrompt.take(120).replace('\n', '↵')}")

        // Fast-track: single attempt, accept output as-is (no refinement pass)
        if (skipValidation) {
            val result = modelManager.generate(basePrompt)
            result.onSuccess { response ->
                val parsed = parseFlashcardsRobust(response, conceptName, conceptDescription = safeDesc)
                val filled = fillToTargetWithFallback(
                    cards = parsed,
                    conceptName = conceptName,
                    conceptDescription = safeDesc,
                    targetCount = count,
                    validate = false
                )
                Log.d(TAG, "║ Fast-track: parsed ${parsed.size} cards, final=${filled.size}")
                filled.forEachIndexed { i, c -> Log.d(TAG, "║   [$i] F='${c.front.take(40)}' B='${c.back.take(40)}'") }
                Log.d(TAG, "╚══ FLASHCARD GEN done (fast-track)")
                return ValidationResult(filled, 0)
            }.onFailure { e ->
                Log.e(TAG, "╚══ Fast-track generation FAILED for '$conceptName': ${e.message}")
            }
            return ValidationResult(emptyList(), 0)
        }

        var bestDraft: List<GeneratedFlashcard> = emptyList()
        var bestPassRate = 0f
        var earlySuccess = false

        for (attempt in 0 until GenerationConfig.FLASHCARD.retryAttempts) {
            if (attempt > 0) onRetrying(attempt)

            Log.d(TAG, "║ Attempt $attempt/${GenerationConfig.FLASHCARD.retryAttempts - 1}")
            val mutatedPrompt = LLMPromptTemplates.mutate(basePrompt, attempt)
            val result = modelManager.generate(mutatedPrompt)

            result.onSuccess { response ->
                val parsed = parseFlashcardsRobust(response, conceptName, conceptDescription = safeDesc)
                val passRate = flashcardValidator.passRate(parsed)
                Log.d(TAG, "║ Attempt $attempt: parsed=${parsed.size} valid=${flashcardValidator.filterValid(parsed).size} passRate=${"%.2f".format(passRate)}")
                parsed.forEachIndexed { i, c ->
                    val q = flashcardValidator.validate(c)
                    Log.d(TAG, "║   [$i] valid=${q.isValid} score=${"%.2f".format(q.score)} F='${c.front.take(50)}' B='${c.back.take(50)}'")
                    if (q.issues.isNotEmpty()) Log.w(TAG, "║       issues=${q.issues}")
                }
                if (passRate > bestPassRate) {
                    bestPassRate = passRate
                    bestDraft = parsed
                }
                if (passRate >= MIN_PASS_RATE) earlySuccess = true
            }.onFailure { e ->
                Log.e(TAG, "║ Attempt $attempt FAILED: ${e.message}")
            }

            if (earlySuccess) break
        }

        if (earlySuccess) {
            val valid = flashcardValidator.filterValid(bestDraft)
            val final = fillToTargetWithFallback(
                cards = valid,
                conceptName = conceptName,
                conceptDescription = safeDesc,
                targetCount = count,
                validate = true
            )
            Log.d(TAG, "╚══ Early success: ${valid.size} valid cards, final=${final.size}")
            return ValidationResult(final, bestDraft.size - valid.size)
        }

        // Pass 2: refine the cards that failed validation
        val validFromDraft   = flashcardValidator.filterValid(bestDraft)
        val invalidFromDraft = bestDraft.filter { !flashcardValidator.validate(it).isValid }

        Log.d(TAG, "║ Pass 2: ${validFromDraft.size} valid, ${invalidFromDraft.size} need refinement, bestPassRate=${"%.2f".format(bestPassRate)}")

        if (invalidFromDraft.isNotEmpty() && bestPassRate < MIN_PASS_RATE) {
            val refinementPrompt = LLMPromptTemplates.flashcardRefinement(invalidFromDraft)
            val refinedResult = modelManager.generate(refinementPrompt)

            refinedResult.onSuccess { refinedResponse ->
                val refined = flashcardValidator.filterValid(
                    parseFlashcardsRobust(refinedResponse, conceptName, conceptDescription = safeDesc)
                )
                Log.d(TAG, "║ Refinement: ${refined.size} cards passed")
                if (refined.isNotEmpty()) {
                    val combined = (validFromDraft + refined).distinctBy { it.front.lowercase().trim() }
                    Log.d(TAG, "╚══ After refinement: ${combined.size} total cards")
                    return ValidationResult(combined, bestDraft.size - combined.size)
                }
            }.onFailure { e ->
                Log.e(TAG, "║ Refinement pass FAILED: ${e.message}")
            }
        }

        val baseFinalCards = validFromDraft.ifEmpty { bestDraft }
        val finalCards = fillToTargetWithFallback(
            cards = baseFinalCards,
            conceptName = conceptName,
            conceptDescription = safeDesc,
            targetCount = count,
            validate = true
        )
        Log.d(TAG, "╚══ Final result: ${finalCards.size} cards (bestPassRate=${"%.2f".format(bestPassRate)})")
        if (finalCards.isEmpty()) {
            Log.e(TAG, "✗ ZERO flashcards produced for '$conceptName' — check raw response above")
        }
        return ValidationResult(finalCards, bestDraft.size - finalCards.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun ensureModelLoaded(config: GenerationConfig = GenerationConfig()): Boolean {
        val modelId = modelPreferences.getActiveModel()
        if (modelId == null) {
            Log.e(TAG, "ensureModelLoaded: NO ACTIVE MODEL SET — user needs to download a model")
            return false
        }
        Log.d(TAG, "ensureModelLoaded: modelId=$modelId maxTokens=${config.maxTokens} temp=${config.temperature}")
        val result = modelManager.loadModel(modelId, config)
        if (result.isFailure) {
            Log.e(TAG, "ensureModelLoaded: FAILED to load '$modelId': ${result.exceptionOrNull()?.message}")
        }
        return result.isSuccess
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Strip chat-model wrapper tokens and prompt echoes from raw model output.
     *
     * Instruction-tuned models (Gemma 1B, Qwen) may wrap responses in chat
     * turn markers or repeat the prompt before generating.  We need the bare
     * structured output (CONCEPT:/FRONT:/BACK:/QUESTION lines) only.
     */
    private fun stripModelWrapper(response: String): String {
        Log.d(TAG, "RAW response (${response.length} chars): ${response.take(200)}")

        // Handle literal \n and \r\n that some small models output instead of real newlines.
        // Also drop zero-width Unicode chars that make outputs look blank after trimming.
        var cleaned = response
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")

        // 1. Strip Gemma-style chat turn markers
        //    <start_of_turn>model
        // <end_of_turn>
        val startMarker = "<start_of_turn>model"
        val endMarker   = "<end_of_turn>"
        val turnStart = cleaned.indexOf(startMarker)
        if (turnStart != -1) {
            val contentStart = turnStart + startMarker.length
            val turnEnd = cleaned.indexOf(endMarker, contentStart)
            cleaned = if (turnEnd != -1) {
                cleaned.substring(contentStart, turnEnd)
            } else {
                cleaned.substring(contentStart)
            }.trim()
            Log.d(TAG, "Stripped Gemma chat wrapper")
        }

        // 2. Strip Qwen/SmolLM assistant markers
        //    <|im_start|>assistant
        // <|im_end|>
        val qwenStart = "<|im_start|>assistant"
        val qwenEnd   = "<|im_end|>"
        val qStart = cleaned.indexOf(qwenStart)
        if (qStart != -1) {
            val contentStart = qStart + qwenStart.length
            val qEnd = cleaned.indexOf(qwenEnd, contentStart)
            cleaned = if (qEnd != -1) {
                cleaned.substring(contentStart, qEnd)
            } else {
                cleaned.substring(contentStart)
            }.trim()
            Log.d(TAG, "Stripped Qwen chat wrapper")
        }

        // 3. Strip prompt echo — if the model repeated instruction text,
        //    jump to the first structured output line.
        val echoMarkers = listOf(
            "Do not invent concepts not in the text.",
            "Do not copy this instruction.",
            "Output only those lines.",
            "Output only FRONT and BACK lines.",
            "Output only CONCEPT, FRONT, and BACK lines.",
            "Extract up to 10 specific concepts"
        )
        for (marker in echoMarkers) {
            val idx = cleaned.lastIndexOf(marker)
            if (idx != -1) {
                val after = cleaned.substring(idx + marker.length)
                // Jump to the first known structural keyword
                val structuralKeywords = listOf("CONCEPT:", "FRONT:", "QUESTION 1", "QUESTION1")
                for (kw in structuralKeywords) {
                    val kwIdx = after.indexOf(kw)
                    if (kwIdx != -1) {
                        cleaned = after.substring(kwIdx).trim()
                        Log.d(TAG, "Stripped prompt echo at marker: '${marker.take(30)}'")
                        break
                    }
                }
                break
            }
        }

        // 4. Strip markdown code fences (```...```)
        if (cleaned.startsWith("```")) {
            val fenceEnd = cleaned.indexOf("```", 3)
            cleaned = if (fenceEnd != -1) cleaned.substring(3, fenceEnd).trim()
            else cleaned.removePrefix("```").trim()
            // Remove optional language tag (```kotlin, ```text, etc.)
            val firstNewline = cleaned.indexOf('\n')
            if (firstNewline != -1 && firstNewline < 20) {
                cleaned = cleaned.substring(firstNewline).trim()
            }
            Log.d(TAG, "Stripped markdown code fence")
        }

        cleaned = cleaned
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()

        Log.d(TAG, "CLEANED response (${cleaned.length} chars): ${cleaned.take(200)}")
        return cleaned
    }

    // Keep old name as alias so concept parser still works unchanged
    private fun stripPromptEcho(response: String) = stripModelWrapper(response)

    private fun parseGroundedAnswer(response: String): GroundedAnswer {
        val answerLine = response
            .lineSequence()
            .firstOrNull { it.trim().startsWith("ANSWER:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()

        val confidence = response
            .lineSequence()
            .firstOrNull { it.trim().startsWith("CONFIDENCE:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.uppercase()
            ?.takeIf { it in setOf("HIGH", "MEDIUM", "LOW") }
            ?: "LOW"

        val citationLine = response
            .lineSequence()
            .firstOrNull { it.trim().startsWith("CITATIONS:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()

        val citationIndexes = if (citationLine.equals("NONE", ignoreCase = true)) {
            emptyList()
        } else {
            Regex("\\d+")
                .findAll(citationLine)
                .mapNotNull { it.value.toIntOrNull() }
                .toList()
        }

        return GroundedAnswer(
            answer = answerLine
                ?.takeIf { it.isNotBlank() }
                ?: response.trim().lines().firstOrNull().orEmpty(),
            confidence = confidence,
            citationIndexes = citationIndexes
        )
    }

    private fun parseConceptsRobust(response: String): List<ExtractedConcept> {
        val cleaned = stripPromptEcho(response)
        if (cleaned.contains("NO_CONCEPTS", ignoreCase = true)) return emptyList()

        // Try structured parsing first (CONCEPT: / DESCRIPTION: format)
        val structuredConcepts = parseConceptsStructured(cleaned)
        if (structuredConcepts.isNotEmpty()) {
            return structuredConcepts
        }

        // Fallback: parse natural language format when model ignores formatting instructions
        Log.d(TAG, "Structured parsing failed, trying natural language fallback")
        return parseConceptsNaturalLanguage(cleaned)
    }

    private fun parseConceptsStructured(response: String): List<ExtractedConcept> {
        val concepts = mutableListOf<ExtractedConcept>()
        var currentName: String? = null

        val lines = response.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Handle cases where the first CONCEPT: label is missing (due to prompt primer)
        var startIndex = 0
        if (lines.isNotEmpty() &&
            !lines[0].startsWith("CONCEPT:", ignoreCase = true) &&
            !lines[0].startsWith("DESCRIPTION:", ignoreCase = true) &&
            lines.size > 1 &&
            lines[1].startsWith("DESCRIPTION:", ignoreCase = true)) {
            currentName = lines[0].replace("*", "").trim()
            startIndex = 1
        }

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val cleanLine = line.replace("*", "").trim()

            when {
                cleanLine.startsWith("CONCEPT:", ignoreCase = true) -> {
                    // If we had a previous concept with no description, flush it now
                    if (currentName != null) {
                        if (currentName!!.isNotBlank() &&
                            currentName!!.lowercase() !in LLMPromptTemplates.KNOWN_EXAMPLE_CONCEPT_NAMES) {
                            concepts.add(ExtractedConcept(currentName!!, currentName!!))
                        }
                    }
                    currentName = cleanLine
                        .substringAfter(":")
                        .trim()
                        .removePrefix("[").removeSuffix("]")
                        .trim()
                    if (currentName!!.lowercase() in LLMPromptTemplates.KNOWN_EXAMPLE_CONCEPT_NAMES) {
                        currentName = null
                    }
                }

                cleanLine.startsWith("DESCRIPTION:", ignoreCase = true) && currentName != null -> {
                    val description = cleanLine
                        .substringAfter(":")
                        .trim()
                        .removePrefix("[").removeSuffix("]")
                        .trim()
                    val descToUse = if (description.length > 10) description else currentName!!
                    if (currentName!!.isNotBlank()) {
                        concepts.add(ExtractedConcept(currentName!!, descToUse))
                    }
                    currentName = null
                }
                
                // If a line doesn't have a label but is followed by one with DESCRIPTION:
                i + 1 < lines.size && lines[i+1].startsWith("DESCRIPTION:", ignoreCase = true) -> {
                    currentName = cleanLine
                }
            }
        }

        // Flush trailing concept
        if (currentName != null &&
            currentName!!.isNotBlank() &&
            currentName!!.lowercase() !in LLMPromptTemplates.KNOWN_EXAMPLE_CONCEPT_NAMES) {
            concepts.add(ExtractedConcept(currentName!!, currentName!!))
        }

        return concepts
    }

    private fun parseConceptsNaturalLanguage(response: String): List<ExtractedConcept> {
        val concepts = mutableListOf<ExtractedConcept>()
        val seenConcepts = mutableSetOf<String>()

        // Helper to add if valid and unique
        fun addIfValid(name: String, description: String) {
            val cleanName = name.replace("*", "").trim()
            val cleanDesc = description.replace("*", "").trim()
            if (cleanName.isNotBlank() && 
                cleanDesc.length > 5 &&
                cleanName.lowercase() !in seenConcepts &&
                !cleanName.lowercase().startsWith("[") &&
                !cleanDesc.lowercase().contains("do not") &&
                !cleanDesc.lowercase().contains("placeholder")) {
                concepts.add(ExtractedConcept(cleanName, cleanDesc))
                seenConcepts.add(cleanName.lowercase())
            }
        }

        // Pattern 1: Bold pattern: **ConceptName** is description or **ConceptName**: description
        val boldPattern = Regex("\\*\\*([^*]+)\\*\\*[:\\s]+([^\\n]+)")
        boldPattern.findAll(response).forEach { match ->
            addIfValid(match.groupValues[1], match.groupValues[2])
        }

        // Pattern 2: "Concept is description" pattern (handling bullet points)
        // Matches: "* Sociology is the study of..." or "Medical Sociology is a subdiscipline..."
        val isPattern = Regex("(?m)^\\s*[*-]?\\s*([A-Z][^:.\\n]+?)\\s+(?:is|refers to|refers specifically to)\\s+([^\\n]+)")
        isPattern.findAll(response).forEach { match ->
            addIfValid(match.groupValues[1], match.groupValues[2])
        }

        // Pattern 3: Numbered or bullet concepts: "1. Name: Description" or "* Name - Description"
        val bulletPattern = Regex("(?m)^\\s*(?:\\d+\\.|-|\\*)\\s*([A-Z][^:.\\n]+)[:.-]\\s*([^\\n]+)")
        bulletPattern.findAll(response).forEach { match ->
            addIfValid(match.groupValues[1], match.groupValues[2])
        }

        Log.d(TAG, "Natural language fallback extracted ${concepts.size} concepts: ${concepts.map { it.name }}")
        return concepts
    }

    private fun parseFlashcardsRobust(
        response: String,
        conceptName: String? = null,
        conceptDescription: String? = null
    ): List<GeneratedFlashcard> {
        val cleaned = stripModelWrapper(response)
        val flashcards = mutableListOf<GeneratedFlashcard>()

        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
        var currentFront: String? = null
        var currentBack: String? = null

        fun flushIfComplete() {
            val front = currentFront?.trim().orEmpty()
            var back = currentBack?.trim().orEmpty()
            if (back.isBlank()) {
                back = conceptDescription?.takeIf { it.isNotBlank() }?.trim().orEmpty()
            }
            if (front.isNotBlank() && back.length > 4) {
                flashcards.add(GeneratedFlashcard(front, back))
            }
            currentFront = null
            currentBack = null
        }

        for (line in lines) {
            val cleanLine = line.replace("*", "").trim()
            when {
                cleanLine.startsWith("FRONT:", ignoreCase = true) -> {
                    if (!currentFront.isNullOrBlank() || !currentBack.isNullOrBlank()) {
                        flushIfComplete()
                    }
                    currentFront = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                }
                cleanLine.startsWith("BACK:", ignoreCase = true) -> {
                    val backLine = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                    if (currentFront.isNullOrBlank() && !conceptName.isNullOrBlank()) {
                        currentFront = "What is $conceptName?"
                    }
                    currentBack = backLine
                }
                currentBack != null -> {
                    // Multiline BACK continuation.
                    currentBack = (currentBack + " " + cleanLine).trim()
                }
                currentFront == null && cleanLine.endsWith("?") -> {
                    // Some outputs omit FRONT: label but still emit a question.
                    currentFront = cleanLine
                }
            }
        }

        flushIfComplete()
        return flashcards
    }

    private fun fillToTargetWithFallback(
        cards: List<GeneratedFlashcard>,
        conceptName: String,
        conceptDescription: String,
        targetCount: Int,
        validate: Boolean
    ): List<GeneratedFlashcard> {
        if (cards.size >= targetCount) return cards.distinctBy { it.front.lowercase().trim() }

        val base = cards.toMutableList()
        val fallback = synthesizeFallbackFlashcards(
            conceptName = conceptName,
            conceptDescription = conceptDescription,
            count = targetCount - base.size
        )
        val merged = (base + fallback).distinctBy { it.front.lowercase().trim() }

        val final = if (validate) flashcardValidator.filterValid(merged) else merged
        if (final.size > cards.size) {
            Log.d(TAG, "║ Fallback filled ${final.size - cards.size} missing flashcards for '$conceptName'")
        }
        return final.take(targetCount)
    }

    private fun synthesizeFallbackFlashcards(
        conceptName: String,
        conceptDescription: String,
        count: Int
    ): List<GeneratedFlashcard> {
        if (count <= 0) return emptyList()

        val desc = conceptDescription.trim().ifBlank {
            "$conceptName is an important concept from your study material."
        }
        val sentences = desc
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 10 }

        val fronts = listOf(
            "What is $conceptName?",
            "Why is $conceptName important?",
            "What is one key idea in $conceptName?",
            "How would you explain $conceptName in one sentence?",
            "What should you remember about $conceptName?"
        )

        return (0 until count).map { i ->
            val front = fronts[i % fronts.size]
            val back = sentences.getOrElse(i % maxOf(sentences.size, 1)) { desc }
            GeneratedFlashcard(front, back)
        }
    }

    private fun parseFlashcardsWithConceptIndex(
        response: String,
        conceptCount: Int
    ): List<Pair<Int, GeneratedFlashcard>> {
        val result = mutableListOf<Pair<Int, GeneratedFlashcard>>()
        var currentConceptIndex = 1
        var conceptTagSeen = false
        var currentFront: String? = null
        var currentBack: String? = null
        var cardCount = 0

        fun flushIndexed() {
            val front = currentFront?.trim().orEmpty()
            val back = currentBack?.trim().orEmpty()
            if (front.isNotBlank() && back.length > 4) {
                result.add(Pair(currentConceptIndex, GeneratedFlashcard(front, back)))
                cardCount++
                if (!conceptTagSeen && conceptCount > 1) {
                    currentConceptIndex = (cardCount % conceptCount) + 1
                }
            }
            currentFront = null
            currentBack = null
            conceptTagSeen = false
        }

        for (line in stripModelWrapper(response).lines()) {
            val cleanLine = line.replace("*", "").trim()
            if (cleanLine.isBlank()) continue
            when {
                cleanLine.startsWith("CONCEPT:", ignoreCase = true) -> {
                    if (!currentFront.isNullOrBlank() || !currentBack.isNullOrBlank()) {
                        flushIndexed()
                    }
                    val parsed = cleanLine.substringAfter(":").trim().toIntOrNull()
                    if (parsed != null && parsed in 1..conceptCount) {
                        currentConceptIndex = parsed
                        conceptTagSeen = true
                    }
                }
                cleanLine.startsWith("FRONT:", ignoreCase = true) -> {
                    if (!currentFront.isNullOrBlank() || !currentBack.isNullOrBlank()) {
                        flushIndexed()
                    }
                    currentFront = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                }
                cleanLine.startsWith("BACK:", ignoreCase = true) -> {
                    currentBack = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                }
                currentBack != null -> {
                    currentBack = (currentBack + " " + cleanLine).trim()
                }
            }
        }
        flushIndexed()
        return result
    }

    private fun parseQuizFromResponse(response: String): List<GeneratedQuizQuestion> {
        val cleaned = stripModelWrapper(response)
        val questions = mutableListOf<GeneratedQuizQuestion>()
        val seenTexts = mutableSetOf<String>()

        // Texts that indicate the model echoed an example rather than generating real content
        val placeholderPatterns = listOf(
            Regex("""^\[.*]$"""),                          // entire text is [placeholder]
            Regex("""^ask about (one of|a different)"""),  // echoed our prompt instructions
            Regex("""^question (specific to|about)""")
        )

        // Options that are clearly placeholder text echoed from the prompt format example
        fun isPlaceholderOption(opt: String): Boolean {
            val l = opt.trim().lowercase()
            return l.startsWith("[") || l.endsWith("]") ||
                    l.contains("wrong answer") || l.contains("correct answer") ||
                    l.contains("choice") || l.contains("option")
        }

        val blocks = cleaned.split(Regex("QUESTION\\s*\\d+", RegexOption.IGNORE_CASE))

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
                        if (opt.isNotEmpty() && !isPlaceholderOption(opt)) options.add(opt)
                    }
                    trimmed.startsWith("CORRECT:", ignoreCase = true) -> correct = trimmed.substringAfter(":").trim()
                }
            }

            // Skip blank or placeholder question text
            if (text.isBlank()) continue
            val lowerText = text.trim().lowercase()
            if (placeholderPatterns.any { it.containsMatchIn(lowerText) }) continue
            if (lowerText in seenTexts) continue
            seenTexts.add(lowerText)

            // Reclassify MCQ→TRUE_FALSE if model only produced True/False options despite the prompt
            val trueFalseValues = setOf("true", "false")
            val allTrueFalse = options.isNotEmpty() &&
                    options.all { it.trim().lowercase() in trueFalseValues }
            if (type.equals("MCQ", ignoreCase = true) && allTrueFalse) {
                type = "TRUE_FALSE"
                val deduped = mutableListOf<String>()
                if (options.any { it.trim().lowercase() == "true" }) deduped.add("True")
                if (options.any { it.trim().lowercase() == "false" }) deduped.add("False")
                options.clear()
                options.addAll(deduped)
            }

            // Discard MCQ questions that ended up with fewer than 2 real options after filtering
            if (type.equals("MCQ", ignoreCase = true) && options.size < 2) continue

            val normalizedCorrect = normalizeCorrectAnswer(correct, options)
            questions.add(GeneratedQuizQuestion(type, text, options.ifEmpty { null }, normalizedCorrect))
        }
        return questions
    }

    private fun normalizeCorrectAnswer(correct: String, options: List<String>): String {
        val trimmed = correct.trim()
        if (trimmed.length == 1 && trimmed.uppercase() in listOf("A", "B", "C", "D")) return trimmed.uppercase()
        if (trimmed.equals("true",  ignoreCase = true)) return "A"
        if (trimmed.equals("false", ignoreCase = true)) return "B"

        val matchIndex = options.indexOfFirst { opt ->
            opt.trim().equals(trimmed, ignoreCase = true) ||
                    opt.trim().lowercase().contains(trimmed.lowercase()) ||
                    trimmed.lowercase().contains(opt.trim().lowercase())
        }
        if (matchIndex >= 0) return ('A' + matchIndex).toString()
        return trimmed.uppercase()
    }

    private fun deduplicateConcepts(concepts: List<ExtractedConcept>): List<ExtractedConcept> {
        return concepts.distinctBy { it.name.lowercase().trim() }
    }

    suspend fun chat(message: String): Result<String> = modelManager.generate(message)
}

data class GroundedContextChunk(
    val index: Int,
    val materialTitle: String,
    val chunkIndex: Int,
    val text: String
)

data class GroundedAnswer(
    val answer: String,
    val confidence: String,
    val citationIndexes: List<Int>
)



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
    /** Emitted after each batch completes with new unique concepts from that batch only. */
    data class BatchComplete(val newConcepts: List<ExtractedConcept>) : ConceptExtractionState()
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