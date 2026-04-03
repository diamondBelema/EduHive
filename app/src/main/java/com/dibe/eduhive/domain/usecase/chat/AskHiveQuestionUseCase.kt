package com.dibe.eduhive.domain.usecase.chat

import android.net.Uri
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.ai.GroundedContextChunk
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.data.source.ai.TextChunker
import com.dibe.eduhive.domain.repository.MaterialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AskHiveQuestionUseCase @Inject constructor(
    private val materialRepository: MaterialRepository,
    private val fileDataSource: FileDataSource,
    private val textChunker: TextChunker,
    private val aiDataSource: AIDataSource
) {

    private data class RankedChunk(
        val materialId: String,
        val materialTitle: String,
        val localPath: String,
        val chunkIndex: Int,
        val text: String,
        val score: Double
    )

    suspend operator fun invoke(
        hiveId: String,
        question: String
    ): Result<DocumentChatAnswer> = withContext(Dispatchers.IO) {
        if (question.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ask a question first"))
        }

        val processedMaterials = if (hiveId == "ALL") {
            materialRepository.getAllProcessedMaterials()
        } else {
            materialRepository.getMaterialsForHive(hiveId).filter { it.processed }
        }

        if (processedMaterials.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No processed documents found in this hive yet")
            )
        }

        val rankedChunks = mutableListOf<RankedChunk>()

        processedMaterials.forEach { material ->
            val extractedPages = runCatching {
                fileDataSource.extractTextPages(Uri.parse(material.localPath)).getOrThrow()
            }.getOrElse { emptyList() }

            var runningChunkIndex = 0
            extractedPages.forEach { page ->
                // Larger chunks to capture more context
                textChunker
                    .chunkText(text = page, maxTokens = 150, overlapTokens = 30)
                    .forEach { chunk ->
                        val normalizedText = chunk.text.trim()
                        if (normalizedText.length < 20) return@forEach
                        val score = lexicalScore(question, normalizedText)
                        rankedChunks += RankedChunk(
                            materialId = material.id,
                            materialTitle = material.title,
                            localPath = material.localPath,
                            chunkIndex = runningChunkIndex,
                            text = normalizedText,
                            score = score
                        )
                        runningChunkIndex += 1
                    }
            }
        }

        if (rankedChunks.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("I could not read any text from your processed documents")
            )
        }

        // Filter and sort chunks
        val topChunks = rankedChunks
            .sortedByDescending { it.score }
            .take(MAX_CONTEXT_CHUNKS)

        val groundedContext = topChunks.mapIndexed { index, chunk ->
            GroundedContextChunk(
                index = index + 1,
                materialTitle = chunk.materialTitle,
                chunkIndex = chunk.chunkIndex,
                text = chunk.text.take(MAX_CHARS_PER_CHUNK)
            )
        }

        val generated = aiDataSource.answerQuestionFromContext(question, groundedContext).getOrElse {
            val fallback = topChunks.first()
            return@withContext Result.success(
                DocumentChatAnswer(
                    question = question,
                    answer = "I could not generate a complete answer right now. Based on your docs, this looks related to: ${fallback.text.take(260)}...",
                    citations = listOf(
                        DocumentChatCitation(
                            materialId = fallback.materialId,
                            materialTitle = fallback.materialTitle,
                            chunkIndex = fallback.chunkIndex,
                            snippet = fallback.text.take(180),
                            localPath = fallback.localPath
                        )
                    ),
                    isGrounded = false,
                    warning = "Low confidence answer. Verify with the cited chunk."
                )
            )
        }

        val citationByContextIndex = topChunks
            .mapIndexed { i, chunk -> i + 1 to chunk }
            .toMap()

        val mappedCitations = generated.citationIndexes
            .distinct()
            .mapNotNull { contextIndex ->
                citationByContextIndex[contextIndex]?.let { chunk ->
                    DocumentChatCitation(
                        materialId = chunk.materialId,
                        materialTitle = chunk.materialTitle,
                        chunkIndex = chunk.chunkIndex,
                        snippet = chunk.text.take(180),
                        localPath = chunk.localPath
                    )
                }
            }
            .ifEmpty {
                // If model failed to cite but we have high-score chunks, cite the best one
                if (topChunks.first().score > MIN_CONFIDENT_SCORE) {
                    listOf(DocumentChatCitation(
                        materialId = topChunks.first().materialId,
                        materialTitle = topChunks.first().materialTitle,
                        chunkIndex = topChunks.first().chunkIndex,
                        snippet = topChunks.first().text.take(180),
                        localPath = topChunks.first().localPath
                    ))
                } else emptyList()
            }

        // Lowered threshold and improved detection
        val strongestScore = topChunks.firstOrNull()?.score ?: 0.0
        val isAnswerRelevant = generated.answer.length > 20 &&
                !generated.answer.contains("enough specific information", ignoreCase = true)

        val weakGrounding = strongestScore < 0.1 || !isAnswerRelevant

        Result.success(
            DocumentChatAnswer(
                question = question,
                answer = generated.answer,
                citations = mappedCitations,
                isGrounded = !weakGrounding,
                warning = if (weakGrounding) {
                    "This answer may be incomplete. Check the cited chunks for accuracy."
                } else {
                    null
                }
            )
        )
    }

    private fun lexicalScore(question: String, text: String): Double {
        val queryTokens = tokenize(question)
        if (queryTokens.isEmpty()) return 0.0

        val textTokens = tokenize(text)
        if (textTokens.isEmpty()) return 0.0

        val overlaps = queryTokens.count { it in textTokens }
        val overlapScore = overlaps.toDouble() / queryTokens.size

        // Increased bonus for exact phrase match
        val phraseBonus = if (text.lowercase().contains(question.trim().lowercase())) 0.5 else 0.0

        val specificTermBonus = queryTokens
            .filter { it.length > 5 } // Lowered to 5 chars
            .count { it in textTokens } * 0.12

        val educationalBonus = queryTokens
            .count { it in EDUCATIONAL_KEYWORDS && it in textTokens } * 0.15

        return overlapScore + phraseBonus + specificTermBonus + educationalBonus
    }

    private fun tokenize(value: String): Set<String> {
        return value
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { token -> token.length >= 2 && token !in STOP_WORDS } // Lowered length to 2
            .toSet()
    }

    companion object {
        // Budget: 1280 token window - 200 output - 80 template - 30 question = 970 tokens for context
        // 3 chunks x ~300 chars (~75 tokens each) = ~225 tokens — fits safely with headroom
        private const val MAX_CONTEXT_CHUNKS = 3
        private const val MAX_CHARS_PER_CHUNK = 300
        private const val MIN_CONFIDENT_SCORE = 0.10

        private val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "from", "into", "about",
            "have", "has", "are", "was", "were", "your", "their",
            "will", "would", "could", "should", "than", "then", "been", "being", "can"
        )

        private val EDUCATIONAL_KEYWORDS = setOf(
            "define", "defined", "definition", "explain", "explained", "explanation",
            "describe", "described", "description", "compare", "contrast",
            "analyze", "analyse", "evaluate", "summarize", "summarise",
            "theory", "theories", "concept", "concepts", "principle", "principles",
            "law", "laws", "effect", "effects", "cause", "causes",
            "process", "processes", "method", "methods", "function", "functions",
            "structure", "structures", "type", "types", "example", "examples",
            "difference", "differences", "relationship", "relationships",
            "factor", "factors", "role", "roles", "purpose", "result", "results",
            "mechanism", "mechanisms", "property", "properties", "characteristic",
            "characteristics", "component", "components", "system", "systems"
        )
    }
}

data class DocumentChatAnswer(
    val question: String,
    val answer: String,
    val citations: List<DocumentChatCitation>,
    val isGrounded: Boolean,
    val warning: String?
)

data class DocumentChatCitation(
    val materialId: String,
    val materialTitle: String,
    val chunkIndex: Int,
    val snippet: String,
    val localPath: String
)