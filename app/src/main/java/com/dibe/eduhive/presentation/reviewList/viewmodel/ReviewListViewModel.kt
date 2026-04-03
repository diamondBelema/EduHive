package com.dibe.eduhive.presentation.reviewList.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.ReviewEvent
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.ReviewEventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ReviewListViewModel @Inject constructor(
    private val reviewEventRepository: ReviewEventRepository,
    private val conceptRepository: ConceptRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(ReviewListState())
    val state: StateFlow<ReviewListState> = _state.asStateFlow()

    init {
        loadReviews()
    }

    fun loadReviews() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val conceptIds = conceptRepository.getConceptsForHive(hiveId).map { it.id }.toSet()
                val reviews = reviewEventRepository.getRecentEvents(limit = 500)
                    .filter { it.conceptId in conceptIds }
                    .sortedByDescending { it.timestamp }

                val stats = computeStats(reviews)
                _state.update {
                    it.copy(
                        isLoading = false,
                        reviews = reviews,
                        streakDays = stats.streakDays,
                        reviewedToday = stats.reviewedToday,
                        correctToday = stats.correctToday,
                        weeklyActivity = stats.weeklyActivity
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun computeStats(reviews: List<ReviewEvent>): ReviewStats {
        if (reviews.isEmpty()) return ReviewStats()

        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        val todayEnd = todayStart + DAY_MS

        // Today stats
        val todayReviews = reviews.filter { it.timestamp in todayStart until todayEnd }
        val reviewedToday = todayReviews.size
        val correctToday = todayReviews.count { it.outcome >= 0.5f }

        // Streak: count consecutive days backwards from today that have at least 1 review
        var streakDays = 0
        var checkDay = todayStart
        while (true) {
            val dayEnd = checkDay + DAY_MS
            val hasActivity = reviews.any { it.timestamp in checkDay until dayEnd }
            if (hasActivity) {
                streakDays++
                checkDay -= DAY_MS
            } else {
                break
            }
        }

        // Weekly activity: reviews per day for last 7 days (index 0 = 6 days ago, 6 = today)
        val weeklyActivity = (6 downTo 0).map { daysAgo ->
            val dayStart = startOfDay(now) - daysAgo * DAY_MS
            val dayEnd = dayStart + DAY_MS
            reviews.count { it.timestamp in dayStart until dayEnd }
        }

        return ReviewStats(
            streakDays = streakDays,
            reviewedToday = reviewedToday,
            correctToday = correctToday,
            weeklyActivity = weeklyActivity
        )
    }

    private fun startOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

private data class ReviewStats(
    val streakDays: Int = 0,
    val reviewedToday: Int = 0,
    val correctToday: Int = 0,
    val weeklyActivity: List<Int> = List(7) { 0 }
)

data class ReviewListState(
    val isLoading: Boolean = false,
    val reviews: List<ReviewEvent> = emptyList(),
    val streakDays: Int = 0,
    val reviewedToday: Int = 0,
    val correctToday: Int = 0,
    /** Number of reviews per day for the last 7 days (index 0 = 6 days ago, 6 = today). */
    val weeklyActivity: List<Int> = List(7) { 0 },
    val error: String? = null
)
