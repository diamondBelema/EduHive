package com.dibe.eduhive.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.data.source.ai.ModelPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EduHiveNavViewModel @Inject constructor(
    private val modelPreferences: ModelPreferences
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    init {
        determineStartDestination()
    }

    private fun determineStartDestination() {
        viewModelScope.launch {
            _startDestination.value =
                if (modelPreferences.isSetupComplete()) {
                    Screen.HiveList.route
                } else {
                    Screen.FirstTimeSetup.route
                }
        }
    }
}
