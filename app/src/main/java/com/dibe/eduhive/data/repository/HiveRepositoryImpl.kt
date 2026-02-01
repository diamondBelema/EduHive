package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.dao.HiveDao
import com.dibe.eduhive.data.local.entity.HiveEntity
import com.dibe.eduhive.data.source.local.ConceptLocalDataSource
import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.domain.repository.HiveRepository
import jakarta.inject.Inject


class HiveRepositoryImpl @Inject constructor(
    private val localDataSource: HiveLocalDataSource
) : HiveRepository {

    override suspend fun createHive(hive: Hive) {
        localDataSource.insert(HiveEntity.fromDomain(hive))
    }

    override suspend fun getAllHives(): List<Hive> {
        return localDataSource.getAll().map { it.toDomain() }
    }

    override suspend fun getHiveById(hiveId: String): Hive? {
        return localDataSource.getById(hiveId)?.toDomain()
    }

    override suspend fun updateLastAccessed(hiveId: String, timestamp: Long) {
        localDataSource.updateLastAccessed(hiveId, timestamp)
    }

    override suspend fun deleteHive(hiveId: String) {
        localDataSource.delete(hiveId)
    }
}