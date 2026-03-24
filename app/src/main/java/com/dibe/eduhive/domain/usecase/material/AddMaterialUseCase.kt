package com.dibe.eduhive.domain.usecase.material

import android.net.Uri
import com.dibe.eduhive.data.repository.ConceptExtractionProgress
import com.dibe.eduhive.data.repository.FlashcardGenerationProgress
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
 * 5. Mark as processed.
 */
class AddMaterialUseCase @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val materialRepository: MaterialRepository,
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository
) {

    @OptIn(DelicateCoroutinesApi::class)
    operator fun invoke(
        uri: Uri,
        hiveId: String,
        title: String,
        hiveContext: String = ""
    ): Flow<MaterialProcessingProgress> = channelFlow {
        send(MaterialProcessingProgress.Started)

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
                        // We continue to generate flashcards if we got at least some concepts earlier? 
                        // No, for now we fail if extraction fails.
                    }
                    else -> {}
                }
            }

            val finalConcepts = extractedConceptsList ?: run {
                if (channel.isClosedForSend) return@channelFlow
                send(MaterialProcessingProgress.Failed("No concepts extracted from material"))
                return@channelFlow
            }

            // 4. Generate flashcards for each concept
            var totalFlashcards = 0

            finalConcepts.forEachIndexed { index, concept ->
                send(MaterialProcessingProgress.GeneratingFlashcards(index + 1, finalConcepts.size))

                flashcardRepository.generateFlashcardsForConceptStreaming(
                    conceptId = concept.id,
                    conceptName = concept.name,
                    conceptDescription = concept.description ?: "",
                    count = 5
                ).collect { progress ->
                    if (progress is FlashcardGenerationProgress.Success) {
                        totalFlashcards += progress.flashcards.size
                    }
                }

                // Brief delay for thermal safety and UI smoothness
                delay(50)
            }

            // 5. Finalize
            materialRepository.markAsProcessed(material.id)

            send(
                MaterialProcessingProgress.Complete(
                    materialId = material.id,
                    conceptsCreated = finalConcepts.size,
                    flashcardsCreated = totalFlashcards
                )
            )

        } catch (e: Exception) {
            send(MaterialProcessingProgress.Failed(e.message ?: "Unknown error occurred"))
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
    data class Complete(
        val materialId: String,
        val conceptsCreated: Int,
        val flashcardsCreated: Int
    ) : MaterialProcessingProgress()
    data class Failed(val error: String) : MaterialProcessingProgress()
}
