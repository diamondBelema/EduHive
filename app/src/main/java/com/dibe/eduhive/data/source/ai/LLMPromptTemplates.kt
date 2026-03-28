package com.dibe.eduhive.data.source.ai

/**
 * Prompt templates for each generation task.
 *
 * Kept deliberately short — on-device models (135M–1B) have a 1280-token window.
 * Every token spent on persona text and verbose rules is a token stolen from the
 * model's output budget. Shorter prompts = faster generation = more output headroom.
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
Extract 5-10 specific, testable concepts from the text.
Prefer mechanisms, processes, relationships, definitions. Avoid broad topics.
${contextLine}Text:
$text

CONCEPT: Muscle hypertrophy
DESCRIPTION: Increase in muscle size due to resistance training.

CONCEPT: Osmosis
DESCRIPTION: Movement of water across a semi-permeable membrane.

CONCEPT:""".trimIndent()
    }

    // ── Flashcard generation ──────────────────────────────────────────────────

    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count flashcards for: $conceptName
Context: $conceptDescription

Rules:
- FRONT: a specific question (scenario, comparison, or consequence — not a single word)
- BACK: a full sentence explaining the logic, not just a definition

FRONT: How does $conceptName break down under extreme conditions?
BACK: [concise explanation of the failure mode or edge case]

FRONT:""".trimIndent()
    }

    /**
     * Batched flashcard generation for multiple concepts.
     * Keep concepts.size <= 3 per call (enforced by BATCH_SIZE in AiDataSource).
     */
    fun flashcardBatch(concepts: List<Pair<String, String>>, countPerConcept: Int): String {
        val conceptsBlock = concepts.mapIndexed { i, (name, desc) ->
            "${i + 1}. $name: $desc"
        }.joinToString("\n")

        return """
Generate $countPerConcept flashcards for each concept below.

$conceptsBlock

Rules:
- FRONT: a complete question (not a single word)
- BACK: a complete answer sentence

CONCEPT: [Number]
FRONT: [Question]
BACK: [Answer]""".trimIndent()
    }

    /**
     * Refinement prompt — only called when pass rate < MIN_PASS_RATE.
     */
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
Write $count quiz questions about: $conceptName
$factsBlock

Use TRUE_FALSE for factual statements. Use MCQ when multiple answers are plausible.
Each question must test a different aspect.

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

QUESTION 1
TYPE:""".trimIndent()
    }

    // ── Prompt mutation for retry attempts ────────────────────────────────────

    fun mutate(basePrompt: String, attempt: Int): String {
        val suffix = when (attempt) {
            0 -> ""
            1 -> "\nFocus on real-world applications and practical scenarios."
            else -> "\nFocus on edge cases and subtle differences between related ideas."
        }
        return basePrompt + suffix
    }

    // ── Grounded chat ─────────────────────────────────────────────────────────

    fun groundedChat(question: String, contextChunks: List<GroundedContextChunk>): String {
        val chunkBlock = contextChunks.joinToString("\n\n") { chunk ->
            """
CHUNK ${chunk.index}
MATERIAL: ${chunk.materialTitle}
SOURCE_CHUNK_INDEX: ${chunk.chunkIndex}
TEXT: ${chunk.text}
            """.trimIndent()
        }

        return """
Answer the question using ONLY the provided chunks.
If context is incomplete, give the best answer but state your uncertainty.

Question:
$question

Context Chunks:
$chunkBlock

ANSWER: <your response in 3-7 sentences>
CONFIDENCE: <HIGH|MEDIUM|LOW>
CITATIONS: <comma-separated chunk numbers like 1,3; use NONE if no support>""".trimIndent()
    }
}