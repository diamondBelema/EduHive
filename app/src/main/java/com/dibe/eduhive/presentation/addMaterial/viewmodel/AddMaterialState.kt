package com.dibe.eduhive.presentation.addMaterial.viewmodel

// State
data class AddMaterialState(
    val isProcessing: Boolean = false,
    val processingStatus: String? = null,
    val progressPercentage: Int = 0,
    val successMessage: String? = null,
    val error: String? = null,
    // Quality tracking — populated as validation progresses
    val flashcardsValid: Int = 0,
    val flashcardsRejected: Int = 0,
    val duplicatesFound: Int = 0
)
