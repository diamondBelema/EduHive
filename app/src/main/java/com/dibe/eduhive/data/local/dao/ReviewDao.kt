package com.dibe.eduhive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dibe.eduhive.data.local.entity.ReviewEventEntity

@Dao
interface ReviewDao {

    @Insert
    suspend fun insert(event: ReviewEventEntity)

    @Query("SELECT * FROM review_events WHERE conceptId = :conceptId")
    suspend fun getForConcept(conceptId: String): List<ReviewEventEntity>

    @Query("SELECT * FROM review_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<ReviewEventEntity>

    @Query("SELECT * FROM review_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getEventsInRange(startTime: Long, endTime: Long): List<ReviewEventEntity>
}
