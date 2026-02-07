package com.dibe.eduhive.domain.usecase.material

import android.net.Uri
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.model.MaterialType
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject

class AddMaterialUseCase @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val materialRepository: MaterialRepository,
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository
) {

    operator fun invoke(
        uri: Uri,
        hiveId: String,
        title: String,
        hiveContext: String = ""
    ): Flow<MaterialProcessingProgress> = flow {

        emit(MaterialProcessingProgress.Started)

        try {
            // 1. Extract text from file
            emit(MaterialProcessingProgress.ExtractingText)

            val extractedText = fileDataSource.extractText(uri).getOrElse { error ->
                emit(MaterialProcessingProgress.Failed(error.message ?: "Failed to extract text"))
                return@flow
            }

            emit(MaterialProcessingProgress.TextExtracted(extractedText.length))

            // 2. Detect material type and save metadata
            val materialType = detectMaterialType(uri)

            // 2. Save material metadata
            val material = Material(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                title = title,
                type =  materialType,
                localPath = uri.toString(),
                processed = false,
                createdAt = System.currentTimeMillis()
            )

            materialRepository.addMaterial(material)
            emit(MaterialProcessingProgress.MaterialSaved(material.id))

            // 3. Extract concepts
            emit(MaterialProcessingProgress.ExtractingConcepts)

            val concepts = conceptRepository.extractConceptsFromMaterial(
                materialText = extractedText,
                hiveId = hiveId,
                hiveContext = hiveContext
            )

            emit(MaterialProcessingProgress.ConceptsExtracted(concepts.size))

            // 4. Generate flashcards for each concept
            var totalFlashcards = 0

            concepts.forEachIndexed { index, concept ->
                emit(MaterialProcessingProgress.GeneratingFlashcards(index + 1, concepts.size))

                val flashcards = flashcardRepository.generateFlashcardsForConcept(
                    conceptId = concept.id,
                    conceptName = concept.name,
                    conceptDescription = concept.description ?: "",
                    count = 5
                )

                totalFlashcards += flashcards.size
            }

            // 5. Mark material as processed
            materialRepository.markAsProcessed(material.id)

            // 6. Complete
            emit(
                MaterialProcessingProgress.Complete(
                    materialId = material.id,
                    conceptsCreated = concepts.size,
                    flashcardsCreated = totalFlashcards
                )
            )

        } catch (e: Exception) {
            emit(MaterialProcessingProgress.Failed(e.message ?: "Unknown error"))
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
}