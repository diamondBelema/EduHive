package com.dibe.eduhive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dibe.eduhive.data.source.ai.ModelPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ModelPreferences.
 *
 * Uses Robolectric to provide Android Context in tests.
 * These are still unit tests (fast), not instrumentation tests (slow).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])  // Android API 28
class ModelPreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: ModelPreferences

    @Before
    fun setup() {
        // Get test context
        context = ApplicationProvider.getApplicationContext()

        // Create preferences
        preferences = ModelPreferences(context)

        // Clear any existing data
        runBlocking {
            preferences.clear()
        }
    }

    @After
    fun tearDown() {
        // Clean up after each test
        runBlocking {
            preferences.clear()
        }
    }

    // ========== ACTIVE MODEL TESTS ==========

    @Test
    fun `getActiveModel returns null when no model set`() {
        // ACT
        val modelId = preferences.getActiveModel()

        // ASSERT
        assertNull(modelId)
    }

    @Test
    fun `setActiveModel stores model correctly`() = runBlocking {
        // ARRANGE
        val modelId = "qwen-0.5b"

        // ACT
        preferences.setActiveModel(modelId)

        // ASSERT
        val retrieved = preferences.getActiveModel()
        assertEquals(modelId, retrieved)
    }

    @Test
    fun `setActiveModel overwrites previous model`() = runBlocking {
        // ARRANGE
        preferences.setActiveModel("old-model")

        // ACT
        preferences.setActiveModel("new-model")

        // ASSERT
        val retrieved = preferences.getActiveModel()
        assertEquals("new-model", retrieved)
    }

    // ========== MODEL DOWNLOADED TESTS ==========

    @Test
    fun `isModelDownloaded returns false by default`() {
        // ACT
        val isDownloaded = preferences.isModelDownloaded("qwen-0.5b")

        // ASSERT
        assertFalse(isDownloaded)
    }

    @Test
    fun `setModelDownloaded marks model as downloaded`() = runBlocking {
        // ARRANGE
        val modelId = "qwen-0.5b"

        // ACT
        preferences.setModelDownloaded(modelId, true)

        // ASSERT
        assertTrue(preferences.isModelDownloaded(modelId))
    }

    @Test
    fun `setModelDownloaded can mark model as not downloaded`() = runBlocking {
        // ARRANGE
        val modelId = "qwen-0.5b"
        preferences.setModelDownloaded(modelId, true)

        // ACT
        preferences.setModelDownloaded(modelId, false)

        // ASSERT
        assertFalse(preferences.isModelDownloaded(modelId))
    }

    @Test
    fun `multiple models can be tracked independently`() = runBlocking {
        // ACT
        preferences.setModelDownloaded("model-1", true)
        preferences.setModelDownloaded("model-2", false)
        preferences.setModelDownloaded("model-3", true)

        // ASSERT
        assertTrue(preferences.isModelDownloaded("model-1"))
        assertFalse(preferences.isModelDownloaded("model-2"))
        assertTrue(preferences.isModelDownloaded("model-3"))
    }

    // ========== SETUP COMPLETE TESTS ==========

    @Test
    fun `isSetupComplete returns false by default`() {
        // ACT
        val isComplete = preferences.isSetupComplete()

        // ASSERT
        assertFalse(isComplete)
    }

    @Test
    fun `setSetupComplete marks setup as complete`() = runBlocking {
        // ACT
        preferences.setSetupComplete(true)

        // ASSERT
        assertTrue(preferences.isSetupComplete())
    }

    // ========== CLEAR TESTS ==========

    @Test
    fun `clear removes all preferences`() = runBlocking {
        // ARRANGE
        preferences.setActiveModel("qwen-0.5b")
        preferences.setModelDownloaded("qwen-0.5b", true)
        preferences.setSetupComplete(true)

        // ACT
        preferences.clear()

        // ASSERT
        assertNull(preferences.getActiveModel())
        assertFalse(preferences.isModelDownloaded("qwen-0.5b"))
        assertFalse(preferences.isSetupComplete())
    }
}