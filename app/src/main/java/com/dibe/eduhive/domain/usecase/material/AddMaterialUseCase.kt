package com.dibe.eduhive.domain.usecase.material

import android.net.Uri
import com.dibe.eduhive.data.repository.ConceptExtractionProgress
import com.dibe.eduhive.data.repository.FlashcardGenerationProgress
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.model.MaterialType
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.MaterialRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for adding new educational material to a Hive.
 * 
 * Process:
 * 1. Extract text from file (PDF, Image, etc.) - page by page for large files.
 * 2. Save material metadata to DB.
 * 3. Extract concepts using AI with streaming updates.
 * 4. Generate flashcards for each extracted concept.
 *    - Small files (< 5 pages): fast-track with single attempt, no validation.
 *    - Larger files: batch generation (3 concepts per AI call = 3× speedup).
 * 5. Mark as processed.
 */
class AddMaterialUseCase @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val materialRepository: MaterialRepository,
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository,
    private val aiDataSource: AIDataSource
) {

    companion object {
        /** Files with fewer pages use fast-track: no validation, 1 retry. */
        private const val SMALL_FILE_PAGE_THRESHOLD = 5
        /** Concepts grouped per AI call in the standard batch path. */
        private const val FLASHCARD_BATCH_SIZE = 3
        private const val FLASHCARDS_PER_CONCEPT = 3
    }

    @OptIn(DelicateCoroutinesApi::class)
    operator fun invoke(
        uri: Uri,
        hiveId: String,
        title: String,
        hiveContext: String = ""
    ): Flow<MaterialProcessingProgress> = channelFlow {
        send(MaterialProcessingProgress.Started)

        // Retain the model for the full pipeline to avoid unload/reload between steps
        aiDataSource.retainModelForPipeline()

        try {
            // 1. Extract text pages from file
            // Page-aware extraction is critical for large PDFs to stay within AI limits
            send(MaterialProcessingProgress.ExtractingText)

            val extractedPages = fileDataSource.extractTextPages(uri).getOrElse { error ->
                send(MaterialProcessingProgress.Failed(error.message ?: "Failed to extract text"))
                return@channelFlow
            }

            if (extractedPages.isEmpty()) {
                send(MaterialProcessingProgress.Failed("No readable text found in file"))
                return@channelFlow
            }

            val totalChars = extractedPages.sumOf { it.length }
            send(MaterialProcessingProgress.TextExtracted(totalChars))

            // Detect small/simple documents for fast-track processing
            val isSmallFile = extractedPages.size < SMALL_FILE_PAGE_THRESHOLD

            // 2. Detect material type and save metadata
            val materialType = detectMaterialType(uri)

            val material = Material(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                title = title,
                type = materialType,
                localPath = uri.toString(),
                processed = false,
                createdAt = System.currentTimeMillis()
            )

            materialRepository.addMaterial(material)
            send(MaterialProcessingProgress.MaterialSaved(material.id))

            // 3. Extract concepts using Page-aware Streaming
            // This prevents crashes by processing the document segment by segment
            send(MaterialProcessingProgress.ExtractingConcepts)

            var extractedConceptsList: List<com.dibe.eduhive.domain.model.Concept>? = null

            conceptRepository.extractConceptsFromPagesStreaming(
                pages = extractedPages,
                hiveId = hiveId,
                hiveContext = hiveContext
            ).collect { progress ->
                when (progress) {
                    is ConceptExtractionProgress.Processing -> {
                        send(MaterialProcessingProgress.ExtractingConceptsProgress(progress.percent))
                    }
                    is ConceptExtractionProgress.Success -> {
                        extractedConceptsList = progress.concepts
                        send(MaterialProcessingProgress.ConceptsExtracted(progress.concepts.size))
                    }
                    is ConceptExtractionProgress.Error -> {
                        send(MaterialProcessingProgress.Failed("Concept extraction failed: ${progress.message}"))
                    }
                    else -> {}
                }
            }

            val finalConcepts = extractedConceptsList ?: run {
                if (channel.isClosedForSend) return@channelFlow
                send(MaterialProcessingProgress.Failed("No concepts extracted from material"))
                return@channelFlow
            }

            // 4. Generate flashcards
            //    - Small files: fast-track streaming (1 attempt per concept, no validation)
            //    - Large files: batch generation (1 AI call per 3 concepts — 3× speedup)
            var totalValid = 0
            var totalRejected = 0
            val seenFronts = mutableSetOf<String>()
            var duplicatesFound = 0

            if (isSmallFile) {
                // Fast-track path: individual streaming with skipValidation=true
                finalConcepts.forEachIndexed { index, concept ->
                    send(MaterialProcessingProgress.GeneratingFlashcards(index + 1, finalConcepts.size))

                    flashcardRepository.generateFlashcardsForConceptStreaming(
                        conceptId = concept.id,
                        conceptName = concept.name,
                        conceptDescription = concept.description ?: "",
                        count = FLASHCARDS_PER_CONCEPT,
                        skipValidation = true
                    ).collect { progress ->
                        when (progress) {
                            is FlashcardGenerationProgress.Success -> {
                                totalValid += progress.flashcards.size
                                for (flashcard in progress.flashcards) {
                                    val key = flashcard.front.lowercase().trim()
                                    if (key in seenFronts) duplicatesFound++ else seenFronts.add(key)
                                }
                                send(MaterialProcessingProgress.ValidationProgress(
                                    current = index + 1,
                                    total = finalConcepts.size,
                                    valid = totalValid,
                                    flagged = 0,
                                    rejected = 0
                                ))
                            }
                            else -> { /* Loading/Retrying/Validating states are informational only */ }
                        }
                    }
                    delay(50)
                }
            } else {
                // Standard path: batch generation (3 concepts per AI call)
                val batches = finalConcepts.chunked(FLASHCARD_BATCH_SIZE)
                batches.forEachIndexed { batchIndex, batch ->
                    val batchStart = batchIndex * FLASHCARD_BATCH_SIZE + 1
                    val batchEnd = minOf(batchStart + batch.size - 1, finalConcepts.size)

                    send(MaterialProcessingProgress.GeneratingFlashcards(batchStart, finalConcepts.size))
                    send(MaterialProcessingProgress.ValidatingFlashcards(batchStart, finalConcepts.size))

                    val conceptTriples = batch.map { concept ->
                        Triple(concept.id, concept.name, concept.description)
                    }
                    val batchFlashcards = flashcardRepository.generateFlashcardsForConceptsBatch(
                        concepts = conceptTriples,
                        countPerConcept = FLASHCARDS_PER_CONCEPT
                    )

                    totalValid += batchFlashcards.size
                    for (flashcard in batchFlashcards) {
                        val key = flashcard.front.lowercase().trim()
                        if (key in seenFronts) duplicatesFound++ else seenFronts.add(key)
                    }

                    send(MaterialProcessingProgress.ValidationProgress(
                        current = batchEnd,
                        total = finalConcepts.size,
                        valid = totalValid,
                        flagged = 0,
                        rejected = totalRejected
                    ))
                    delay(50)
                }
            }

            // 5. Finalize
            send(MaterialProcessingProgress.DeduplicatingCards(duplicatesFound, totalValid + duplicatesFound))
            delay(100)

            materialRepository.markAsProcessed(material.id)

            send(
                MaterialProcessingProgress.ProcessingSummary(
                    materialId = material.id,
                    conceptsCreated = finalConcepts.size,
                    flashcardsValid = totalValid,
                    flashcardsFlagged = 0,
                    flashcardsRejected = totalRejected,
                    duplicatesFound = duplicatesFound
                )
            )

        } catch (e: Exception) {
            send(MaterialProcessingProgress.Failed(e.message ?: "Unknown error occurred"))
        } finally {
            aiDataSource.releaseModelRef()
        }
    }

    private fun detectMaterialType(uri: Uri): MaterialType {
        val mimeType = fileDataSource.context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("pdf") == true -> MaterialType.PDF
            mimeType?.contains("powerpoint") == true || mimeType?.contains("presentation") == true -> MaterialType.SLIDES
            mimeType?.contains("text") == true -> MaterialType.TEXT
            else -> MaterialType.PDF
        }
    }
}

