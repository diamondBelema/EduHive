package com.dibe.eduhive.presentation.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.data.source.ai.AIModelManager
import com.dibe.eduhive.data.source.ai.ModelDownloadProgress
import com.dibe.eduhive.data.source.ai.ModelPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelManager: AIModelManager,
    private val modelPreferences: ModelPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        combine(
            modelPreferences.activeModelFlow,
            modelPreferences.useMobileDataFlow
        ) { activeModel, mobileData ->
            val available = modelManager.getAvailableModels().map { info ->
                ModelSettingsInfo(
                    id = info.id,
                    name = info.name,
                    description = info.description,
                    sizeLabel = String.format(Locale.getDefault(), "%.0f MB", info.sizeMB),
                    isDownloaded = modelManager.isModelReady(info.id),
                    isRecommended = info.recommended
                )
            }
            _state.update {
                it.copy(
                    activeModelId = activeModel,
                    useMobileData = mobileData,
                    availableModels = available
                )
            }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectModel -> {
                switchModel(event.modelId)
            }
            is SettingsEvent.ToggleMobileData -> {
                viewModelScope.launch {
                    modelPreferences.setUseMobileData(event.enabled)
                }
            }
            is SettingsEvent.ClearCache -> {
                clearCache()
            }
            is SettingsEvent.DismissMessage -> {
                _state.update { it.copy(message = null) }
            }
            is SettingsEvent.ResetSetupOnNextLaunch -> {
                viewModelScope.launch {
                    modelPreferences.setSetupComplete(false)
                    _state.update {
                        it.copy(message = SettingsMessage.Success("Setup wizard will appear on next app launch."))
                    }
                }
            }
        }
    }

    private fun switchModel(modelId: String) {
        viewModelScope.launch {
            if (_state.value.isBusy) return@launch

            _state.update {
                it.copy(
                    isBusy = true,
                    busyModelId = modelId,
                    downloadProgress = 0f,
                    message = null
                )
            }

            val allowMobileData = _state.value.useMobileData
            val alreadyDownloaded = modelManager.isModelReady(modelId)
            var downloadFailed = false

            if (!alreadyDownloaded) {
                modelManager.downloadModel(modelId, allowMobileData).collect { progress ->
                    when (progress) {
                        is ModelDownloadProgress.Started -> {
                            _state.update { it.copy(downloadStatus = "Starting download...") }
                        }
                        is ModelDownloadProgress.Registered -> {
                            _state.update { it.copy(downloadStatus = "Download queued", downloadProgress = 0.05f) }
                        }
                        is ModelDownloadProgress.Downloading -> {
                            _state.update {
                                it.copy(
                                    downloadStatus = "Downloading ${progress.progressPercentage}%",
                                    downloadProgress = progress.progressPercentage / 100f
                                )
                            }
                        }
                        is ModelDownloadProgress.Complete -> {
                            _state.update { it.copy(downloadStatus = "Download complete. Activating model...") }
                        }
                        is ModelDownloadProgress.Paused -> {
                            _state.update {
                                it.copy(downloadStatus = progress.reason)
                            }
                        }
                        is ModelDownloadProgress.Failed -> {
                            downloadFailed = true
                            _state.update {
                                it.copy(
                                    isBusy = false,
                                    busyModelId = null,
                                    downloadStatus = null,
                                    message = SettingsMessage.Error(progress.error)
                                )
                            }
                        }
                    }
                }
            }

            if (downloadFailed) return@launch

            modelManager.unloadModel()
            modelManager.loadModel(modelId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            busyModelId = null,
                            downloadProgress = 0f,
                            downloadStatus = null,
                            message = SettingsMessage.Success("${modelDisplayName(modelId)} is now active.")
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            busyModelId = null,
                            downloadProgress = 0f,
                            downloadStatus = null,
                            message = SettingsMessage.Error(error.message ?: "Failed to activate model")
                        )
                    }
                }
            )
        }
    }

    private fun clearCache() {
        viewModelScope.launch {
            if (_state.value.isBusy) return@launch
            _state.update { it.copy(isBusy = true, message = null) }

            modelManager.clearDownloadedModelsCache().fold(
                onSuccess = { deletedCount ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            busyModelId = null,
                            downloadProgress = 0f,
                            downloadStatus = null,
                            message = SettingsMessage.Success("Cleared cache. Removed $deletedCount model file(s).")
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = SettingsMessage.Error(error.message ?: "Failed to clear cache")
                        )
                    }
                }
            )
        }
    }

    private fun modelDisplayName(modelId: String): String {
        return state.value.availableModels.firstOrNull { it.id == modelId }?.name ?: modelId
    }
}

data class SettingsState(
    val activeModelId: String? = null,
    val useMobileData: Boolean = true,
    val availableModels: List<ModelSettingsInfo> = emptyList(),
    val isBusy: Boolean = false,
    val busyModelId: String? = null,
    val downloadProgress: Float = 0f,
    val downloadStatus: String? = null,
    val message: SettingsMessage? = null
)

data class ModelSettingsInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeLabel: String,
    val isDownloaded: Boolean,
    val isRecommended: Boolean
)

sealed class SettingsMessage {
    data class Success(val text: String) : SettingsMessage()
    data class Error(val text: String) : SettingsMessage()
}

sealed class SettingsEvent {
    data class SelectModel(val modelId: String) : SettingsEvent()
    data class ToggleMobileData(val enabled: Boolean) : SettingsEvent()
    object ClearCache : SettingsEvent()
    object ResetSetupOnNextLaunch : SettingsEvent()
    object DismissMessage : SettingsEvent()
}