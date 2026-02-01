package com.dibe.eduhive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dibe.eduhive.data.local.entity.HiveEntity


@Dao
interface HiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hive: HiveEntity)

    @Query("SELECT * FROM hives ORDER BY lastAccessedAt DESC")
    suspend fun getAll(): List<HiveEntity>

    @Query("DELETE FROM hives WHERE hiveId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM hives WHERE hiveId = :id")
    suspend fun getById(id: String): HiveEntity?

    @Query("UPDATE hives SET lastAccessedAt = :time WHERE hiveId = :id")
    suspend fun updateLastAccessed(id: String, time: Long)
}
