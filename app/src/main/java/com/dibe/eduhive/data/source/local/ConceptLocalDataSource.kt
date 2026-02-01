package com.dibe.eduhive.data.source.local

import com.dibe.eduhive.data.local.dao.ConceptDao
import com.dibe.eduhive.data.local.entity.ConceptEntity
import jakarta.inject.Inject


/**
 * Local data source for Concept operations.
 */
class ConceptLocalDataSource @Inject constructor(
    private val conceptDao: ConceptDao
) {
    suspend fun insertAll(concepts: List<ConceptEntity>) =
        conceptDao.insertAll(concepts)

    suspend fun getForHive(hiveId: String) =
        conceptDao.getForHive(hiveId)

    suspend fun getById(id: String) =
        conceptDao.getById(id)

    suspend fun getWeakestConcepts(hiveId: String, limit: Int) =
        conceptDao.getWeakestConcepts(hiveId, limit)

    suspend fun getAverageConfidence(hiveId: String) =
        conceptDao.getAverageConfidence(hiveId)

    suspend fun updateConfidence(id: String, score: Float, time: Long) =
        conceptDao.updateConfidence(id, score, time)

    suspend fun deleteAllForHive(hiveId: String) =
        conceptDao.deleteAllForHive(hiveId)
}