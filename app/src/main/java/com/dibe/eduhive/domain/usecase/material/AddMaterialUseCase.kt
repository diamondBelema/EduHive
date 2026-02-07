package com.dibe.eduhive.domain.usecase.material

import android.net.Uri
import com.dibe.eduhive.data.repository.ConceptRepositoryImpl
import com.dibe.eduhive.data.repository.FlashcardRepositoryImpl
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.model.MaterialType
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.MaterialRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for adding new study material to a hive.
 *
 * Flow:
 * 1. Extract text from file (PDF, PPTX, DOCX, Image, etc.)
 * 2. Save material metadata to database
 * 3. Extract concepts using AI
 * 4. Generate flashcards for each concept
 * 5. Mark material as processed
 */
class AddMaterialUseCase @Inject constructor(
    private val materialRepository: MaterialRepository,
    private val fileDataSource: FileDataSource,
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(
        uri: Uri,
        hiveId: String,
        title: String,
        hiveContext: String = "" // e.g., "Biology 101"
    ): Result<AddMaterialResult> {
        return try {
            // 1. Extract text from file
            val extractedText = fileDataSource.extractText(uri).getOrElse {
                return Result.failure(it)
            }

            if (extractedText.isBlank()) {
                return Result.failure(IllegalStateException("File contains no readable text"))
            }

            // 2. Detect material type and save metadata
            val materialType = detectMaterialType(uri)
            val material = Material(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                title = title.trim().ifEmpty { "Untitled Material" },
                type = materialType,
                localPath = uri.toString(),
                processed = false,
                createdAt = System.currentTimeMillis()
            )

            materialRepository.addMaterial(material)

            // 3. Extract concepts using AI
            val concepts = conceptRepository.extractConceptsFromMaterial(
                materialText = extractedText,
                hiveId = hiveId,
                hiveContext = hiveContext
            )

            if (concepts.isEmpty()) {
                return Result.failure(IllegalStateException("No concepts could be extracted from this material"))
            }

            // 4. Generate flashcards for each concept
            var totalFlashcards = 0
            concepts.forEach { concept ->
                val flashcards = flashcardRepository.generateFlashcardsForConcept(
                    conceptId = concept.id,
                    conceptName = concept.name,
                    conceptDescription = concept.description,
                    count = 5
                )
                totalFlashcards += flashcards.size
            }

            // 5. Mark material as processed
            materialRepository.markAsProcessed(material.id)

            Result.success(
                AddMaterialResult(
                    material = material,
                    conceptsCreated = concepts.size,
                    flashcardsCreated = totalFlashcards
                )
            )

        } catch (e: Exception) {
            Result.failure(e)
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
 * Result of adding material with statistics.
 */
data class AddMaterialResult(
    val material: Material,
    val conceptsCreated: Int,
    val flashcardsCreated: Int
)