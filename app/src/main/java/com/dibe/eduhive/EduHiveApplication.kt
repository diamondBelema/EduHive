package com.dibe.eduhive

import android.app.Application
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment
import dagger.hilt.android.HiltAndroidApp

/**
 * EduHive Application class.
 *
 * Responsibilities:
 * - Initialize Hilt for dependency injection
 * - Initialize Run Anywhere SDK for AI operations
 */
@HiltAndroidApp
class EduHiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Run Anywhere SDK for on-device AI
        RunAnywhere.initialize(
            apiKey = null,  // Optional for development mode
            environment = SDKEnvironment.DEVELOPMENT
        )
    }
}
