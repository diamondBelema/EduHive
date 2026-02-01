package com.dibe.eduhive.domain.usecase.hive

import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.domain.repository.HiveRepository
import javax.inject.Inject

/**
 * Use case for retrieving all hives.
 * Returns hives sorted by last accessed (most recent first).
 */
class GetHivesUseCase @Inject constructor(
    private val hiveRepository: HiveRepository
) {
    suspend operator fun invoke(): Result<List<Hive>> {
        return try {
            val hives = hiveRepository.getAllHives()
                .sortedByDescending { it.lastAccessedAt }

            Result.success(hives)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}