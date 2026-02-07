package com.dibe.eduhive.data.source.ai

import android.content.Context
import com.runanywhere.sdk.core.types.InferenceFramework
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.Models.DownloadState
import com.runanywhere.sdk.public.extensions.downloadModel
import com.runanywhere.sdk.public.extensions.isLLMModelLoaded
import com.runanywhere.sdk.public.extensions.loadLLMModel
import com.runanywhere.sdk.public.extensions.registerModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages AI model lifecycle using Run Anywhere SDK.
 *
 * Handles:
 * - Model registration
 * - Model download with progress
 * - Model loading/unloading
 * - State persistence
 */
@Singleton
class AIModelManager @Inject constructor(
    private val context: Context,
    private val modelPreferences: ModelPreferences
) {

    companion object {
        // Recommended models for EduHive
        const val MODEL_QWEN_0_5B = "qwen-0.5b"
        const val MODEL_QWEN_1_5B = "qwen-1.5b"
        const val MODEL_SMOLLM_360M = "smollm-360m"

        // HuggingFace URLs

        private const val QWEN_0_5B_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf"
        private const val QWEN_1_5B_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf"
        private const val SMOLLM_360M_URL = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf"
    }

    /**
     * Check if any model is ready to use.
     */
    suspend fun hasModelReady(): Boolean {
        val modelId = modelPreferences.getActiveModel() ?: return false
        return modelPreferences.isModelDownloaded(modelId) && RunAnywhere.isLLMModelLoaded()
    }

    /**
     * Get currently active model ID.
     */
    fun getActiveModel(): String? {
        return modelPreferences.getActiveModel()
    }

    /**
     * Get recommended model based on device capabilities.
     */
    fun getRecommendedModel(): ModelInfo {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)

        return when {
            maxMemoryMB > 3000 -> getModelInfo(MODEL_QWEN_1_5B)  // High-end
            maxMemoryMB > 1500 -> getModelInfo(MODEL_QWEN_0_5B)  // Mid-range (recommended)
            else -> getModelInfo(MODEL_SMOLLM_360M)  // Low-end
        }
    }

    /**
     * Get all available models.
     */
    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            getModelInfo(MODEL_QWEN_0_5B),
            getModelInfo(MODEL_QWEN_1_5B),
            getModelInfo(MODEL_SMOLLM_360M)
        )
    }

    /**
     * Register and download model with progress tracking.
     */
    fun downloadModel(modelId: String): Flow<ModelDownloadProgress> {
        val modelInfo = getModelInfo(modelId)

        return kotlinx.coroutines.flow.flow {
            emit(ModelDownloadProgress.Started(modelId))

            try {
                // Register model with Run Anywhere
                val registeredModel = RunAnywhere.registerModel(
                    name = modelInfo.name,
                    url = modelInfo.url,
                    framework = InferenceFramework.LLAMA_CPP
                )

                emit(ModelDownloadProgress.Registered(registeredModel.id))

                // Download with progress tracking
                RunAnywhere.downloadModel(registeredModel.id).collect { progress ->
                    when (progress.state) {
                        DownloadState.DOWNLOADING -> {
                            emit(
                                ModelDownloadProgress.Downloading(
                                    progress = progress.progress,
                                    totalBytes = modelInfo.sizeBytes,
                                    downloadedBytes = (modelInfo.sizeBytes * progress.progress).toLong()
                                )
                            )
                        }

                        DownloadState.COMPLETED -> {
                            // Save to preferences
                            modelPreferences.setModelDownloaded(registeredModel.id, true)
                            modelPreferences.setActiveModel(registeredModel.id)

                            emit(ModelDownloadProgress.Complete(registeredModel.id))
                        }

                        DownloadState.ERROR -> {
                            emit(ModelDownloadProgress.Failed(
                                progress.error ?: "Download failed"
                            ))
                        }

                        else -> {
                            // Handle other states if needed
                        }
                    }
                }

            } catch (e: Exception) {
                emit(ModelDownloadProgress.Failed(e.message ?: "Failed to download model"))
            }
        }
    }

    /**
     * Load model into memory for inference.
     */
    suspend fun loadModel(modelId: String): Result<Unit> {
        return try {
            if (!modelPreferences.isModelDownloaded(modelId)) {
                return Result.failure(IllegalStateException("Model not downloaded: $modelId"))
            }

            RunAnywhere.loadLLMModel(modelId)
            modelPreferences.setActiveModel(modelId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if model is currently loaded in memory.
     */
    suspend fun isModelLoaded(): Boolean {
        return RunAnywhere.isLLMModelLoaded()
    }

    /**
     * Unload model from memory to free resources.
     */
    suspend fun unloadModel(): Result<Unit> {
        return try {
            // Note: Run Anywhere SDK doesn't expose explicit unload
            // Model is automatically managed by the SDK
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get model information.
     */
    private fun getModelInfo(modelId: String): ModelInfo {
        return when (modelId) {
            MODEL_QWEN_0_5B -> ModelInfo(
                id = MODEL_QWEN_0_5B,
                name = "Qwen 0.5B",
                description = "Balanced performance and quality",
                url = QWEN_0_5B_URL,
                sizeBytes = 500L * 1024 * 1024,  // 500MB
                tokensPerSecond = 25,
                recommended = true
            )

            MODEL_QWEN_1_5B -> ModelInfo(
                id = MODEL_QWEN_1_5B,
                name = "Qwen 1.5B",
                description = "Higher quality, slower",
                url = QWEN_1_5B_URL,
                sizeBytes = 1500L * 1024 * 1024,  // 1.5GB
                tokensPerSecond = 15,
                recommended = false
            )

            MODEL_SMOLLM_360M -> ModelInfo(
                id = MODEL_SMOLLM_360M,
                name = "SmolLM 360M",
                description = "Fastest, smallest model",
                url = SMOLLM_360M_URL,
                sizeBytes = 200L * 1024 * 1024,  // 200MB
                tokensPerSecond = 40,
                recommended = false
            )

            else -> throw IllegalArgumentException("Unknown model: $modelId")
        }
    }
}

/**
 * Model download progress states.
 */
sealed class ModelDownloadProgress {
    data class Started(val modelId: String) : ModelDownloadProgress()
    data class Registered(val registeredId: String) : ModelDownloadProgress()
    data class Downloading(
        val progress: Float,  // 0.0 to 1.0
        val totalBytes: Long,
        val downloadedBytes: Long
    ) : ModelDownloadProgress() {
        val progressPercentage: Int
            get() = (progress * 100).toInt()

        val downloadedMB: Float
            get() = downloadedBytes / (1024f * 1024f)

        val totalMB: Float
            get() = totalBytes / (1024f * 1024f)
    }
    data class Complete(val modelId: String) : ModelDownloadProgress()
    data class Failed(val error: String) : ModelDownloadProgress()
}

/**
 * Model information.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
    val tokensPerSecond: Int,
    val recommended: Boolean
) {
    val sizeMB: Float
        get() = sizeBytes / (1024f * 1024f)
}