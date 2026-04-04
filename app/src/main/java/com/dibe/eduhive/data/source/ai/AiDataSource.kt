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
         * Capping at 5 per batch means even a fully looping response contributes at most
         * 5 concepts, and duplicates are filtered against the global seenNames set.
         */
        private const val MAX_CONCEPTS_PER_BATCH = 5

        /**
         * Max flashcard facts to inject into a quiz prompt.
         */
        private const val MAX_FACTS_PER_QUIZ = 5
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
                Log.d(TAG, "RAW RESPONSE BATCH $index:\n$response")
                val batchConcepts = parseConceptsRobust(response)

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

        // Fast-track: single attempt, accept output as-is (no refinement pass)
        if (skipValidation) {
            val result = modelManager.generate(basePrompt)
            result.onSuccess { response ->
                val parsed = parseFlashcardsRobust(response)
                return ValidationResult(parsed, 0)
            }.onFailure { e ->
                Log.w(TAG, "Fast-track generation failed for \"$conceptName\": ${e.message}")
            }
            return ValidationResult(emptyList(), 0)
        }

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

    private suspend fun ensureModelLoaded(config: GenerationConfig = GenerationConfig()): Boolean {
        // Always call loadModel — it checks internally whether a reload is actually needed
        // (same modelId + same maxTokens = no reload, just updates session config).
        // The old guard "if isModelLoaded return true" bypassed the config entirely,
        // meaning maxTokens was never updated between tasks (concept=800, flashcard=1280).
        val modelId = modelPreferences.getActiveModel() ?: return false
        return modelManager.loadModel(modelId, config).isSuccess
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsers
    // ─────────────────────────────────────────────────────────────────────────

    private fun stripPromptEcho(response: String): String {
        // Strategy 1: OUTPUT_START/END delimiters (old prompt format)
        val blockStart = response.indexOf("OUTPUT_START")
        val blockEnd = response.indexOf("OUTPUT_END")
        if (blockStart != -1 && blockEnd > blockStart) {
            return response.substring(blockStart + "OUTPUT_START".length, blockEnd).trim()
        }

        // Strategy 2: find the last known example DESCRIPTION line and take everything after
        // This strips any prompt echo when the model repeats our example before generating
        val echoMarkers = listOf(
            "DESCRIPTION: Passive diffusion of water molecules across a selectively permeable membrane down a concentration gradient.",
            "DESCRIPTION: Increase in muscle fiber size caused by progressive resistance training over time.",
            "DESCRIPTION: A short definition of a key idea found in the provided text.",
            "DESCRIPTION: Another distinct idea from the provided text, not a repeat.",
            "Output format:",
            "Extract up to 10 specific concepts",
            "Output:"
        )
        for (marker in echoMarkers) {
            val idx = response.lastIndexOf(marker)
            if (idx != -1) {
                val afterMarker = response.substring(idx + marker.length)
                val nextConcept = afterMarker.indexOf("CONCEPT:")
                if (nextConcept != -1) {
                    return afterMarker.substring(nextConcept).trim()
                }
            }
        }
        return response
    }

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

        val concepts = mutableListOf<ExtractedConcept>()
        var currentName: String? = null

        val lines = cleaned.lines()
        lines.forEachIndexed { idx, line ->
            val cleanLine = line.replace("*", "").trim()

            when {
                cleanLine.startsWith("CONCEPT:", ignoreCase = true) -> {
                    // If we had a previous concept with no description, flush it now
                    // before overwriting currentName
                    if (currentName != null) {
                        if (currentName!!.isNotBlank() &&
                            currentName!!.lowercase() !in LLMPromptTemplates.KNOWN_EXAMPLE_CONCEPT_NAMES) {
                            // Use name as description fallback — better than discarding
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
            }
        }

        // Flush any trailing concept that had no description
        if (currentName != null &&
            currentName!!.isNotBlank() &&
            currentName!!.lowercase() !in LLMPromptTemplates.KNOWN_EXAMPLE_CONCEPT_NAMES) {
            concepts.add(ExtractedConcept(currentName!!, currentName!!))
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
                }
                cleanLine.startsWith("BACK:", ignoreCase = true) && currentFront != null -> {
                    val back = cleanLine
                        .substringAfter(":").trim()
                        .removePrefix("[").removeSuffix("]").trim()
                    // Skip if back is a bracket placeholder the model echoed literally
                    val isPlaceholder = back.startsWith("[") || back.endsWith("]") ||
                            back.lowercase().startsWith("brief ") ||
                            back.lowercase().startsWith("concise ") ||
                            back.lowercase().contains("explanation of the") ||
                            back.lowercase().contains("description of the") ||
                            back.lowercase().contains("mission statement") ||
                            back.lowercase().startsWith("a brief") ||
                            back.lowercase().startsWith("an explanation")
                    if (currentFront!!.isNotBlank() && back.length > 10 && !isPlaceholder) {
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
        val seenTexts = mutableSetOf<String>()
        val exampleTexts = setOf(
            "[question specific to",
            "[true or false statement specific to"
        )

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

            if (text.isBlank()) continue
            val lowerText = text.trim().lowercase()
            if (lowerText in exampleTexts) continue
            if (exampleTexts.any { lowerText.startsWith(it) }) continue
            if (Regex("""^\[.*]$""").matches(lowerText)) continue
            val key = lowerText
            if (key in seenTexts) continue
            seenTexts.add(key)

            // If the model generated MCQ but filled every option with True/False variants,
            // reclassify as TRUE_FALSE (2-option) and deduplicate.
            val trueFalseOptions = setOf("true", "false")
            val allOptionsTrueFalse = options.isNotEmpty() &&
                    options.all { it.trim().lowercase() in trueFalseOptions }
            if (type.equals("MCQ", ignoreCase = true) && allOptionsTrueFalse) {
                type = "TRUE_FALSE"
                // Keep only the canonical True/False pair
                val dedupedOptions = mutableListOf<String>()
                if (options.any { it.trim().lowercase() == "true" }) dedupedOptions.add("True")
                if (options.any { it.trim().lowercase() == "false" }) dedupedOptions.add("False")
                options.clear()
                options.addAll(dedupedOptions)
            }

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