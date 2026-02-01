package com.dibe.eduhive.data.repository

import com.dibe.eduhive.data.local.entity.ReviewEventEntity
import com.dibe.eduhive.data.source.local.ReviewEventLocalDataSource
import com.dibe.eduhive.domain.model.ReviewEvent
import com.dibe.eduhive.domain.repository.ReviewEventRepository
import jakarta.inject.Inject


class ReviewEventRepositoryImpl @Inject constructor(
    private val localDataSource: ReviewEventLocalDataSource
) : ReviewEventRepository {

    override suspend fun logReviewEvent(event: ReviewEvent) {
        localDataSource.insert(ReviewEventEntity.fromDomain(event))
    }

    override suspend fun getEventsForConcept(conceptId: String): List<ReviewEvent> {
        return localDataSource.getForConcept(conceptId).map { it.toDomain() }
    }

    override suspend fun getRecentEvents(limit: Int): List<ReviewEvent> {
        return localDataSource.getRecentEvents(limit).map { it.toDomain() }
    }

    override suspend fun getEventsInRange(startTime: Long, endTime: Long): List<ReviewEvent> {
        return localDataSource.getEventsInRange(startTime, endTime).map { it.toDomain() }
    }
}