/**
 * Progress states for material processing UI.
 */
sealed class MaterialProcessingProgress {
    object Started : MaterialProcessingProgress()
    object ExtractingText : MaterialProcessingProgress()
    data class TextExtracted(val characterCount: Int) : MaterialProcessingProgress()
    data class MaterialSaved(val materialId: String) : MaterialProcessingProgress()
    object ExtractingConcepts : MaterialProcessingProgress()
    data class ExtractingConceptsProgress(val percent: Int) : MaterialProcessingProgress()
    data class ConceptsExtracted(val count: Int) : MaterialProcessingProgress()
    data class GeneratingFlashcards(val current: Int, val total: Int) : MaterialProcessingProgress()

    // Quality Control Stages
    /** Emitted when quality validation begins for the concept at [current]/[total]. */
    data class ValidatingFlashcards(val current: Int, val total: Int) : MaterialProcessingProgress()
    /** Running quality totals after each concept finishes validation. */
    data class ValidationProgress(
        val current: Int,
        val total: Int,
        val valid: Int,
        val flagged: Int,
        val rejected: Int
    ) : MaterialProcessingProgress()
    /** Emitted when the AI retries generation for a concept that had too many bad cards. */
    data class RetryingGeneration(val conceptName: String, val attemptNumber: Int) : MaterialProcessingProgress()
    /** Emitted once cross-concept duplicate detection runs. */
    data class DeduplicatingCards(val progress: Int, val total: Int) : MaterialProcessingProgress()
    /** Terminal success state with full quality breakdown. */
    data class ProcessingSummary(
        val materialId: String,
        val conceptsCreated: Int,
        val flashcardsValid: Int,
        val flashcardsFlagged: Int,
        val flashcardsRejected: Int,
        val duplicatesFound: Int
    ) : MaterialProcessingProgress()

    data class Failed(val error: String) : MaterialProcessingProgress()
}
