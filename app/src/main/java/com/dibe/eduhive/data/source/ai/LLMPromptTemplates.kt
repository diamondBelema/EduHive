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

    private val TEACHER_PERSONA = """
        You are an elite Academic Tutor specializing in active recall and spaced repetition. 
        Your goal is to help students truly understand complex logic, not just memorize definitions.
        You write in a clear, professional, and intellectually stimulating tone.
        ---
    """.trimIndent()

    fun conceptExtraction(text: String, hiveContext: String = ""): String {
        val contextLine = if (hiveContext.isNotBlank()) "Subject: $hiveContext\n" else ""
        return """
Extract 5–10 HIGH-VALUE concepts from the text.

STRICT RULES:
- Each concept must be specific and testable (not broad topics like "exercise" or "health")
- Prefer mechanisms, processes, relationships, or definitions
- Avoid vague or generic terms
- Avoid overlapping concepts

$contextLine
Text:
$text

GOOD EXAMPLES:
CONCEPT: Muscle hypertrophy
DESCRIPTION: Increase in muscle size due to resistance training.

CONCEPT: Osmosis
DESCRIPTION: Movement of water across a semi-permeable membrane.

BAD EXAMPLES:
CONCEPT: Exercise
CONCEPT: Health

Output format:
CONCEPT: ...
DESCRIPTION: ...
    """.trimIndent()
    }

    fun flashcardDraft(conceptName: String, conceptDescription: String, count: Int): String {
        val basePrompt = """
Create $count unique, challenging flashcards for the concept: $conceptName
Context: $conceptDescription

GOAL: 
Create "Active Recall" cards. The front must be a specific question or scenario, and the back must be a concise, complete explanation.

STRICT RULES FOR THE FRONT (The Question):
- NEVER use single words like "Mechanism", "Application", or "Definition".
- Use "Scenario-based" prompts: "A manager is faced with X; how does $conceptName apply?"
- Use "Comparison" prompts: "How does $conceptName differ from its closest alternative?"
- Use "Functional" prompts: "What specific problem does $conceptName solve in a real-world system?"
- Use "Consequence" prompts: "What happens if $conceptName is ignored or performed incorrectly?"

STRICT RULES FOR THE BACK (The Answer):
- Must be a full, standalone sentence.
- Do not just define the word; explain the *logic* behind the answer.
- Keep it "bite-sized" but information-dense.

BAD EXAMPLE (Avoid these):
FRONT: Application
BACK: Applying the process to a task.

GOOD EXAMPLE:
FRONT: In a high-pressure environment, why might $conceptName lead to 'analysis paralysis'?
BACK: It occurs when the complexity of the decision-making process outweighs the time available, causing a total stall in action.

Output format:
FRONT: [Specific Question]
BACK: [Detailed Answer]
""".trimIndent()

        return "$TEACHER_PERSONA\n$basePrompt"
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
Generate $countPerConcept high-quality flashcards for each of the following $concepts.size concepts:

$conceptsBlock

STRICT QUALITY CHECK:
1. Every FRONT must be a complete, inquisitive question (e.g., "Under what conditions...", "How does...", "Why is...")
2. NO one-word fronts.
3. NO repetitive structures. 
4. If the concept is mitochondria for example, the question must mention the concept or a specific situation where it applies.

Output format:
CONCEPT: [Number]
FRONT: [Complete Question]
BACK: [Complete Answer]
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
Rewrite these flashcards to improve quality.

STRICT RULES:
- Replace ALL generic questions
- Remove repeated sentence patterns
- Make each question specific and unique
- Ensure each card tests a different idea
- Improve clarity and precision of answers

$cardsBlock

Output format:
FRONT: ...
BACK: ...
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
            0 -> ""
            1 -> "\nFocus more on real-world applications and practical scenarios."
            else -> "\nFocus on edge cases, exceptions, and subtle differences between related ideas."
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
You are EduHive's grounded study assistant.

Answer the question using ONLY the provided chunks.
If context is incomplete, still give the best possible answer but say uncertainty clearly.
Never invent source references.

Question:
$question

Context Chunks:
$chunkBlock

Return EXACT format:
ANSWER: <your response in 3-7 sentences>
CONFIDENCE: <HIGH|MEDIUM|LOW>
CITATIONS: <comma-separated chunk numbers like 1,3; use NONE if no support>
        """.trimIndent()
    }
}
