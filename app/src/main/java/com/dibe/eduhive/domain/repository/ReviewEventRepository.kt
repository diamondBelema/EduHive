package com.dibe.eduhive.domain.repository

import com.dibe.eduhive.domain.model.ReviewEvent

/**
 * Repository interface for ReviewEvent operations.
 * Used for analytics, history tracking, and debugging.
 */
interface ReviewEventRepository {

    suspend fun logReviewEvent(event: ReviewEvent)

    suspend fun getEventsForConcept(conceptId: String): List<ReviewEvent>

    /**
     * Get recent review events across all concepts.
     * Used for activity feed and dashboards.
     */
    suspend fun getRecentEvents(limit: Int = 50): List<ReviewEvent>

    /**
     * Get review events within a time range.
     * Used for streaks and session analytics.
     */
    suspend fun getEventsInRange(startTime: Long, endTime: Long): List<ReviewEvent>
}