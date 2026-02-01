package com.dibe.eduhive.domain.usecase.hive

import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.domain.repository.HiveRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for creating a new Hive.
 *
 * A Hive is the top-level container for a learning context
 * (e.g., a subject, course, or study goal).
 */
class CreateHiveUseCase @Inject constructor(
    private val hiveRepository: HiveRepository
) {
    suspend operator fun invoke(
        name: String,
        description: String? = null
    ): Result<Hive> {
        return try {
            // Validate input
            if (name.isBlank()) {
                return Result.failure(IllegalArgumentException("Hive name cannot be empty"))
            }

            // Create hive
            val hive = Hive(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = description?.trim(),
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis()
            )

            // Save to repository
            hiveRepository.createHive(hive)

            Result.success(hive)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}