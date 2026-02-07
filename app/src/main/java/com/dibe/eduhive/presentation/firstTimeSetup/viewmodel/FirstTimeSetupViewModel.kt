package com.dibe.eduhive.presentation.firstTimeSetup.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.data.source.ai.AIModelManager
import com.dibe.eduhive.data.source.ai.ModelDownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FirstTimeSetupViewModel @Inject constructor(
    private val modelManager: AIModelManager
) : ViewModel() {

    private val _state = MutableStateFlow(FirstTimeSetupState())
    val state: StateFlow<FirstTimeSetupState> = _state.asStateFlow()

    init {
        checkSetupStatus()
    }

    fun onEvent(event: FirstTimeSetupEvent) {
        when (event) {
            is FirstTimeSetupEvent.StartSetup -> startSetup()
            is FirstTimeSetupEvent.SelectModel -> selectModel(event.modelId)
            is FirstTimeSetupEvent.DownloadModel -> downloadModel(event.modelId)
            is FirstTimeSetupEvent.SkipSetup -> skipSetup()
            is FirstTimeSetupEvent.RetryDownload -> retryDownload()
            is FirstTimeSetupEvent.CompleteSetup -> completeSetup()
        }
    }

    private fun checkSetupStatus() {
        viewModelScope.launch {
            val hasModel = modelManager.hasModelReady()

            if (hasModel) {
                _state.update { it.copy(setupNeeded = false, isComplete = true) }
            } else {
                val recommended = modelManager.getRecommendedModel()
                val available = modelManager.getAvailableModels()

                _state.update {
                    it.copy(
                        setupNeeded = true,
                        recommendedModel = recommended,
                        availableModels = available,
                        selectedModel = recommended
                    )
                }
            }
        }
    }

    private fun startSetup() {
        _state.update { it.copy(currentStep = SetupStep.MODEL_SELECTION) }
    }

    private fun selectModel(modelId: String) {
        val model = state.value.availableModels.find { it.id == modelId }
        if (model != null) {
            _state.update { it.copy(selectedModel = model) }
        }
    }

    private fun downloadModel(modelId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    currentStep = SetupStep.DOWNLOADING,
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadStatus = "",
                    error = null
                )
            }

            modelManager.downloadModel(modelId).collect { progress ->
                when (progress) {
                    is ModelDownloadProgress.Started -> {
                        _state.update {
                            it.copy(
                                downloadStatus = "Starting download...",
                                downloadProgress = 0f
                            )
                        }
                    }

                    is ModelDownloadProgress.Registered -> {
                        _state.update {
                            it.copy(
                                downloadStatus = "Model registered: ${progress.registeredId}",
                                downloadProgress = 0.05f  // 5% for registration
                            )
                        }
                    }

                    is ModelDownloadProgress.Downloading -> {
                        _state.update {
                            it.copy(
                                downloadProgress = progress.progress,
                                downloadStatus = "Downloaded ${progress.downloadedMB.toInt()}MB / ${progress.totalMB.toInt()}MB",
                                downloadedBytes = progress.downloadedBytes,
                                totalBytes = progress.totalBytes
                            )
                        }
                    }

                    is ModelDownloadProgress.Complete -> {
                        _state.update {
                            it.copy(
                                downloadStatus = "Loading model...",
                                downloadProgress = 0.95f
                            )
                        }

                        // Load the model after download
                        modelManager.loadModel(progress.modelId).fold(
                            onSuccess = {
                                _state.update {
                                    it.copy(
                                        currentStep = SetupStep.COMPLETE,
                                        isDownloading = false,
                                        downloadProgress = 1f,
                                        downloadStatus = "Setup complete!",
                                        error = null
                                    )
                                }
                            },
                            onFailure = { error ->
                                _state.update {
                                    it.copy(
                                        isDownloading = false,
                                        error = "Downloaded but failed to load: ${error.message}",
                                        downloadStatus = "Load failed",
                                        downloadProgress = 0f
                                    )
                                }
                            }
                        )
                    }

                    is ModelDownloadProgress.Failed -> {
                        _state.update {
                            it.copy(
                                isDownloading = false,
                                error = progress.error,
                                downloadStatus = "Download failed",
                                downloadProgress = 0f
                            )
                        }
                    }
                }
            }
        }
    }

    private fun retryDownload() {
        val modelId = state.value.selectedModel?.id ?: return
        _state.update { it.copy(error = null) }
        downloadModel(modelId)
    }

    private fun skipSetup() {
        // Allow user to proceed without model (will prompt when needed)
        _state.update {
            it.copy(
                setupNeeded = false,
                isComplete = true,
                skipped = true
            )
        }
    }

    private fun completeSetup() {
        _state.update {
            it.copy(
                setupNeeded = false,
                isComplete = true
            )
        }
    }
}


