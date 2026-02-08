package com.dibe.eduhive

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class EduHiveApplication : Application() {

    companion object {
        private const val TAG = "EduHive"
    }

    override fun onCreate() {
        super.onCreate()

        // MediaPipe doesn't need SDK initialization!
        // Just create storage directories
        setupStorageDirectories()

        // Initialize PDFBox
        PDFBoxResourceLoader.init(this)

        Log.d(TAG, "EduHive initialized successfully")
    }

    private fun setupStorageDirectories() {
        try {
            // Create models directory
            val modelsDir = File(filesDir, "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
                Log.d(TAG, "Created models directory: ${modelsDir.absolutePath}")
            }

            Log.d(TAG, """
                Storage Ready:
                - Models: ${modelsDir.absolutePath}
                - Free Space: ${filesDir.freeSpace / (1024 * 1024)}MB
            """.trimIndent())

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup directories", e)
        }
    }
}