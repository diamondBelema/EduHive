package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.HiveEntity
import com.dibe.eduhive.data.source.local.HiveLocalDataSource
import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.domain.repository.HiveRepository
import javax.inject.Inject


class HiveRepositoryImpl @Inject constructor(
    private val localDataSource: HiveLocalDataSource
) : HiveRepository {

    override suspend fun createHive(hive: Hive) {
        localDataSource.insert(HiveEntity.fromDomain(hive))
    }

    override suspend fun getAllHives(): List<Hive> {
        return localDataSource.getAll().map { it.toDomain() }
    }

    override suspend fun getArchivedHives(): List<Hive> {
        return localDataSource.getArchived().map { it.toDomain() }
    }

    override suspend fun getHiveById(hiveId: String): Hive? {
        return localDataSource.getById(hiveId)?.toDomain()
    }

    override suspend fun updateLastAccessed(hiveId: String, timestamp: Long) {
        localDataSource.updateLastAccessed(hiveId, timestamp)
    }

    override suspend fun updateHive(hiveId: String, name: String, description: String?, iconName: String) {
        localDataSource.updateHiveDetails(hiveId, name, description, iconName)
    }

    override suspend fun archiveHive(hiveId: String) {
        localDataSource.setArchived(hiveId, true)
    }

    override suspend fun unarchiveHive(hiveId: String) {
        localDataSource.setArchived(hiveId, false)
    }

    override suspend fun deleteHive(hiveId: String) {
        localDataSource.delete(hiveId)
    }
}