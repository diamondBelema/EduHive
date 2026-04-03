package com.dibe.eduhive.domain.usecase.hive

import com.dibe.eduhive.domain.repository.HiveRepository
import javax.inject.Inject

class DeleteHiveUseCase @Inject constructor(
    private val hiveRepository: HiveRepository
) {
    suspend operator fun invoke(hiveId: String): Result<Unit> {
        return try {
            hiveRepository.deleteHive(hiveId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
