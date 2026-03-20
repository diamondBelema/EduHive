package com.dibe.eduhive.domain.usecase.material

import android.net.Uri
import com.dibe.eduhive.data.repository.ConceptExtractionProgress
import com.dibe.eduhive.data.repository.ConceptRepositoryImpl
import com.dibe.eduhive.data.repository.FlashcardGenerationProgress
import com.dibe.eduhive.data.repository.FlashcardRepositoryImpl
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.model.MaterialType
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.forEachIndexed

class AddMaterialUseCase @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val materialRepository: MaterialRepository,
    private val conceptRepository: ConceptRepository, // Use Impl for streaming
    private val flashcardRepository: FlashcardRepository // Use Impl for streaming
) {

    operator fun invoke(
        uri: Uri,
        hiveId: String,
        title: String,
        hiveContext: String = ""
    ): Flow<MaterialProcessingProgress> = channelFlow {
        send(MaterialProcessingProgress.Started)

        try {
            // 1. Extract text from file
            send(MaterialProcessingProgress.ExtractingText)

            val extractedText = fileDataSource.extractText(uri).getOrElse { error ->
                send(MaterialProcessingProgress.Failed(error.message ?: "Failed to extract text"))
                return@channelFlow
            }

            if (extractedText.isBlank()) {
                send(MaterialProcessingProgress.Failed("No text found in file"))
                return@channelFlow
            }

            send(MaterialProcessingProgress.TextExtracted(extractedText.length))

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

            // 3. Extract concepts with STREAMING progress
            send(MaterialProcessingProgress.ExtractingConcepts)

            var concepts: List<com.dibe.eduhive.domain.model.Concept>? = null

            // Collect streaming progress from repository
            conceptRepository.extractConceptsFromMaterialStreaming(
                materialText = extractedText,
                hiveId = hiveId,
                hiveContext = hiveContext
            ).collect { progress ->
                when (progress) {
                    is ConceptExtractionProgress.Processing -> {
                        // Optional: Send detailed progress (40-60% range)
                        // send(MaterialProcessingProgress.ExtractingConceptsProgress(progress.percent))
                    }
                    is ConceptExtractionProgress.Success -> {
                        concepts = progress.concepts
                        send(MaterialProcessingProgress.ConceptsExtracted(progress.concepts.size))
                    }
                    is ConceptExtractionProgress.Error -> {
                        send(MaterialProcessingProgress.Failed("Concept extraction failed: ${progress.message}"))
                        return@collect
                    }
                    else -> { /* Loading - ignore */ }
                }
            }

            val extractedConcepts = concepts ?: run {
                send(MaterialProcessingProgress.Failed("No concepts extracted"))
                return@channelFlow
            }

            if (extractedConcepts.isEmpty()) {
                send(MaterialProcessingProgress.Failed("No concepts found in material"))
                return@channelFlow
            }

            // 4. Generate flashcards for each concept with progress
            var totalFlashcards = 0

            extractedConcepts.forEachIndexed { index, concept ->
                send(MaterialProcessingProgress.GeneratingFlashcards(index + 1, extractedConcepts.size))

                // Use standard blocking call (fast enough for flashcards)
                val flashcards = flashcardRepository.generateFlashcardsForConceptStreaming(
                    conceptId = concept.id,
                    conceptName = concept.name,
                    conceptDescription = concept.description ?: "",
                    count = 5
                )

                flashcards.collect { flashcards ->
                    if(flashcards is FlashcardGenerationProgress.Success) {
                        totalFlashcards += flashcards.flashcards.size
                    }
                }

                // Small yield to keep UI responsive
                kotlinx.coroutines.delay(10)
            }

            // 5. Mark material as processed
            materialRepository.markAsProcessed(material.id)

            // 6. Complete
            send(
                MaterialProcessingProgress.Complete(
                    materialId = material.id,
                    conceptsCreated = extractedConcepts.size,
                    flashcardsCreated = totalFlashcards
                )
            )

        } catch (e: Exception) {
            send(MaterialProcessingProgress.Failed(e.message ?: "Unknown error"))
        }
    }

    private fun detectMaterialType(uri: Uri): MaterialType {
        val mimeType = fileDataSource.context.contentResolver.getType(uri)

        return when {
            mimeType?.contains("pdf") == true -> MaterialType.PDF
            mimeType?.contains("powerpoint") == true ||
                    mimeType?.contains("presentation") == true -> MaterialType.SLIDES
            mimeType?.contains("text") == true -> MaterialType.TEXT
            else -> MaterialType.PDF // Default
        }
    }
}

/**
 * Progress states for material processing.
 * (Unchanged - matches your ViewModel expectations)
 */
sealed class MaterialProcessingProgress {
    object Started : MaterialProcessingProgress()
    object ExtractingText : MaterialProcessingProgress()
    data class TextExtracted(val characterCount: Int) : MaterialProcessingProgress()
    data class MaterialSaved(val materialId: String) : MaterialProcessingProgress()
    object ExtractingConcepts : MaterialProcessingProgress()
    data class ConceptsExtracted(val count: Int) : MaterialProcessingProgress()
    data class GeneratingFlashcards(val current: Int, val total: Int) : MaterialProcessingProgress()
    data class Complete(
        val materialId: String,
        val conceptsCreated: Int,
        val flashcardsCreated: Int
    ) : MaterialProcessingProgress()
    data class Failed(val error: String) : MaterialProcessingProgress()
    data class ExtractingConceptsProgress(val percent: Int) : MaterialProcessingProgress()
}