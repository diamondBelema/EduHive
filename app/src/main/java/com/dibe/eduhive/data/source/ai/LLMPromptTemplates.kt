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
     * Generate quiz questions grounded in existing flashcard facts.
     *
     * [facts] is a list of "Q: <front> | A: <back>" strings built from the
     * concept's existing flashcards. Each fact is a distinct piece of knowledge,
     * so each question the model produces is forced to cover something different.
     * This eliminates the duplicate-question problem that occurs when the model
     * is asked to generate 5 questions from a single one-sentence description.
     *
     * If no flashcards exist yet, [facts] is empty and we fall back to the
     * concept description, capped at count=1 by the caller.
     */
    fun quizGeneration(
        conceptName: String,
        conceptDescription: String,
        facts: List<String>,
        count: Int
    ): String {
        val factsBlock = if (facts.isNotEmpty()) {
            "Write one question based on each of these facts:\n" +
                    facts.mapIndexed { i, f -> "${i + 1}. $f" }.joinToString("\n")
        } else {
            "Context: $conceptDescription"
        }

        return """
Create $count quiz questions about: $conceptName
$factsBlock

MCQ format — options must be real answer choices, not labels:
QUESTION 1
TYPE: MCQ
TEXT: What is the primary site of ATP production in a cell?
OPTION A: The nucleus
OPTION B: The mitochondria
OPTION C: The ribosome
OPTION D: The cell membrane
CORRECT: B

TRUE_FALSE format:
QUESTION 2
TYPE: TRUE_FALSE
TEXT: The mitochondria is found only in plant cells.
OPTION A: True
OPTION B: False
CORRECT: B
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