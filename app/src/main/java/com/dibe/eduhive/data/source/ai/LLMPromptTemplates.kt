package com.dibe.eduhive.data.source.ai

/**
 * Prompt templates for each generation task.
 */
object LLMPromptTemplates {

    val KNOWN_EXAMPLE_CONCEPT_NAMES = emptySet<String>()

    // ── Concept extraction ────────────────────────────────────────────────────

    fun conceptExtraction(text: String, hiveContext: String = ""): String {
        val contextLine = if (hiveContext.isNotBlank()) "Subject: $hiveContext\n" else ""
        return """
${contextLine}TASK: Extract 5-10 key concepts from the text below.

OUTPUT FORMAT:
CONCEPT: Concept Name
DESCRIPTION: Short explanation

RULES:
- Use EXACTLY the CONCEPT: and DESCRIPTION: labels.
- One concept per pair of lines.
- No introductory text or extra commentary.
- Only from the provided text.

Text:
$text

CONCEPT:""".trimIndent()
    }


    // ── Flashcard generation ──────────────────────────────────────────────────

    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count flashcards for this concept.

You MUST output EXACTLY this repeated format:
FRONT: <question>
BACK: <answer>

Hard rules:
- Every FRONT must be followed by a BACK.
- Do not output BACK without a FRONT.
- No markdown, no bullets, no extra labels.
- If unsure, still output valid FRONT/BACK pairs.

Example:
FRONT: What is photosynthesis?
BACK: Photosynthesis is the process plants use to convert light energy into chemical energy.

CONCEPT: $conceptName
Definition: $conceptDescription

FRONT:""".trimIndent()
    }

    fun flashcardBatch(concepts: List<Pair<String, String>>, countPerConcept: Int): String {
        val conceptsBlock = concepts.mapIndexed { i, (name, desc) ->
            "${i + 1}. $name: $desc"
        }.joinToString("\n")

        return """
Generate $countPerConcept flashcards for each concept below.
Output only CONCEPT, FRONT, and BACK lines.

$conceptsBlock

Rules:
- CONCEPT: must be the concept number (1, 2, 3...).
- FRONT: specific question about that concept only.
- BACK: complete answer, one unique angle per card.
- No markdown, no bullet lists, no extra labels.

CONCEPT: 1
FRONT:""".trimIndent()
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
Format (do not copy this):
QUESTION [n]
TYPE: MCQ
TEXT: [question about $conceptName]
OPTION A: [option]
OPTION B: [option]
OPTION C: [option]
OPTION D: [option]
CORRECT: [A/B/C/D]

QUESTION 1
TYPE: MCQ
TEXT:""".trimIndent()
    }

    fun mutate(basePrompt: String, attempt: Int): String {
        val suffix = when (attempt) {
            0 -> ""
            1 -> """

Retry mode:
- Your last answer likely had missing structure.
- Output at least 2 complete FRONT/BACK pairs.
- Never output BACK alone.
- Keep answers factual and concise.
""".trimIndent()
            else -> """

Final retry mode:
- Emit strict pairs only.
- Pattern:
FRONT: ...
BACK: ...
FRONT: ...
BACK: ...
""".trimIndent()
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