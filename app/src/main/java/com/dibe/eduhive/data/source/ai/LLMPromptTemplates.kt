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
Extract 5-10 specific study concepts from the text.
Do not output blank lines. Do not output markdown.
Each concept must be unique and testable.
Each description must be 8-16 words.

OUTPUT RULES:
1) Start with OUTPUT_START on its own line.
2) Then only repeated pairs:
CONCEPT: <name>
DESCRIPTION: <description>
3) End with OUTPUT_END on its own line.
4) If nothing is found, return exactly:
OUTPUT_START
NO_CONCEPTS
OUTPUT_END

${contextLine}Text:
$text

OUTPUT_START
CONCEPT: Muscle hypertrophy
DESCRIPTION: Increase in muscle fiber size due to progressive resistance loading.

CONCEPT: Osmosis
DESCRIPTION: Passive movement of water across a selectively permeable membrane gradient.

OUTPUT_END

Now produce only the output block:
OUTPUT_START
CONCEPT:
DESCRIPTION:
OUTPUT_END
""".trimIndent()
    }

    // ── Flashcard generation ──────────────────────────────────────────────────

    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        return """
Create $count flashcards for: $conceptName
Context: $conceptDescription

Rules:
- Use only this format:
FRONT: <question>
BACK: <answer>
- FRONT must be a complete question mentioning "$conceptName" or a clear scenario.
- Avoid one-word or generic fronts (Definition, Application, Mechanism, etc.).
- BACK must be a concise standalone answer (1-2 sentences).
- Do not repeat stems like "What is" across all cards.

FRONT: How does $conceptName break down under extreme conditions?
BACK: [concise explanation of the failure mode or edge case]

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