package com.dibe.eduhive.domain.usecase.concept

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.repository.ConceptRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for manually creating a concept.
 *
 * Note: Most concepts are created automatically via AddMaterialUseCase.
 * This is for manual creation when needed.
 */
class CreateConceptUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository
) {
    suspend operator fun invoke(
        hiveId: String,
        name: String,
        description: String? = null
    ): Result<Concept> {
        return try {
            if (name.isBlank()) {
                return Result.failure(IllegalArgumentException("Concept name cannot be empty"))
            }

            val concept = Concept(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                name = name.trim(),
                description = description?.trim(),
                confidence = 0.3, // Default initial confidence
                lastReviewedAt = null
            )

            conceptRepository.addConcepts(listOf(concept))

            Result.success(concept)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}