package com.dibe.eduhive.data.source.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.Delegates

/**
 * MediaPipe-based AI Model Manager with HTTP download.
 *
 * Drop-in replacement for Run Anywhere SDK.
 * Provides same interface, same UX, but MORE RELIABLE!
 */
@Singleton
class AIModelManager @Inject constructor(
    private val context: Context,
    private val modelPreferences: ModelPreferences
) {

    companion object {
        const val TAG = "AIModelManager"

        // Available models
        const val MODEL_GEMMA_2B = "gemma-2b"
        const val MODEL_PHI_2 = "phi-2"
        const val MODEL_STABLELM = "stablelm-1.6b"

        // Direct download URLs (Google Cloud Storage)
        private const val GEMMA_2B_URL = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-gpu-int4/gemma-2b-it-gpu-int4.bin"
        private const val PHI_2_URL = "https://storage.googleapis.com/mediapipe-models/llm_inference/phi-2-gpu-int4/phi-2-gpu-int4.bin"
        private const val STABLELM_URL = "https://storage.googleapis.com/mediapipe-models/llm_inference/stablelm-2-zephyr-1_6b-int4/stablelm-2-zephyr-1_6b-int4.bin"
    }

    var topK by Delegates.notNull<Int>()
    private var llmInference: LlmInference? = null

    /**
     * Check if model is ready.
     * Same signature as Run Anywhere!
     */
    suspend fun hasModelReady(): Boolean {
        val modelId = modelPreferences.getActiveModel() ?: return false
        val modelFile = getModelFile(modelId)
        return modelFile.exists() && llmInference != null
    }

    /**
     * Get recommended model.
     */
    fun getRecommendedModel(): ModelInfo {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)

        return when {
            maxMemoryMB > 4000 -> getModelInfo(MODEL_GEMMA_2B)
            maxMemoryMB > 2000 -> getModelInfo(MODEL_PHI_2)
            else -> getModelInfo(MODEL_STABLELM)
        }
    }

    /**
     * Get available models.
     */
    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            getModelInfo(MODEL_STABLELM),  // Smallest first
            getModelInfo(MODEL_PHI_2),
            getModelInfo(MODEL_GEMMA_2B)
        )
    }

    /**
     * Download model with progress.
     * SAME INTERFACE as Run Anywhere!
     */
    fun downloadModel(modelId: String): Flow<ModelDownloadProgress> = flow {
        val modelInfo = getModelInfo(modelId)
        val modelFile = getModelFile(modelId)

        emit(ModelDownloadProgress.Started(modelId))

        try {
            // Create models directory
            modelFile.parentFile?.mkdirs()

            emit(ModelDownloadProgress.Registered(modelId))

            // Download file
            withContext(Dispatchers.IO) {
                val url = URL(modelInfo.url)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP $responseCode")
                }

                val totalBytes = connection.contentLengthLong
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(modelFile)

                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val progress = downloadedBytes.toFloat() / totalBytes
                    emit(
                        ModelDownloadProgress.Downloading(
                            progress = progress,
                            totalBytes = totalBytes,
                            downloadedBytes = downloadedBytes
                        )
                    )
                }

                outputStream.close()
                inputStream.close()
                connection.disconnect()
            }

            // Mark as downloaded
            modelPreferences.setModelDownloaded(modelId, true)
            modelPreferences.setActiveModel(modelId)

            emit(ModelDownloadProgress.Complete(modelId))

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)

            // Clean up partial download
            if (modelFile.exists()) {
                modelFile.delete()
            }

            emit(ModelDownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }

    /**
     * Load model into memory.
     * SAME SIGNATURE as Run Anywhere!
     */
    suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = getModelFile(modelId)

            if (!modelFile.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Model not downloaded: $modelId")
                )
            }

            Log.d(TAG, "Loading model from: ${modelFile.absolutePath}")

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

            Log.d(TAG, "Model loaded successfully")
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
     * Unload model.
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
     * Generate text.
     * Used internally by AIDataSource.
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

            val response = inference.generateResponse(prompt)
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
     * Get model file.
     */
    private fun getModelFile(modelId: String): File {
        return File(context.filesDir, "models/${modelId}.bin")
    }

    /**
     * Get model info.
     */
    private fun getModelInfo(modelId: String): ModelInfo {
        return when (modelId) {
            MODEL_GEMMA_2B -> ModelInfo(
                id = MODEL_GEMMA_2B,
                name = "Gemma 2B",
                description = "Best quality, slower",
                url = GEMMA_2B_URL,
                sizeBytes = 1_600_000_000L,  // 1.6GB
                tokensPerSecond = 20,
                recommended = false
            )

            MODEL_PHI_2 -> ModelInfo(
                id = MODEL_PHI_2,
                name = "Phi-2",
                description = "Balanced performance",
                url = PHI_2_URL,
                sizeBytes = 1_200_000_000L,  // 1.2GB
                tokensPerSecond = 30,
                recommended = true
            )

            MODEL_STABLELM -> ModelInfo(
                id = MODEL_STABLELM,
                name = "StableLM 1.6B",
                description = "Fastest, smallest",
                url = STABLELM_URL,
                sizeBytes = 800_000_000L,  // 800MB
                tokensPerSecond = 50,
                recommended = false
            )

            else -> throw IllegalArgumentException("Unknown model: $modelId")
        }
    }
}

/**
 * Model download progress - SAME AS RUN ANYWHERE!
 */
sealed class ModelDownloadProgress {
    data class Started(val modelId: String) : ModelDownloadProgress()
    data class Registered(val registeredId: String) : ModelDownloadProgress()
    data class Downloading(
        val progress: Float,  // 0.0 to 1.0
        val totalBytes: Long,
        val downloadedBytes: Long
    ) : ModelDownloadProgress() {
        val progressPercentage: Int get() = (progress * 100).toInt()
        val downloadedMB: Float get() = downloadedBytes / (1024f * 1024f)
        val totalMB: Float get() = totalBytes / (1024f * 1024f)
    }
    data class Complete(val modelId: String) : ModelDownloadProgress()
    data class Failed(val error: String) : ModelDownloadProgress()
}

/**
 * Model info - SAME AS RUN ANYWHERE!
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