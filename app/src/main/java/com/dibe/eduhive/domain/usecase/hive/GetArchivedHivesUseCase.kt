package com.dibe.eduhive.domain.usecase.hive

import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.domain.repository.HiveRepository
import javax.inject.Inject

class GetArchivedHivesUseCase @Inject constructor(
    private val hiveRepository: HiveRepository
) {
    suspend operator fun invoke(): Result<List<Hive>> {
        return try {
            val hives = hiveRepository.getArchivedHives()
                .sortedByDescending { it.lastAccessedAt }
            Result.success(hives)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
