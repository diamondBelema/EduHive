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
                val reviews = reviewEventRepository.getRecentEvents(limit = 300)
                    .filter { it.conceptId in conceptIds }
                    .sortedByDescending { it.timestamp }
                _state.update { it.copy(isLoading = false, reviews = reviews) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

data class ReviewListState(
    val isLoading: Boolean = false,
    val reviews: List<ReviewEvent> = emptyList(),
    val error: String? = null
)
