package com.dibe.eduhive.domain.usecase.concept

import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.repository.ConceptRepository
import javax.inject.Inject

/**
 * Use case for retrieving all concepts in a hive.
 * Can optionally sort by confidence (weakest first).
 */
class GetConceptsByHiveUseCase @Inject constructor(
    private val conceptRepository: ConceptRepository
) {
    suspend operator fun invoke(
        hiveId: String,
        sortByWeakest: Boolean = false
    ): Result<List<Concept>> {
        return try {
            val concepts = conceptRepository.getConceptsForHive(hiveId)

            val sorted = if (sortByWeakest) {
                concepts.sortedBy { it.confidence }
            } else {
                concepts.sortedBy { it.name }
            }

            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}