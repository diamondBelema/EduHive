package com.dibe.eduhive.data.source.ai

/**
 * Prompt templates for each generation task.
 */
object LLMPromptTemplates {

    val KNOWN_EXAMPLE_CONCEPT_NAMES = setOf(
        "muscle hypertrophy",
        "osmosis"
    )

    /**
     * Lowercased question texts used as format examples in [quizGeneration].
     * Stored here so [parseQuizFromResponse] can filter them out without duplicating the strings.
     */
    val QUIZ_EXAMPLE_QUESTION_TEXTS = setOf(
        "during osmosis, which direction does water move?",
        "osmosis requires energy from the cell to move water molecules."
    )

    // ── Concept extraction ────────────────────────────────────────────────────

    fun conceptExtraction(text: String, hiveContext: String = ""): String {
        val contextLine = if (hiveContext.isNotBlank()) "Subject: $hiveContext\n" else ""
        return """
${contextLine}Extract as many specific, testable concepts as you can find in the text below.
Output only pairs of CONCEPT and DESCRIPTION lines. No other text.
Each DESCRIPTION must be one or more complete sentence explaining what the concept means.

Text:
$text

Output:
CONCEPT: Muscle hypertrophy
DESCRIPTION: Increase in muscle fiber size caused by progressive resistance training over time.
CONCEPT: Osmosis
DESCRIPTION: Passive diffusion of water molecules across a selectively permeable membrane down a concentration gradient.
CONCEPT:""".trimIndent()
    }


    // ── Flashcard generation ──────────────────────────────────────────────────

    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count flashcards for the concept: $conceptName
Definition: $conceptDescription

Output only FRONT and BACK pairs. No other text.
FRONT must be a specific question about $conceptName.
BACK must be a real, complete answer — not a placeholder.

CONCEPT: Osmosis
FRONT: Why does a cell placed in a hypertonic solution shrink?
BACK: Water moves out of the cell by osmosis because the external solution has a higher solute concentration, creating a concentration gradient.
FRONT: What happens when osmosis is disrupted by a damaged membrane?
BACK: The cell loses the ability to regulate water balance, causing it to either swell and burst or shrink depending on the environment.

CONCEPT: $conceptName
FRONT:""".trimIndent()
    }

    fun flashcardBatch(concepts: List<Pair<String, String>>, countPerConcept: Int): String {
        val conceptsBlock = concepts.mapIndexed { i, (name, desc) ->
            "${i + 1}. $name: $desc"
        }.joinToString("\n")

        return """
Generate $countPerConcept flashcards for each concept below.

$conceptsBlock

CONCEPT: [Number]
FRONT: [Question]
BACK: [Answer]

Rules:
- Keep FRONT specific to that concept; never generic.
- One unique angle per card (mechanism, contrast, application, failure case).
- No markdown, no bullet lists, no extra labels.""".trimIndent()
    }

    fun flashcardRefinement(draftFlashcards: List<GeneratedFlashcard>): String {
        val cardsBlock = draftFlashcards.joinToString("\n\n") { card ->
            "FRONT: ${card.front}\nBACK: ${card.back}"
        }

        return """
Rewrite these flashcards. Replace generic questions with specific ones. Keep FRONT/BACK format.

$cardsBlock

FRONT:""".trimIndent()
    }

    // ── Quiz generation ───────────────────────────────────────────────────────

    fun quizGeneration(
        conceptName: String,
        conceptDescription: String,
        facts: List<String>,
        count: Int
    ): String {
        val factsBlock = if (facts.isNotEmpty()) {
            "Facts:\n" + facts.mapIndexed { i, f -> "${i + 1}. $f" }.joinToString("\n")
        } else {
            "Context: $conceptDescription"
        }

        return """
Generate $count quiz questions about: $conceptName
$factsBlock

Output ONLY questions in this exact format. Do not repeat the examples.

QUESTION 1
TYPE: MCQ
TEXT: During osmosis, which direction does water move?
OPTION A: From low solute concentration to high solute concentration
OPTION B: From high solute concentration to low solute concentration
OPTION C: Against the concentration gradient
OPTION D: Only through active transport
CORRECT: A

QUESTION 2
TYPE: TRUE_FALSE
TEXT: Osmosis requires energy from the cell to move water molecules.
OPTION A: True
OPTION B: False
CORRECT: B

NOW GENERATE $count QUESTIONS ABOUT $conceptName:
QUESTION 1
TYPE:""".trimIndent()
    }

    fun mutate(basePrompt: String, attempt: Int): String {
        val suffix = when (attempt) {
            0 -> ""
            1 -> "\nFocus on real-world applications and practical scenarios."
            else -> "\nFocus on edge cases and subtle differences between related ideas."
        }
        return basePrompt + suffix
    }

    fun groundedChat(question: String, contextChunks: List<GroundedContextChunk>): String {
        val chunkBlock = contextChunks.joinToString("\n\n") { chunk ->
            "[${chunk.index}] ${chunk.text}"
        }

        return """
Use ONLY the numbered passages below to answer the question. Be direct and concise.
If the answer is not in the passages, say "I don't have that information in your materials."

$chunkBlock

Question: $question
Answer:""".trimIndent()
    }
}