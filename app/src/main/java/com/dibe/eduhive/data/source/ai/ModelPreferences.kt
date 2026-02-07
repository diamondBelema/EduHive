package com.dibe.eduhive.data.source.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for model preferences using DataStore.
 * Replaces SharedPreferences for better async support.
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

        // Dynamic keys for downloaded models
        private fun keyModelDownloaded(modelId: String) =
            booleanPreferencesKey("model_downloaded_$modelId")
    }

    /**
     * Get active model ID (synchronously for quick checks).
     */
    fun getActiveModel(): String? {
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[KEY_ACTIVE_MODEL]
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Set active model ID.
     */
    suspend fun setActiveModel(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_MODEL] = modelId
        }
    }

    /**
     * Check if model is downloaded (synchronously).
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[keyModelDownloaded(modelId)] ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Mark model as downloaded.
     */
    suspend fun setModelDownloaded(modelId: String, downloaded: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyModelDownloaded(modelId)] = downloaded
        }
    }

    /**
     * Check if first-time setup is complete.
     */
    fun isSetupComplete(): Boolean {
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[KEY_SETUP_COMPLETE] ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Mark setup as complete.
     */
    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SETUP_COMPLETE] = complete
        }
    }

    /**
     * Clear all preferences (for testing).
     */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}