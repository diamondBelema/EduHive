package com.dibe.eduhive.data.source.local

import com.dibe.eduhive.data.local.dao.HiveDao
import com.dibe.eduhive.data.local.entity.HiveEntity
import javax.inject.Inject


/**
 * Local data source for Hive operations.
 * Thin wrapper around HiveDao.
 */
class HiveLocalDataSource @Inject constructor(
    private val hiveDao: HiveDao
) {
    suspend fun insert(hive: HiveEntity) = hiveDao.insert(hive)

    suspend fun getAll() = hiveDao.getAll()

    suspend fun getArchived() = hiveDao.getArchived()

    suspend fun getById(id: String) = hiveDao.getById(id)

    suspend fun updateLastAccessed(id: String, time: Long) =
        hiveDao.updateLastAccessed(id, time)

    suspend fun updateHiveDetails(id: String, name: String, description: String?, iconName: String) =
        hiveDao.updateHiveDetails(id, name, description, iconName)

    suspend fun setArchived(id: String, archived: Boolean) =
        hiveDao.setArchived(id, archived)

    suspend fun delete(id: String) = hiveDao.delete(id)
}