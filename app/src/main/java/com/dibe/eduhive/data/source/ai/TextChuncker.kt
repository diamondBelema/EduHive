package com.dibe.eduhive.data.source.ai

import android.text.util.Rfc822Tokenizer.tokenize
import javax.inject.Inject

/**
 * Smart text chunking system for handling large documents.
 *
 * Features:
 * - Token counting (approximate)
 * - Overlap between chunks for context preservation
 * - Metadata retention (chunk index, total chunks)
 * - Configurable chunk size
 */
class TextChunker @Inject constructor() {

    companion object {
        // Run Anywhere SDK typical limits
        const val DEFAULT_MAX_TOKENS = 3500  // Leave buffer for prompt + output
        const val DEFAULT_OVERLAP_TOKENS = 200  // Overlap for context
        const val TOKENS_PER_WORD_AVG = 1.3  // English average
    }

    /**
     * Chunk text into manageable segments.
     *
     * @param text Full text to chunk
     * @param maxTokens Maximum tokens per chunk
     * @param overlapTokens Tokens to overlap between chunks
     * @return List of text chunks with metadata
     */
    fun chunkText(
        text: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        overlapTokens: Int = DEFAULT_OVERLAP_TOKENS
    ): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        val totalEstimatedTokens = estimateTokenCount(text)

        // Fast path: single chunk
        if (totalEstimatedTokens <= maxTokens) {
            return listOf(
                TextChunk(
                    text = text,
                    chunkIndex = 0,
                    totalChunks = 1,
                    startToken = 0,
                    endToken = totalEstimatedTokens,
                    overlapWithPrevious = false,
                    overlapWithNext = false
                )
            )
        }

        val sentences = splitIntoSentences(text)
        val chunks = mutableListOf<TextChunk>()

        var buffer = StringBuilder()
        var bufferTokens = 0
        var startToken = 0

        for (sentence in sentences) {
            val sentenceTokens = estimateTokenCount(sentence)

            // Flush current chunk if limit exceeded
            if (bufferTokens + sentenceTokens > maxTokens && buffer.isNotEmpty()) {
                chunks += TextChunk(
                    text = buffer.toString().trim(),
                    chunkIndex = chunks.size,
                    totalChunks = 0, // temporary, fixed later
                    startToken = startToken,
                    endToken = startToken + bufferTokens,
                    overlapWithPrevious = chunks.isNotEmpty(),
                    overlapWithNext = true
                )

                // Seed next chunk with overlap
                val overlapText = getLastNTokens(buffer.toString(), overlapTokens)
                val overlapTokenCount = estimateTokenCount(overlapText)

                buffer = StringBuilder(overlapText)
                bufferTokens = overlapTokenCount
                startToken += maxOf(0, bufferTokens - overlapTokens)
            }

            buffer.append(sentence).append(" ")
            bufferTokens += sentenceTokens
        }

        // Final chunk
        if (buffer.isNotEmpty()) {
            chunks += TextChunk(
                text = buffer.toString().trim(),
                chunkIndex = chunks.size,
                totalChunks = 0,
                startToken = startToken,
                endToken = startToken + bufferTokens,
                overlapWithPrevious = chunks.isNotEmpty(),
                overlapWithNext = false
            )
        }

        // Fix totalChunks once, truthfully
        val totalChunks = chunks.size
        return chunks.map { it.copy(totalChunks = totalChunks) }
    }

    /**
     * Estimate token count for text.
     * Uses simple heuristic: words * 1.3
     */
    fun estimateTokenCount(text: String): Int {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        return (words * TOKENS_PER_WORD_AVG).toInt()
    }

    /**
     * Split text into sentences.
     */
    private fun splitIntoSentences(text: String): List<String> {
        // Simple sentence splitting (can be improved with NLP)
        return text.split(Regex("[.!?]+\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Get last N tokens worth of text for overlap.
     */
    private fun getLastNTokens(text: String, tokens: Int): String {
        val words = text.split(Regex("\\s+"))
        val wordsToTake = (tokens / TOKENS_PER_WORD_AVG).toInt()

        return if (words.size <= wordsToTake) {
            text
        } else {
            words.takeLast(wordsToTake).joinToString(" ")
        }
    }
}

/**
 * Represents a chunk of text with metadata.
 */
data class TextChunk(
    val text: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val startToken: Int,
    val endToken: Int,
    val overlapWithPrevious: Boolean,
    val overlapWithNext: Boolean
) {
    val tokenCount: Int
        get() = endToken - startToken

    val progress: Float
        get() = when {
            totalChunks <= 1 -> 1f
            else -> chunkIndex.toFloat() / (totalChunks - 1)
        }

}