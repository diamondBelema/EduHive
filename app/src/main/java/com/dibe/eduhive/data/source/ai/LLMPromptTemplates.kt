package com.dibe.eduhive.data.source.ai

/**
 * Prompt templates for each generation task.
 *
 * Design principles for small on-device models (135M–1B parameters):
 *
 * 1. Examples ARE the instruction.
 *    Small models pattern-match to concrete examples far better than they follow
 *    prose rules. Show exactly what you want — twice. Never use placeholders like
 *    "name" because the model will output the literal token.
 *
 * 2. One task per prompt.
 *    Never ask the model to analyse AND format in the same call. The prompt either
 *    extracts concepts OR generates flashcards — never both.
 *
 * 3. Ask for fewer items, get better items.
 *    3 concepts per page is reliable across all model sizes. 5 is only reliable
 *    on Gemma 1B. Aggregate across pages to get total concept count.
 *
 * 4. Keep the instruction block short.
 *    Every character of instruction text competes with response tokens in the
 *    same context window. Aim for < 120 tokens of instructions.
 *
 * 5. Low temperature handles format, not the prompt.
 *    Do not add "strictly follow the format" or "do not deviate" — those phrases
 *    waste tokens. Set temperature=0.2 in GenerationConfig instead.
 *
 * 6. End with the last example line, not an instruction.
 *    The model continues from the last line it sees. If the last line is
 *    "Now extract 3 concepts:" the model may echo that. If the last line is
 *    "DESCRIPTION: ..." the model is primed to start the next CONCEPT: block.
 */
object LLMPromptTemplates {

    // ─── Known example values the parser must ignore ────────────────────────
    // These are the concept names used in our prompt examples. If the model
    // echoes them back, the parser will skip them as known-example noise.
    val KNOWN_EXAMPLE_CONCEPT_NAMES = setOf(
        "example concept a",
        "example concept b"
    )

    // ────────────────────────────────────────────────────────────────────────
    // Concept extraction
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Extract 3 concepts from a single cleaned page of text.
     *
     * Why 3 and not 5:
     * - 3 concept pairs (~90 tokens output) comfortably fit in the response
     *   budget left after the prompt + page text.
     * - If a document has 10 pages and each yields 3 concepts, deduplication
     *   produces 15–25 unique concepts — more than enough.
     * - Asking for 5 from a 135M model on a token-tight budget produces 3 good
     *   ones and 2 garbage ones. Better to ask for 3 and get 3 good ones.
     *
     * Why two examples and no placeholders:
     * - One example gives the model a pattern to copy once.
     * - Two examples confirms the pattern — the model sees it is not a one-off.
         * - Placeholders such as "name" and "description" get copied verbatim by small models.
     *
     * The prompt ends immediately after the second example's DESCRIPTION line.
     * This primes the model to continue writing a third CONCEPT: block rather
     * than echoing the instruction header.
     */
    fun conceptExtraction(text: String, hiveContext: String = ""): String {
        val contextLine = if (hiveContext.isNotBlank()) "Subject: $hiveContext\n" else ""
        return """
Extract up to 10 specific concepts from the text below.
${contextLine}
Text:
$text

Output format:
CONCEPT: Example Concept A
DESCRIPTION: A short definition of a key idea found in the provided text.

CONCEPT: Example Concept B
DESCRIPTION: Another distinct idea from the provided text, not a repeat.
        """.trimIndent()
        // Intentionally ends here — model continues with the next CONCEPT: block
    }

    // ────────────────────────────────────────────────────────────────────────
    // Flashcard generation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Generate [count] flashcards for a single concept.
     *
     * Why count is capped externally at 3:
     * The caller (AiDataSource) should pass count=3. Three FRONT/BACK pairs
     * (~120 tokens) fit comfortably in the response budget. Passing count=5
     * risks truncation on smaller models.
     *
     * The prompt ends after the second example BACK line to prime continuation.
     */
    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count flashcards for: $conceptName
Context: $conceptDescription

Output format:
FRONT: What is the main idea of "$conceptName"?
BACK: The main idea is: [one clear sentence based only on the provided concept context].

FRONT: Why is "$conceptName" important?
BACK: It is important because: [one specific reason grounded in the provided context].
        """.trimIndent()
    }

    /**
     * Batched flashcard generation for multiple concepts.
     *
     * Used when generating across several concepts at once. Each card is tagged
     * with its concept number so the parser can route cards to the right concept.
     *
     * Keep concepts.size <= 3 per batch call for small models. The caller
     * (AiDataSource.generateFlashcardsBatch) already enforces BATCH_SIZE = 3.
     */
    fun flashcardBatch(
        concepts: List<Pair<String, String>>,
        countPerConcept: Int
    ): String {
        val conceptsBlock = concepts.mapIndexed { i, (name, desc) ->
            "${i + 1}. $name: $desc"
        }.joinToString("\n")

        return """
Create ${countPerConcept} flashcards for each of these ${concepts.size} concepts:
$conceptsBlock

Tag each card with its concept number. Output format:
CONCEPT: 1
FRONT: What is the main idea of concept 1?
BACK: One concise answer derived from concept 1.

CONCEPT: 2
FRONT: Why does concept 2 matter?
BACK: One concise reason derived from concept 2.
        """.trimIndent()
    }

    /**
     * Refinement prompt — improve a set of low-quality draft flashcards.
     *
     * Only called when pass rate < MIN_PASS_RATE after the draft attempt.
     * We show one example of a bad card being improved so the model
     * understands what "improve" means in context.
     */
    fun flashcardRefinement(draftFlashcards: List<GeneratedFlashcard>): String {
        val cardsBlock = draftFlashcards.joinToString("\n\n") { card ->
            "FRONT: ${card.front}\nBACK: ${card.back}"
        }
        return """
Rewrite these flashcards. Make each question specific and each answer a complete sentence.
Keep the same FRONT/BACK format and the same number of cards.

$cardsBlock
        """.trimIndent()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Quiz generation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Generate quiz questions for a single concept.
     *
     * Why we show both MCQ and TRUE_FALSE examples:
     * Without seeing both formats, small models default to producing only MCQ.
     * Showing one of each primes them to alternate — which gives more variety
     * and a better quiz experience.
     *
     * The prompt ends after the second complete example block (TRUE_FALSE),
     * which primes the model to continue writing QUESTION 1.
     */
    fun quizGeneration(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count quiz questions about: $conceptName
Context: $conceptDescription

MCQ format:
QUESTION 1
TYPE: MCQ
TEXT: Which statement best describes $conceptName?
OPTION A: Answer A
OPTION B: Answer B
OPTION C: Answer C
OPTION D: Answer D
CORRECT: A

TRUE/FALSE format:
QUESTION 2
TYPE: TRUE_FALSE
TEXT: This statement about $conceptName is correct.
OPTION A: True
OPTION B: False
CORRECT: A
        """.trimIndent()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Prompt mutation for retry logic
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Appends a short re-focusing suffix to a base prompt on retry.
     *
     * Attempt 0: no mutation (base prompt used as-is)
     * Attempt 1: nudge toward definitions
     * Attempt 2: nudge toward brevity
     *
     * We only go to attempt 2 max — more attempts rarely improve quality and
     * burn time on an on-device model. The caller handles the attempt limit via
     * GenerationConfig.retryAttempts.
     */
    fun mutate(basePrompt: String, attempt: Int): String {
        val suffix = when (attempt) {
            0    -> ""
            1    -> "\nFocus on definitions and key terms."
            else -> "\nKeep answers short. One sentence per answer."
        }
        return basePrompt + suffix
    }
}