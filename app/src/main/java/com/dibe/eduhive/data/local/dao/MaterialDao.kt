package com.dibe.eduhive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dibe.eduhive.data.local.entity.MaterialEntity


@Dao
interface MaterialDao {
    @Insert
    suspend fun insert(material: MaterialEntity)

    @Query("SELECT * FROM materials WHERE hiveId = :hiveId")
    suspend fun getForHive(hiveId: String): List<MaterialEntity>

    @Query("UPDATE materials SET processed = 1 WHERE materialId = :id")
    suspend fun markProcessed(id: String)

    @Query("DELETE FROM materials WHERE materialId = :id")
    suspend fun delete(id: String)
}
