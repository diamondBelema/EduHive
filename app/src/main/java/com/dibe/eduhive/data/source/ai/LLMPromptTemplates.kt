package com.dibe.eduhive.data.source.ai

/**
 * Centralized prompt templates for each LLM generation stage.
 *
 * Keeping prompts here makes them easy to tune and test independently.
 */
object LLMPromptTemplates {

    /**
     * Concept extraction prompt for a single page/block of text.
     */
    fun conceptExtraction(text: String, hiveContext: String = ""): String {
        val contextLine = if (hiveContext.isNotEmpty()) "Goal: $hiveContext\n" else ""
        return """
Extract important educational concepts from the following text.
${contextLine}Text:
$text

Output format:
CONCEPT: [Simple Name]
DESCRIPTION: [Clear Explanation]

Provide 3 to 8 concepts.
        """.trimIndent()
    }

    /**
     * Flashcard draft generation for a single concept.
     */
    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count flashcards for: $conceptName
Information: $conceptDescription

Rules:
- Each question must end with a "?"
- Answers must be concise and specific
- Avoid vague pronouns like "it" or "this"

Format:
FRONT: [Short Question ending with ?]
BACK: [Concise Answer]
        """.trimIndent()
    }

    /**
     * Batched flashcard generation for multiple concepts.
     * Groups 3-5 concepts to improve global context and reduce duplication.
     * Each flashcard is attributed to its source concept via a CONCEPT tag.
     */
    fun flashcardBatch(
        concepts: List<Pair<String, String>>,
        countPerConcept: Int
    ): String {
        val conceptsBlock = concepts.mapIndexed { index, (name, desc) ->
            "Concept ${index + 1}: $name\nDescription: $desc"
        }.joinToString("\n---\n")
        val total = concepts.size * countPerConcept
        return """
Generate $total flashcards across these ${concepts.size} concepts:

$conceptsBlock

Rules:
- No duplicate questions across concepts
- Each question must end with a "?"
- Answers must be concise and specific
- Avoid vague pronouns like "it" or "this"
- Vary question types (definition, mechanism, example, comparison)
- Tag each flashcard with its source concept number

Format (repeat for each flashcard):
CONCEPT: [concept number, e.g. 1]
FRONT: [Short Question ending with ?]
BACK: [Concise Answer]
        """.trimIndent()
    }

    /**
     * Refinement prompt for improving a set of draft flashcards.
     */
    fun flashcardRefinement(draftFlashcards: List<GeneratedFlashcard>): String {
        val cardsBlock = draftFlashcards.joinToString("\n") { card ->
            "FRONT: ${card.front}\nBACK: ${card.back}"
        }
        return """
Improve these flashcards:

$cardsBlock

Rules:
- Fix vague questions (make them specific)
- Ensure each question ends with "?"
- Make answers concise and accurate
- Remove vague pronouns ("it", "this", "that")
- Return the same number of flashcards in the same format

Format:
FRONT: [Improved Question ending with ?]
BACK: [Concise Answer]
        """.trimIndent()
    }

    /**
     * Quiz question generation prompt.
     */
    fun quizGeneration(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count quiz questions about: $conceptName
Description: $conceptDescription

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

    /**
     * Prompt mutation variants for retry logic.
     * Each variant adds a specific instruction to improve output quality.
     */
    fun mutate(basePrompt: String, attempt: Int): String {
        val mutations = listOf(
            "",
            "\nBe more specific and avoid vague answers.",
            "\nFocus on definitions and mechanisms.",
            "\nAvoid vague answers. Use precise terminology."
        )
        val suffix = mutations.getOrElse(attempt) { mutations.last() }
        return basePrompt + suffix
    }
}
