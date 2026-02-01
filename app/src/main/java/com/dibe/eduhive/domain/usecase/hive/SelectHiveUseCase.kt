package com.dibe.eduhive.domain.usecase.hive

import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.domain.repository.HiveRepository
import javax.inject.Inject

/**
 * Use case for selecting/switching to a hive.
 * Updates the lastAccessedAt timestamp.
 */
class SelectHiveUseCase @Inject constructor(
    private val hiveRepository: HiveRepository
) {
    suspend operator fun invoke(hiveId: String): Result<Hive> {
        return try {
            // Get the hive
            val hive = hiveRepository.getHiveById(hiveId)
                ?: return Result.failure(IllegalArgumentException("Hive not found"))

            // Update last accessed timestamp
            hiveRepository.updateLastAccessed(hiveId, System.currentTimeMillis())

            Result.success(hive)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}