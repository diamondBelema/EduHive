package com.dibe.eduhive.domain.repository

import com.dibe.eduhive.domain.model.Hive

/**
 * Repository interface for Hive operations.
 * Follows the Repository Pattern and Dependency Inversion Principle.
 *
 * The domain layer defines the contract (interface).
 * The data layer provides the implementation.
 */
interface HiveRepository {

    suspend fun createHive(hive: Hive)

    suspend fun getAllHives(): List<Hive>

    suspend fun getArchivedHives(): List<Hive>

    suspend fun getHiveById(hiveId: String): Hive?

    suspend fun updateLastAccessed(hiveId: String, timestamp: Long)

    suspend fun updateHive(hiveId: String, name: String, description: String?, iconName: String)

    suspend fun archiveHive(hiveId: String)

    suspend fun unarchiveHive(hiveId: String)

    suspend fun deleteHive(hiveId: String)
}