package com.dibe.eduhive.data.source.ai

/**
 * Prompt templates for each generation task.
 */
object LLMPromptTemplates {

    val KNOWN_EXAMPLE_CONCEPT_NAMES = setOf(
        "muscle hypertrophy",
        "osmosis"
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
        // Reformat facts from "Q: x | A: y" into plain statements so the model
        // does not pattern-match Q/A style and default to True/False answers.
        val knowledgeBlock = if (facts.isNotEmpty()) {
            val statements = facts.map { fact ->
                val parts = fact.split("|")
                if (parts.size == 2) parts[1].removePrefix("A:").removePrefix(" A:").trim()
                else fact.trim()
            }
            buildString {
                appendLine("Key facts about $conceptName:")
                statements.forEach { s -> appendLine("- $s") }
            }.trimEnd()
        } else {
            "Topic: $conceptName\nDescription: $conceptDescription"
        }

        return """
$knowledgeBlock

Write $count multiple-choice questions about $conceptName.
Each question must have 4 answer options (A, B, C, D) — no True/False.

QUESTION 1
TYPE: MCQ
TEXT: [ask about one of the facts above]
OPTION A: [wrong answer]
OPTION B: [correct answer]
OPTION C: [wrong answer]
OPTION D: [wrong answer]
CORRECT: B

QUESTION 2
TYPE: MCQ
TEXT: [ask about a different fact]
OPTION A: [correct answer]
OPTION B: [wrong answer]
OPTION C: [wrong answer]
OPTION D: [wrong answer]
CORRECT: A

Now write $count questions about $conceptName:
QUESTION 1
TYPE: MCQ
TEXT:""".trimIndent()
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

        return """Use only the text below to answer. Be concise.

$chunkBlock

Q: $question
CITATIONS: [list chunk numbers used, or NONE]
CONFIDENCE: [HIGH, MEDIUM, or LOW]
ANSWER:""".trimIndent()
    }
}