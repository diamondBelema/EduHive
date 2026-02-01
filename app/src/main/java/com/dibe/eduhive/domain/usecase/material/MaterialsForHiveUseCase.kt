package com.dibe.eduhive.domain.usecase.material

import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.repository.MaterialRepository
import javax.inject.Inject

/**
 * Use case for retrieving all materials in a hive.
 * Returns materials sorted by creation date (newest first).
 */
class GetMaterialsForHiveUseCase @Inject constructor(
    private val materialRepository: MaterialRepository
) {
    suspend operator fun invoke(hiveId: String): Result<List<Material>> {
        return try {
            val materials = materialRepository.getMaterialsForHive(hiveId)
                .sortedByDescending { it.createdAt }

            Result.success(materials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}