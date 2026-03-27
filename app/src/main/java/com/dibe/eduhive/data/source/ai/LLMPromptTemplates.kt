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
Create $count high-quality flashcards for the concept below.

Concept: $conceptName
Context: $conceptDescription

STRICT RULES:
- Do NOT use generic questions like:
  "What is...", "Why is ... important?", "What is the main idea..."
- Each question must reference a SPECIFIC detail, mechanism, example, or implication.
- Each card must test a DIFFERENT cognitive angle:
  (definition, mechanism, example, comparison, application, edge case)
- Questions must NOT be reusable for other topics.
- Answers must be precise and directly derived from the context.

BAD EXAMPLES (DO NOT COPY):
FRONT: What is $conceptName?
BACK: It is...

FRONT: Why is $conceptName important?
BACK: It is important because...

GOOD EXAMPLES:
FRONT: What specific role does "$conceptName" play in [contextual mechanism]?
BACK: It functions by...

FRONT: In what situation would "$conceptName" fail or not apply?
BACK: It fails when...

FRONT: How does "$conceptName" differ from a closely related idea?
BACK: Unlike..., it...

Now generate the flashcards.

Output format:
FRONT: ...
BACK: ...
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
    fun quizGeneration(
        conceptName: String,
        conceptDescription: String,
        facts: List<String>,
        count: Int
    ): String {

        val factsBlock = if (facts.isNotEmpty()) {
            """
Write at least one question based on each of these facts:
${facts.mapIndexed { i, f -> "${i + 1}. $f" }.joinToString("\n")}
        """.trimIndent()
        } else {
            "Context: $conceptDescription"
        }

        return """
Write $count high-quality quiz questions about: $conceptName

$factsBlock

RULES:
- Use TRUE_FALSE for simple factual statements
- Use MCQ when there are multiple plausible answers
- Each question must test a DIFFERENT aspect (definition, mechanism, example, comparison, application)
- Avoid generic or obvious questions
- Questions must require understanding, not just recall
- MCQ options must be realistic and plausible (no joke or obvious throwaways)
- Include at least one tricky or misleading option in MCQs
- Do NOT repeat patterns from the examples

FORMAT EXAMPLES (follow strictly):

QUESTION 1
TYPE: TRUE_FALSE
TEXT: The French Revolution began in 1789.
OPTION A: True
OPTION B: False
CORRECT: A

QUESTION 2
TYPE: MCQ
TEXT: Which country did Napoleon Bonaparte originally come from?
OPTION A: France
OPTION B: Corsica
OPTION C: Italy
OPTION D: Spain
CORRECT: B

Now continue with QUESTION 1.
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