package com.dibe.eduhive.data.source.ai

/**
 * Prompt templates for each generation task.
 * 
 * DESIGN UPDATE: Enhanced for variety and non-repetitive logic.
 */
object LLMPromptTemplates {

    val KNOWN_EXAMPLE_CONCEPT_NAMES = setOf(
        "example concept a",
        "example concept b"
    )

    fun conceptExtraction(text: String, hiveContext: String = ""): String {
        val contextLine = if (hiveContext.isNotBlank()) "Subject: $hiveContext\n" else ""
        return """
Extract up to 10 unique and distinct concepts from the text below.
Avoid overlapping or repetitive ideas. Focus on core terminology and logic.
${contextLine}
Text:
$text

Output format:
CONCEPT: Example Concept A
DESCRIPTION: A short definition of a key idea found in the provided text.

CONCEPT: Example Concept B
DESCRIPTION: Another distinct idea from the provided text, not a repeat.
        """.trimIndent()
    }

    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count high-quality flashcards for: $conceptName
Context: $conceptDescription

Each card must cover a DIFFERENT aspect of the concept (e.g., definition, use-case, counter-example).
Do not repeat the same question in different words.

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
Create $count distinct and challenging quiz questions about: $conceptName
Context: $conceptDescription

VARIETY IS KEY:
- Use different question types (MCQ, TRUE_FALSE).
- Ask about different details (definitions, relationships, implications).
- DO NOT repeat the same fact across multiple questions.
- Distractors (wrong options) should be plausible but clearly incorrect.

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

    fun mutate(basePrompt: String, attempt: Int): String {
        val suffix = when (attempt) {
            0    -> ""
            1    -> "\nFocus on high-level architecture and abstract logic."
            else -> "\nFocus on granular details and specific edge cases."
        }
        return basePrompt + suffix
    }
}