package com.dibe.eduhive.data.source.local

import com.dibe.eduhive.data.local.dao.ReviewDao
import com.dibe.eduhive.data.local.entity.ReviewEventEntity
import jakarta.inject.Inject

/**
 * Local data source for ReviewEvent operations.
 */
class ReviewEventLocalDataSource @Inject constructor(
    private val reviewDao: ReviewDao
) {
    suspend fun insert(event: ReviewEventEntity) =
        reviewDao.insert(event)

    suspend fun getForConcept(conceptId: String) =
        reviewDao.getForConcept(conceptId)

    suspend fun getRecentEvents(limit: Int) =
        reviewDao.getRecentEvents(limit)

    suspend fun getEventsInRange(startTime: Long, endTime: Long) =
        reviewDao.getEventsInRange(startTime, endTime)
}