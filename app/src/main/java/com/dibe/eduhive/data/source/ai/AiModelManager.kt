package com.dibe.eduhive.data.source.ai

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.dibe.eduhive.data.source.online.Downloader
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
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
 * that avoids native crashes and context instability.
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

        const val MODEL_GEMMA3_1B = "gemma3-1b"
        const val MODEL_GEMMA3_270M = "gemma3-270m"
        const val MODEL_QWEN = "Qwen2.5-0.5B"
        const val MODEL_SMOLLM_135M = "SmolLM-135M"

        private const val GEMMA3_1B_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/gemma3-1b-it-int4.task?download=true"
        private const val QWEN_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
        private const val SMOLLM_135M_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true"
        private const val GEMMA3_270M_URL = "https://huggingface.co/diamondbelema/edu-hive-llm-models/resolve/main/gemma3-270m-it-q4_0-web.task?download=true"




        // Input limits are enforced per-task in AiDataSource, not here.
        // The manager accepts whatever prompt it receives.
        const val MAX_INPUT_CHARS = Int.MAX_VALUE  // deprecated — use AiDataSource task limits
    }

    private var llmInference: LlmInference? = null
    private var currentConfig: GenerationConfig = GenerationConfig()
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
            maxMemoryMB > 6000 -> getModelInfo(MODEL_GEMMA3_1B)
            maxMemoryMB > 4000 -> getModelInfo(MODEL_QWEN)
            maxMemoryMB > 2000 -> getModelInfo(MODEL_GEMMA3_270M)
            else -> getModelInfo(MODEL_SMOLLM_135M)
        }
    }

    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            getModelInfo(MODEL_SMOLLM_135M),
            getModelInfo(MODEL_GEMMA3_270M),
            getModelInfo(MODEL_QWEN),
            getModelInfo(MODEL_GEMMA3_1B)
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

    /**
     * Load model engine.
     * Sessions are created per-request to apply config and prevent context accumulation.
     */
    suspend fun loadModel(modelId: String, config: GenerationConfig = GenerationConfig()): Result<Unit> = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            try {
                val modelFile = getModelFile(modelId)
                if (!modelFile.exists()) return@withLock Result.failure(Exception("Model file not found"))

                currentConfig = config
                llmInference?.close()
                llmInference = null

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(config.maxTokens)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)

                modelPreferences.setActiveModel(modelId)

                Log.d(TAG, "Model $modelId engine loaded. Session-based generation enabled.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Creates a new session with current configuration.
     * A fresh session for each generation ensures stateless behavior and stability.
     */
    private fun createSessionInternal(): LlmInferenceSession? {
        val engine = llmInference ?: return null
        return try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(currentConfig.temperature)
                .setTopK(currentConfig.topK)
                .setRandomSeed(currentConfig.randomSeed)
                .build()
            LlmInferenceSession.createFromOptions(engine, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            null
        }
    }

    /**
     * Generate response using a fresh session to apply parameters and avoid context pollution.
     */
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val engine = llmInference ?: return@withContext Result.failure(Exception("AI engine not loaded"))

            val safePrompt = if (prompt.length > MAX_INPUT_CHARS) {
                prompt.take(MAX_INPUT_CHARS).substringBeforeLast(' ')
            } else prompt

            val response = inferenceMutex.withLock {
                val session = createSessionInternal() ?: return@withLock "[Error: Session creation failed]"
                session.use { session ->
                    session.addQueryChunk(safePrompt)
                    session.generateResponse()
                }
            }

            if (response.startsWith("[Error:")) {
                return@withContext Result.failure(Exception(response))
            }

            Result.success(response)

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed, reinitializing model", e)
            unloadModel()
            delay(500)
            val activeModel = modelPreferences.getActiveModel()
            if (activeModel != null) loadModel(activeModel)
            Result.failure(e)
        }
    }

    /**
     * Streaming generation using a fresh session.
     */
    fun generateStreaming(prompt: String): Flow<GenerationResult> = flow {
        val engine = llmInference ?: run {
            emit(GenerationResult.Error(Exception("AI engine not loaded")))
            return@flow
        }

        val safePrompt = if (prompt.length > MAX_INPUT_CHARS) {
            prompt.take(MAX_INPUT_CHARS).substringBeforeLast(' ')
        } else prompt

        try {
            val response = inferenceMutex.withLock {
                val session = createSessionInternal() ?: throw Exception("Session creation failed")
                session.use { session ->
                    session.addQueryChunk(safePrompt)
                    session.generateResponse()
                }
            }
            emit(GenerationResult.Progress(0, 1, 1, response))
            emit(GenerationResult.Success(response.trim(), 1, 1))
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
                val engine = llmInference ?: return@map "[Error: Engine not loaded]"
                val prompt = operation(page)
                val safePrompt = if (prompt.length > MAX_INPUT_CHARS) prompt.take(MAX_INPUT_CHARS) else prompt

                inferenceMutex.withLock {
                    val session = createSessionInternal() ?: return@withLock "[Error: Session creation failed]"
                    session.use { session ->
                        session.addQueryChunk(safePrompt)
                        session.generateResponse()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Batch page failed", e)
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
            MODEL_GEMMA3_1B -> ModelInfo(MODEL_GEMMA3_1B, "Gemma 3 1B", "Best reasoning, most intelligent", GEMMA3_1B_URL, 555_000_000L, 15, false)
            MODEL_QWEN -> ModelInfo(MODEL_QWEN, "Qwen 2.5 0.5B", "Fast and high-quality", QWEN_URL, 547_000_000L, 30, true)
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