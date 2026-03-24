package com.dibe.eduhive.data.source.ai

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.dibe.eduhive.data.source.online.Downloader
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Model Manager using MediaPipe LLM Inference.
 *
 * Handles model lifecycle and provides a safe interface for text generation
 * that avoids native crashes by chunking large inputs.
 */
@Singleton
class AIModelManager @Inject constructor(
    private val context: Context,
    private val modelPreferences: ModelPreferences,
    private val downloader: Downloader
) {

    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    companion object {
        const val TAG = "AIModelManager"

        const val MODEL_GEMMA3_270M = "gemma3-270m"
        const val MODEL_QWEN = "Qwen2.5-0.5B"
        const val MODEL_SMOLLM_135M = "SmolLM-135M"

        private const val QWEN_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
        private const val SMOLLM_135M_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
        private const val GEMMA3_270M_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/gemma3-270m-it-q4_0-web.task?download=true"

        // 🛡️ SAFETY LIMITS
        private const val MAX_CONTEXT_TOKENS = 2048
        // Safe input limit to prevent SIGSEGV in the native engine.
        // 2048 tokens is ~8000 chars. We use 6000 to leave room for the response.
        const val MAX_INPUT_CHARS = 6000 
    }

    private var llmInference: LlmInference? = null
    private val inferenceMutex = Mutex()

    fun hasModelReady(): Boolean {
        val modelId = modelPreferences.getActiveModel() ?: return false
        val modelFile = getModelFile(modelId)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun getRecommendedModel(): ModelInfo {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)

        return when {
            maxMemoryMB > 4000 -> getModelInfo(MODEL_GEMMA3_270M)
            maxMemoryMB > 2000 -> getModelInfo(MODEL_QWEN)
            else -> getModelInfo(MODEL_SMOLLM_135M)
        }
    }

    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            getModelInfo(MODEL_SMOLLM_135M),
            getModelInfo(MODEL_QWEN),
            getModelInfo(MODEL_GEMMA3_270M)
        )
    }

    fun downloadModel(modelId: String): Flow<ModelDownloadProgress> = flow {
        val modelInfo = getModelInfo(modelId)
        val modelFile = getModelFile(modelId)

        emit(ModelDownloadProgress.Started(modelId))

        try {
            if (modelFile.exists() && modelFile.length() > 0) {
                modelPreferences.setModelDownloaded(modelId, true)
                modelPreferences.setActiveModel(modelId)
                emit(ModelDownloadProgress.Complete(modelId))
                return@flow
            }

            val downloadId = downloader.downloadFile(modelInfo.url, "${modelId}.task")
            emit(ModelDownloadProgress.Registered(downloadId.toString()))

            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager?.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0
                            emit(ModelDownloadProgress.Downloading(progress, bytesTotal, bytesDownloaded))
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            modelPreferences.setModelDownloaded(modelId, true)
                            modelPreferences.setActiveModel(modelId)
                            emit(ModelDownloadProgress.Complete(modelId))
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            emit(ModelDownloadProgress.Failed("Download failed"))
                        }
                    }
                } else {
                    downloading = false
                    emit(ModelDownloadProgress.Failed("Download task not found"))
                }
                cursor?.close()
                if (downloading) delay(1000)
            }
        } catch (e: Exception) {
            emit(ModelDownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            try {
                val modelFile = getModelFile(modelId)
                if (!modelFile.exists()) return@withLock Result.failure(Exception("Model file not found"))

                llmInference?.close()

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_CONTEXT_TOKENS)
                    .setMaxTopK(40)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                modelPreferences.setActiveModel(modelId)
                
                Log.d(TAG, "Model $modelId loaded successfully.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                Result.failure(e)
            }
        }
    }

    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inference = llmInference ?: return@withContext Result.failure(Exception("Model not loaded"))

            if (prompt.length <= MAX_INPUT_CHARS) {
                val response = inferenceMutex.withLock {
                    inference.generateResponse(prompt)
                }
                return@withContext Result.success(response)
            }

            val chunks = prompt.chunked(MAX_INPUT_CHARS)
            val combinedResults = StringBuilder()

            chunks.forEach { chunk ->
                val response = inferenceMutex.withLock {
                    inference.generateResponse(chunk)
                }
                combinedResults.append(response).append("\n\n")
                delay(50) 
            }

            Result.success(combinedResults.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            if (e.message?.contains("OUT_OF_RANGE") == true) unloadModel()
            Result.failure(e)
        }
    }

    fun generateStreaming(prompt: String): Flow<GenerationResult> = flow {
        val inference = llmInference ?: run {
            emit(GenerationResult.Error(Exception("Model not loaded")))
            return@flow
        }

        val chunks = prompt.chunked(MAX_INPUT_CHARS)
        val totalChunks = chunks.size
        val fullResponse = StringBuilder()

        try {
            chunks.forEachIndexed { index, chunk ->
                val response = inferenceMutex.withLock {
                    inference.generateResponse(chunk)
                }
                fullResponse.append(response).append("\n")
                emit(GenerationResult.Progress(index, totalChunks, index + 1, response))
                delay(50)
            }
            emit(GenerationResult.Success(fullResponse.toString().trim(), totalChunks, totalChunks))
        } catch (e: Exception) {
            emit(GenerationResult.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun processDocumentBatched(
        pages: List<String>,
        operation: (String) -> String
    ): List<String> = withContext(Dispatchers.IO) {
        pages.map { page ->
            try {
                val inference = llmInference ?: return@map "[Error: Model not loaded]"
                val prompt = operation(page)
                val safePrompt = if (prompt.length > MAX_INPUT_CHARS) prompt.take(MAX_INPUT_CHARS) else prompt
                
                inferenceMutex.withLock {
                    inference.generateResponse(safePrompt)
                }
            } catch (e: Exception) {
                "[Error: ${e.message}]"
            }
        }
    }

    fun isModelLoaded(): Boolean = llmInference != null

    fun unloadModel(): Result<Unit> {
        return try {
            llmInference?.close()
            llmInference = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveModel(): String? = modelPreferences.getActiveModel()

    private fun getModelFile(modelId: String): File {
        val externalFilesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        return File(externalFilesDir, "${modelId}.task")
    }

    private fun getModelInfo(modelId: String): ModelInfo {
        return when (modelId) {
            MODEL_QWEN -> ModelInfo(MODEL_QWEN, "Qwen 2.5 0.5B", "Best quality, slower", QWEN_URL, 547_000_000L, 30, true)
            MODEL_GEMMA3_270M -> ModelInfo(MODEL_GEMMA3_270M, "Gemma 3 270M", "Balanced performance", GEMMA3_270M_URL, 249_000_000L, 20, false)
            MODEL_SMOLLM_135M -> ModelInfo(MODEL_SMOLLM_135M, "SmolLM 135M", "Fastest, smallest", SMOLLM_135M_URL, 167_000_000L, 50, false)
            else -> throw IllegalArgumentException("Unknown model: $modelId")
        }
    }
}

sealed class ModelDownloadProgress {
    data class Started(val modelId: String) : ModelDownloadProgress()
    data class Registered(val registeredId: String) : ModelDownloadProgress()
    data class Downloading(val progressPercentage: Int, val totalBytes: Long, val downloadedBytes: Long) : ModelDownloadProgress() {
        val totalMB: Float get() = totalBytes / (1024f * 1024f)
        val downloadedMB: Float get() = downloadedBytes / (1024f * 1024f)
    }
    data class Complete(val modelId: String) : ModelDownloadProgress()
    data class Failed(val error: String) : ModelDownloadProgress()
}

data class ModelInfo(val id: String, val name: String, val description: String, val url: String, val sizeBytes: Long, val tokensPerSecond: Int, val recommended: Boolean) {
    val sizeMB: Float get() = sizeBytes / (1024f * 1024f)
}

sealed class GenerationResult {
    data class Success(val text: String, val chunksProcessed: Int, val totalChunks: Int) : GenerationResult()
    data class Progress(val chunkIndex: Int, val totalChunks: Int, val completedChunks: Int, val partialResult: String) : GenerationResult()
    data class Error(val exception: Throwable) : GenerationResult()
}
