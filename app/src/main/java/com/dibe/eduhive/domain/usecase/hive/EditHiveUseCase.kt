package com.dibe.eduhive.domain.usecase.hive

import com.dibe.eduhive.domain.repository.HiveRepository
import javax.inject.Inject

class EditHiveUseCase @Inject constructor(
    private val hiveRepository: HiveRepository
) {
    suspend operator fun invoke(
        hiveId: String,
        name: String,
        description: String?
    ): Result<Unit> {
        return try {
            if (name.isBlank()) {
                return Result.failure(IllegalArgumentException("Hive name cannot be empty"))
            }
            hiveRepository.updateHive(hiveId, name.trim(), description?.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
