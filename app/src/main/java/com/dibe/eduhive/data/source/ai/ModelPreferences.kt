package com.dibe.eduhive.data.source.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for model preferences using DataStore.
 */
@Singleton
class ModelPreferences @Inject constructor(
    private val context: Context
) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "model_preferences"
    )

    companion object {
        private val KEY_ACTIVE_MODEL = stringPreferencesKey("active_model")
        private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val KEY_USE_MOBILE_DATA = booleanPreferencesKey("use_mobile_data")

        private fun keyModelDownloaded(modelId: String) =
            booleanPreferencesKey("model_downloaded_$modelId")
    }

    val activeModelFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_MODEL] }
    val setupCompleteFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_COMPLETE] ?: false }
    val useMobileDataFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_USE_MOBILE_DATA] ?: true }

    fun getActiveModel(): String? {
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[KEY_ACTIVE_MODEL]
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setActiveModel(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_MODEL] = modelId
        }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[keyModelDownloaded(modelId)] ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setModelDownloaded(modelId: String, downloaded: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyModelDownloaded(modelId)] = downloaded
        }
    }

    fun isSetupComplete(): Boolean {
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[KEY_SETUP_COMPLETE] ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SETUP_COMPLETE] = complete
        }
    }

    suspend fun setUseMobileData(use: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_MOBILE_DATA] = use
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
