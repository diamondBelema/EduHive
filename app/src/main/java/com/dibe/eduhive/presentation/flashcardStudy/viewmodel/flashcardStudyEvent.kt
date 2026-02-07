package com.dibe.eduhive.presentation.flashcardStudy.viewmodel

import com.dibe.eduhive.domain.model.enums.ConfidenceLevel

// Events
sealed class FlashcardStudyEvent {
    object FlipCard : FlashcardStudyEvent()
    data class RateConfidence(val level: ConfidenceLevel) : FlashcardStudyEvent()
    object Reload : FlashcardStudyEvent()
}