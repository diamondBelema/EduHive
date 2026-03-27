package com.dibe.eduhive

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dibe.eduhive.workers.NotificationHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class EduHiveApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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