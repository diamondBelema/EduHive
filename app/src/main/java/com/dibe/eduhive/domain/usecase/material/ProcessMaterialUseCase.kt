package com.dibe.eduhive.domain.usecase.material

import android.net.Uri
import com.dibe.eduhive.data.repository.ConceptRepositoryImpl
import com.dibe.eduhive.data.repository.FlashcardRepositoryImpl
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.domain.repository.MaterialRepository
import javax.inject.Inject

/**
 * Use case for reprocessing an existing material.
 *
 * Useful when:
 * - Initial processing failed
 * - User wants to regenerate concepts/flashcards
 * - AI model has been updated
 */
class ProcessMaterialUseCase @Inject constructor(
    private val materialRepository: MaterialRepository,
    private val fileDataSource: FileDataSource,
    private val conceptRepository: ConceptRepositoryImpl,
    private val flashcardRepository: FlashcardRepositoryImpl
) {
    suspend operator fun invoke(
        materialId: String,
        hiveContext: String = ""
    ): Result<ProcessMaterialResult> {
        return try {
            // Get material
            val materials = materialRepository.getMaterialsForHive("")
            val material = materials.find { it.id == materialId }
                ?: return Result.failure(IllegalArgumentException("Material not found"))

            // Extract text from stored path
            val uri = Uri.parse(material.localPath)
            val extractedText = fileDataSource.extractText(uri).getOrElse {
                return Result.failure(it)
            }

            // Extract concepts
            val concepts = conceptRepository.extractConceptsFromMaterial(
                materialText = extractedText,
                hiveId = material.hiveId,
                hiveContext = hiveContext
            )

            // Generate flashcards
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

            // Mark as processed
            materialRepository.markAsProcessed(material.id)

            Result.success(
                ProcessMaterialResult(
                    conceptsCreated = concepts.size,
                    flashcardsCreated = totalFlashcards
                )
            )

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ProcessMaterialResult(
    val conceptsCreated: Int,
    val flashcardsCreated: Int
)