package com.dibe.eduhive.presentation.firstTimeSetup.viewmodel

import com.dibe.eduhive.data.source.ai.ModelInfo

data class FirstTimeSetupState(
    val setupNeeded: Boolean = false,
    val isComplete: Boolean = false,
    val skipped: Boolean = false,
    val currentStep: SetupStep = SetupStep.WELCOME,
    val recommendedModel: ModelInfo? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: ModelInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)