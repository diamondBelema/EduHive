package com.dibe.eduhive.presentation.addMaterial.viewmodel

import android.net.Uri

// Events
sealed class AddMaterialEvent {
    data class SelectFile(val uri: Uri, val title: String) : AddMaterialEvent()
    object DismissSuccess : AddMaterialEvent()
    object ClearError : AddMaterialEvent()
}