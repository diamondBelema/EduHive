package com.dibe.eduhive.data.source.local

import com.dibe.eduhive.data.local.dao.MaterialDao
import com.dibe.eduhive.data.local.entity.MaterialEntity
import jakarta.inject.Inject

/**
 * Local data source for Material operations.
 */
class MaterialLocalDataSource @Inject constructor(
    private val materialDao: MaterialDao
) {
    suspend fun insert(material: MaterialEntity) = materialDao.insert(material)

    suspend fun getForHive(hiveId: String) = materialDao.getForHive(hiveId)

    suspend fun markProcessed(id: String) = materialDao.markProcessed(id)

    suspend fun delete(id: String) = materialDao.delete(id)
}