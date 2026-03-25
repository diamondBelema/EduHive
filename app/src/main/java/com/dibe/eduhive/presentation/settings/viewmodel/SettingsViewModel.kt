package com.dibe.eduhive.presentation.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.data.source.ai.AIModelManager
import com.dibe.eduhive.data.source.ai.ModelPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
                    isDownloaded = modelManager.hasModelReady() && info.id == activeModel // Simplification
                )
            }
            _state.update { it.copy(
                activeModelId = activeModel,
                useMobileData = mobileData,
                availableModels = available
            ) }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectModel -> {
                viewModelScope.launch {
                    modelPreferences.setActiveModel(event.modelId)
                    // Optionally trigger download if not present
                }
            }
            is SettingsEvent.ToggleMobileData -> {
                viewModelScope.launch {
                    modelPreferences.setUseMobileData(event.enabled)
                }
            }
            is SettingsEvent.ClearCache -> {
                viewModelScope.launch {
                    modelManager.unloadModel()
                    // Clear logic if needed
                }
            }
        }
    }
}

data class SettingsState(
    val activeModelId: String? = null,
    val useMobileData: Boolean = true,
    val availableModels: List<ModelSettingsInfo> = emptyList()
)

data class ModelSettingsInfo(
    val id: String,
    val name: String,
    val description: String,
    val isDownloaded: Boolean
)

sealed class SettingsEvent {
    data class SelectModel(val modelId: String) : SettingsEvent()
    data class ToggleMobileData(val enabled: Boolean) : SettingsEvent()
    object ClearCache : SettingsEvent()
}
