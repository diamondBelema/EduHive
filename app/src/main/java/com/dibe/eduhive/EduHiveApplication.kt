package com.dibe.eduhive

import android.app.Application
import android.util.Log
import com.ketch.Ketch
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class EduHiveApplication : Application() {

    companion object {
        private const val TAG = "EduHive"
    }

    private lateinit var ketch: Ketch
    override fun onCreate() {
        super.onCreate()

        // Create models directory
        setupModelStorage()

        ketch = Ketch.builder().build(this)

        // Initialize PDFBox
        PDFBoxResourceLoader.init(this)

        Log.d(TAG, "✅ EduHive initialized successfully")
    }

    private fun setupModelStorage() {
        try {
            val modelsDir = File(filesDir, "models")
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