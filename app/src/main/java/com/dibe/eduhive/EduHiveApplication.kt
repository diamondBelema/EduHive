package com.dibe.eduhive

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dibe.eduhive.data.source.ai.AIModelManager
import com.dibe.eduhive.workers.NotificationHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class EduHiveApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var aiModelManager: AIModelManager

    /** Long-lived scope for fire-and-forget operations like model unloading. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "EduHive"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupModelStorage()
        PDFBoxResourceLoader.init(this)
        NotificationHelper.createNotificationChannel(this)
        Log.d(TAG, "✅ EduHive initialized successfully")
    }

    /**
     * Release the AI model when the OS signals low memory so the native weights
     * (~150 MB – 1.5 GB depending on the chosen model) are freed before the OS
     * has to kill the process entirely.  The model will be reloaded on demand.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.w(TAG, "onTrimMemory level=$level — releasing AI model to free native memory")
            appScope.launch {
                aiModelManager.unloadModel()
            }
        }
    }

    private fun setupModelStorage() {
        try {
            val modelsDir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "")
            if (!modelsDir.exists()) {
                val created = modelsDir.mkdirs()
                Log.d(TAG, "Models directory created: $created")
            }
            Log.d(TAG, """
                Storage Setup:
                - Path: ${modelsDir.absolutePath}
                - Writable: ${modelsDir.canWrite()}
                - Free Space: ${filesDir.freeSpace / (1024 * 1024)}MB
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create models directory", e)
        }
    }
}