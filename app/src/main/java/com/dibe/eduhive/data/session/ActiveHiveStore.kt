package com.dibe.eduhive.data.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ActiveHiveStore @Inject constructor() {

    private val _activeHiveId = MutableStateFlow<String?>(null)
    val activeHiveId: StateFlow<String?> = _activeHiveId.asStateFlow()

    fun setActiveHiveId(hiveId: String?) {
        _activeHiveId.value = hiveId
    }

    fun clear() {
        _activeHiveId.value = null
    }
}

