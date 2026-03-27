package com.dibe.eduhive.presentation.addMaterial.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.manager.BackgroundGenerationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddMaterialViewModel @Inject constructor(
    private val backgroundGenerationManager: BackgroundGenerationManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(AddMaterialState())
    val state: StateFlow<AddMaterialState> = _state.asStateFlow()

    fun onEvent(event: AddMaterialEvent) {
        when (event) {
            is AddMaterialEvent.SelectFile -> handleFileSelected(event.uri, event.title)
            is AddMaterialEvent.DismissSuccess -> {
                _state.update { it.copy(successMessage = null) }
            }
            is AddMaterialEvent.ClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    private fun handleFileSelected(uri: Uri, title: String) {
        viewModelScope.launch {
            // Now scheduling as a background task
            backgroundGenerationManager.scheduleMaterialProcessing(
                uri = uri,
                hiveId = hiveId,
                title = title
            )
            
            // Immediately notify user that processing has started in background
            _state.update { 
                it.copy(
                    isProcessing = false, // We don't block the UI anymore
                    successMessage = "Processing \"$title\" in background. You'll be notified when complete."
                )
            }
        }
    }
}
