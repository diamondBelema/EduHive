package com.dibe.eduhive.domain.usecase.hive

import com.dibe.eduhive.domain.repository.HiveRepository
import javax.inject.Inject

class ArchiveHiveUseCase @Inject constructor(
    private val hiveRepository: HiveRepository
) {
    suspend operator fun invoke(hiveId: String, archive: Boolean = true): Result<Unit> {
        return try {
            if (archive) {
                hiveRepository.archiveHive(hiveId)
            } else {
                hiveRepository.unarchiveHive(hiveId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
