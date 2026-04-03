package com.dibe.eduhive.presentation.conceptList.viewmodel

sealed class ConceptListEvent {
    object Reload : ConceptListEvent()
    data class ToggleSelection(val conceptId: String) : ConceptListEvent()
    object SelectWeak : ConceptListEvent()
    object ClearSelection : ConceptListEvent()
    data class Generate(val mode: GenerationMode) : ConceptListEvent()
    object ClearGenerated : ConceptListEvent()
    object DismissError : ConceptListEvent()
}
