package com.dibe.eduhive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dibe.eduhive.data.local.entity.ConceptEntity


@Dao
interface ConceptDao {

    @Insert
    suspend fun insertAll(concepts: List<ConceptEntity>)

    @Query("SELECT * FROM concepts WHERE hiveId = :hiveId")
    suspend fun getForHive(hiveId: String): List<ConceptEntity>

    @Query("""
        UPDATE concepts 
        SET confidenceScore = :score, lastReviewedAt = :time 
        WHERE conceptId = :id
    """)
    suspend fun updateConfidence(id: String, score: Float, time: Long)

    @Query("SELECT * FROM concepts WHERE conceptId = :id")
    suspend fun getById(id: String): ConceptEntity?

    @Query("SELECT * FROM concepts WHERE hiveId = :hiveId ORDER BY confidenceScore ASC LIMIT :limit")
    suspend fun getWeakestConcepts(hiveId: String, limit: Int): List<ConceptEntity>

    @Query("SELECT AVG(confidenceScore) FROM concepts WHERE hiveId = :hiveId")
    suspend fun getAverageConfidence(hiveId: String): Float?

    @Query("DELETE FROM concepts WHERE hiveId = :hiveId")
    suspend fun deleteAllForHive(hiveId: String)
}
