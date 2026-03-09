package com.dibe.eduhive.data.source.ai

import android.content.Context
import android.util.Log
import com.dibe.eduhive.R
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.ketch.DownloadConfig
import com.ketch.Ketch
import com.ketch.NotificationConfig
import com.ketch.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Model Manager using MediaPipe + Ketch.
 *
 * - MediaPipe for inference
 * - Ketch for downloading models with progress
 */
@Singleton
class AIModelManager @Inject constructor(
    private val context: Context,
    private val modelPreferences: ModelPreferences
) {

    companion object {
        const val TAG = "AIModelManager"

        // Available models
        const val MODEL_GEMMA3_270M = "gemma3-270m"
        const val MODEL_QWEN = "Qwen2.5-0.5B"
        const val MODEL_SMOLLM_135M = "SmolLM-135M"

        // HuggingFace URLs
        private const val QWEN_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
        private const val SMOLLM_135M_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
        private const val GEMMA3_270M_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/gemma3-270m-it-q4_0-web.task?download=true"
    }

    private var llmInference: LlmInference? = null

    // Initialize Ketch with LONGER timeout
    private var ketch: Ketch = Ketch.builder()
        .setNotificationConfig(
            config = NotificationConfig(
                enabled = true,
                smallIcon = R.drawable.ic_launcher_foreground
            )
        ).setDownloadConfig(
            config = DownloadConfig(
                connectTimeOutInMs = 60_000L, //Default: 10000L
                readTimeOutInMs = 300_000L //Default: 10000L
            )
        )
        .build(context)

    /**
     * Check if model is ready.
     */
    suspend fun hasModelReady(): Boolean {
        val modelId = modelPreferences.getActiveModel() ?: return false
        val modelFile = getModelFile(modelId)
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Get recommended model.
     */
    fun getRecommendedModel(): ModelInfo {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)

        return when {
            maxMemoryMB > 4000 -> getModelInfo(MODEL_GEMMA3_270M)
            maxMemoryMB > 2000 -> getModelInfo(MODEL_QWEN)
            else -> getModelInfo(MODEL_SMOLLM_135M)
        }
    }

    /**
     * Get available models.
     */
    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            getModelInfo(MODEL_SMOLLM_135M),  // Smallest first
            getModelInfo(MODEL_QWEN),
            getModelInfo(MODEL_GEMMA3_270M)
        )
    }

    /**
     * Download model using Ketch.
     */
    fun downloadModel(modelId: String): Flow<ModelDownloadProgress> = flow {
        val modelInfo = getModelInfo(modelId)
        val modelFile = getModelFile(modelId)

        emit(ModelDownloadProgress.Started(modelId))

        try {
            // Create models directory if needed
            modelFile.parentFile?.mkdirs()

            // Check if already downloaded
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Model already exists: ${modelFile.absolutePath}")
                modelPreferences.setModelDownloaded(modelId, true)
                modelPreferences.setActiveModel(modelId)
                emit(ModelDownloadProgress.Complete(modelId))
                return@flow
            }

            emit(ModelDownloadProgress.Registered(modelId))

            Log.d(TAG, "Starting download from: ${modelInfo.url}")
            Log.d(TAG, "Saving to: ${modelFile.absolutePath}")

            // Start download
            val downloadId = ketch.download(
                url = modelInfo.url,
                path = modelFile.parent!!,
                fileName = modelFile.name,
                tag = modelId,  // Add tag for easier identification
                metaData = modelInfo.name  // Add metadata
            )

            Log.d(TAG, "Download started with ID: $downloadId")

            // Observe download progress
            ketch.observeDownloadById(downloadId).collect { downloadModel ->
                Log.d(TAG, "Status: ${downloadModel.status}, Progress: ${downloadModel.progress}%")

                when (downloadModel.status) {
                    Status.QUEUED -> {
                        Log.d(TAG, "Download queued")
                        emit(ModelDownloadProgress.Downloading(0, modelInfo.sizeBytes, 0))
                    }

                    Status.STARTED -> {
                        Log.d(TAG, "Download started")
                        emit(ModelDownloadProgress.Downloading(0, modelInfo.sizeBytes, 0))
                    }

                    Status.PROGRESS -> {
                        val progress = downloadModel.progress
                        val totalBytes = downloadModel.total
                        val downloadedBytes = (totalBytes * progress / 100f).toLong()

                        Log.d(TAG, "Downloading: $progress% (${downloadedBytes / (1024 * 1024)}MB / ${totalBytes / (1024 * 1024)}MB)")

                        emit(
                            ModelDownloadProgress.Downloading(
                                progressPercentage = progress,
                                totalBytes = totalBytes,
                                downloadedBytes = downloadedBytes
                            )
                        )
                    }

                    Status.SUCCESS -> {
                        Log.d(TAG, "Download complete!")

                        // Verify file exists and has content
                        if (modelFile.exists() && modelFile.length() > 0) {
                            Log.d(TAG, "File verified: ${modelFile.length() / (1024 * 1024)}MB")
                            modelPreferences.setModelDownloaded(modelId, true)
                            modelPreferences.setActiveModel(modelId)
                            emit(ModelDownloadProgress.Complete(modelId))
                        } else {
                            Log.e(TAG, "File missing or empty after download!")
                            emit(ModelDownloadProgress.Failed("Downloaded file is empty or missing"))
                        }
                    }

                    Status.FAILED -> {
                        val reason = downloadModel.failureReason ?: "Unknown error"
                        Log.e(TAG, "Download failed: $reason")

                        // Clean up failed download
                        if (modelFile.exists()) {
                            modelFile.delete()
                            Log.d(TAG, "Cleaned up failed download")
                        }

                        // Provide helpful error message
                        val errorMessage = when {
                            reason.contains("timeout", ignoreCase = true) ->
                                "Download timed out. Check your internet connection and try again."
                            reason.contains("network", ignoreCase = true) ->
                                "Network error. Please check your connection."
                            reason.contains("space", ignoreCase = true) ->
                                "Not enough storage space. Free up ${modelInfo.sizeMB.toInt()}MB and try again."
                            else -> "Download failed: $reason"
                        }

                        emit(ModelDownloadProgress.Failed(errorMessage))
                    }

                    Status.PAUSED -> {
                        Log.d(TAG, "Download paused")
                    }

                    Status.CANCELLED -> {
                        Log.d(TAG, "Download cancelled")
                        if (modelFile.exists()) {
                            modelFile.delete()
                        }
                        emit(ModelDownloadProgress.Failed("Download cancelled"))
                    }

                    else -> {
                        Log.d(TAG, "Unknown status: ${downloadModel.status}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download exception", e)

            // Clean up on error
            if (modelFile.exists()) {
                modelFile.delete()
            }

            emit(ModelDownloadProgress.Failed(
                e.message ?: "Download failed with unknown error"
            ))
        }
    }

    /**
     * Load model into memory using MediaPipe.
     */
    suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = getModelFile(modelId)

            if (!modelFile.exists() || modelFile.length() == 0L) {
                return@withContext Result.failure(
                    IllegalStateException("Model not downloaded or file is empty: $modelId")
                )
            }

            Log.d(TAG, "Loading model from: ${modelFile.absolutePath}")
            Log.d(TAG, "File size: ${modelFile.length() / (1024 * 1024)}MB")

            // Unload existing model
            llmInference?.close()

            // Create MediaPipe LLM options
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setMaxTopK(40)
                .build()

            // Load model
            llmInference = LlmInference.createFromOptions(context, options)

            modelPreferences.setActiveModel(modelId)

            Log.d(TAG, "Model loaded successfully!")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            Result.failure(e)
        }
    }

    /**
     * Check if model is loaded.
     */
    suspend fun isModelLoaded(): Boolean {
        return llmInference != null
    }

    /**
     * Unload model from memory.
     */
    suspend fun unloadModel(): Result<Unit> {
        return try {
            llmInference?.close()
            llmInference = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate text using MediaPipe.
     */
    suspend fun generate(
        prompt: String,
        temperature: Float = 0.8f,
        maxTokens: Int = 512
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inference = llmInference
                ?: return@withContext Result.failure(
                    IllegalStateException("No model loaded")
                )

            Log.d(TAG, "Generating response...")
            val response = inference.generateResponse(prompt)
            Log.d(TAG, "Generation complete: ${response.length} chars")

            Result.success(response)

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get active model ID.
     */
    fun getActiveModel(): String? {
        return modelPreferences.getActiveModel()
    }

    /**
     * Cancel ongoing download.
     */
    suspend fun cancelDownload(modelId: String) {
        // Find and cancel the download
        val modelFile = getModelFile(modelId)
        if (modelFile.exists() && !modelPreferences.isModelDownloaded(modelId)) {
            modelFile.delete()
            Log.d(TAG, "Cancelled and cleaned up incomplete download")
        }
    }

    /**
     * Get model file path.
     */
    private fun getModelFile(modelId: String): File {
        return File(context.filesDir, "models/${modelId}.task")  // Changed to .task extension
    }

    /**
     * Get model info.
     */
    private fun getModelInfo(modelId: String): ModelInfo {
        return when (modelId) {
            MODEL_QWEN -> ModelInfo(
                id = MODEL_QWEN,
                name = "Qwen 2.5 0.5B",
                description = "Best quality, slower",
                url = QWEN_URL,
                sizeBytes = 547_000_000L,  // 547MB
                tokensPerSecond = 30,
                recommended = true
            )

            MODEL_GEMMA3_270M -> ModelInfo(
                id = MODEL_GEMMA3_270M,
                name = "Gemma 3 270M",
                description = "Balanced performance",
                url = GEMMA3_270M_URL,
                sizeBytes = 249_000_000L,  // 249MB
                tokensPerSecond = 20,
                recommended = false
            )

            MODEL_SMOLLM_135M -> ModelInfo(
                id = MODEL_SMOLLM_135M,
                name = "SmolLM 135M",
                description = "Fastest, smallest",
                url = SMOLLM_135M_URL,
                sizeBytes = 167_000_000L,  // 167MB
                tokensPerSecond = 50,
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
        val progressPercentage: Int,
        val totalBytes: Long,
        val downloadedBytes: Long
    ) : ModelDownloadProgress() {
        val totalMB: Float get() = totalBytes / (1024f * 1024f)
        val downloadedMB: Float get() = downloadedBytes / (1024f * 1024f)
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
    val sizeMB: Float get() = sizeBytes / (1024f * 1024f)
}