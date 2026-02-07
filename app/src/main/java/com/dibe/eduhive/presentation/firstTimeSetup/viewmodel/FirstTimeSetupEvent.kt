package com.dibe.eduhive.presentation.firstTimeSetup.viewmodel


sealed class FirstTimeSetupEvent {
    object StartSetup : FirstTimeSetupEvent()
    data class SelectModel(val modelId: String) : FirstTimeSetupEvent()
    data class DownloadModel(val modelId: String) : FirstTimeSetupEvent()
    object SkipSetup : FirstTimeSetupEvent()
    object RetryDownload : FirstTimeSetupEvent()
    object CompleteSetup : FirstTimeSetupEvent()
}