package com.dibe.eduhive.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dibe.eduhive.data.repository.ConceptExtractionProgress
import com.dibe.eduhive.data.repository.FlashcardGenerationProgress
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.model.MaterialType
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import com.dibe.eduhive.domain.repository.MaterialRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class MaterialProcessingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val fileDataSource: FileDataSource,
    private val materialRepository: MaterialRepository,
    private val conceptRepository: ConceptRepository,
    private val flashcardRepository: FlashcardRepository,
    private val aiDataSource: AIDataSource
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val hiveId = inputData.getString(KEY_HIVE_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val hiveContext = inputData.getString(KEY_HIVE_CONTEXT) ?: ""

        val uri = Uri.parse(uriString)

        // Promote to a foreground service immediately so the OS will not kill this
        // worker when the app moves to the background during long AI processing.
        val startNotification = NotificationHelper.getBaseNotification(
            context, "Analyzing \"$title\"", "Starting..."
        ).build()
        try {
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    NotificationHelper.NOTIFICATION_ID,
                    startNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                ForegroundInfo(NotificationHelper.NOTIFICATION_ID, startNotification)
            }
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "setForeground failed, worker may be killed in background: ${e.message}")
        }

        postNotification("Preparing \"$title\"", "Starting...", 0)
        aiDataSource.retainModelForPipeline()

        try {
            // 1. Extract text pages
            if (isStopped) return Result.failure()

            setProgress(workDataOf(KEY_HIVE_ID to hiveId, KEY_TITLE to title, KEY_STATUS to "Reading file...", KEY_PROGRESS to 5))

            val extractedPages = fileDataSource.extractTextPages(uri).getOrElse { error ->
                cancelNotification()
                return Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Failed to extract text"), KEY_HIVE_ID to hiveId))
            }

            if (extractedPages.isEmpty()) {
                cancelNotification()
                return Result.failure(workDataOf(KEY_ERROR to "No readable text found", KEY_HIVE_ID to hiveId))
            }

            val isSmallFile = extractedPages.size < SMALL_FILE_PAGE_THRESHOLD

            // 2. Save metadata
            val materialType = detectMaterialType(uri)
            val material = Material(
                id = UUID.randomUUID().toString(),
                hiveId = hiveId,
                title = title,
                type = materialType,
                localPath = uri.toString(),
                processed = false,
                createdAt = System.currentTimeMillis()
            )
            materialRepository.addMaterial(material)

            if (isStopped) return Result.failure()

            // 3. Extract concepts
            setProgress(workDataOf(KEY_HIVE_ID to hiveId, KEY_TITLE to title, KEY_STATUS to "Analyzing content...", KEY_PROGRESS to 15))
            postNotification("Analyzing \"$title\"", "Identifying concepts...", 15)

            var extractedConceptsList: List<com.dibe.eduhive.domain.model.Concept>? = null
            var extractionError: String? = null

            conceptRepository.extractConceptsFromPagesStreaming(
                pages = extractedPages,
                hiveId = hiveId,
                hiveContext = hiveContext
            ).collect { progress ->
                if (isStopped) return@collect

                when (progress) {
                    is ConceptExtractionProgress.Processing -> {
                        val p = 15 + (progress.percent * 0.35).toInt()
                        setProgress(workDataOf(KEY_HIVE_ID to hiveId, KEY_TITLE to title, KEY_PROGRESS to p, KEY_STATUS to "Analyzing... ${progress.percent}%"))
                        postNotification("Analyzing \"$title\"", "Concept extraction: ${progress.percent}%", p)
                    }
                    is ConceptExtractionProgress.Success -> {
                        extractedConceptsList = progress.concepts
                    }
                    is ConceptExtractionProgress.Error -> {
                        // Capture the error — don't crash, handle it after collect
                        extractionError = progress.message
                    }
                    else -> { /* Loading state — ignore */ }
                }
            }

            if (isStopped) return Result.failure()

            // Never retry — restarting from scratch is worse than a clean failure.
            // AiDataSource already handles partial success (some batches fail, some pass)
            // by emitting Success with whatever concepts it collected. So if we get here
            // with an empty list, the model truly produced nothing — fail cleanly.
            if (extractedConceptsList.isNullOrEmpty()) {
                cancelNotification()
                android.util.Log.w("MaterialWorker", "Concept extraction produced nothing: $extractionError")
                return Result.failure(workDataOf(
                    KEY_ERROR to (extractionError ?: "No concepts could be extracted from this document"),
                    KEY_HIVE_ID to hiveId
                ))
            }

            val finalConcepts = extractedConceptsList!!

            // 4. Generate flashcards
            var totalValid = 0
            val totalConcepts = finalConcepts.size
            val failedConcepts = mutableListOf<String>()

            for ((index, concept) in finalConcepts.withIndex()) {
                if (isStopped) return Result.failure(workDataOf(KEY_STATUS to "Cancelled", KEY_HIVE_ID to hiveId))

                val conceptProgress = 50 + ((index.toFloat() / totalConcepts) * 45).toInt()
                setProgress(workDataOf(
                    KEY_STATUS to "Concept ${index + 1}/$totalConcepts: ${concept.name}",
                    KEY_PROGRESS to conceptProgress,
                    KEY_HIVE_ID to hiveId,
                    KEY_TITLE to title,
                    KEY_VALID_COUNT to totalValid,
                    KEY_CURRENT_CONCEPT to (index + 1),
                    KEY_TOTAL_CONCEPTS to totalConcepts
                ))
                postNotification("Generating: $title", "${index + 1}/$totalConcepts: ${concept.name}", conceptProgress)

                try {
                    var cardsAdded = 0
                    android.util.Log.d("MaterialWorker", "▶ Flashcard gen start: '${concept.name}' id=${concept.id} skipValidation=$isSmallFile")
                    flashcardRepository.generateFlashcardsForConceptStreaming(
                        conceptId = concept.id,
                        conceptName = concept.name,
                        conceptDescription = concept.description ?: "",
                        count = 3,
                        skipValidation = isSmallFile
                    ).collect { progress ->
                        when (progress) {
                            is FlashcardGenerationProgress.Success -> {
                                cardsAdded = progress.flashcards.size
                                totalValid += cardsAdded
                                android.util.Log.d("MaterialWorker", "✓ Flashcard gen OK: '${concept.name}' → $cardsAdded cards (rejected=${progress.rejectedCount})")
                            }
                            is FlashcardGenerationProgress.Error -> {
                                android.util.Log.e("MaterialWorker", "✗ Flashcard gen ERROR for '${concept.name}': ${progress.message}")
                            }
                            is FlashcardGenerationProgress.Loading -> {
                                android.util.Log.d("MaterialWorker", "  Loading model for '${concept.name}'...")
                            }
                            else -> {}
                        }
                    }
                    if (cardsAdded == 0) {
                        android.util.Log.w("MaterialWorker", "⚠ Zero cards for '${concept.name}' — added to failed list")
                        failedConcepts.add(concept.name)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MaterialWorker", "✗ EXCEPTION generating cards for '${concept.name}': ${e.message}", e)
                    failedConcepts.add(concept.name)
                }
            }

            if (isStopped) return Result.failure(workDataOf(KEY_STATUS to "Cancelled", KEY_HIVE_ID to hiveId))

            // 5. Finalize
            materialRepository.markAsProcessed(material.id)

            val summary = if (failedConcepts.isEmpty()) {
                "Successfully created $totalValid flashcards from all $totalConcepts concepts."
            } else {
                "Created $totalValid cards. Skipped ${failedConcepts.size} concepts (low quality)."
            }

            setProgress(workDataOf(
                KEY_HIVE_ID to hiveId,
                KEY_TITLE to title,
                KEY_PROGRESS to 100,
                KEY_STATUS to "Complete!",
                KEY_VALID_COUNT to totalValid,
                KEY_TOTAL_CONCEPTS to totalConcepts,
                KEY_SUMMARY to summary
            ))

            val completionNotification = NotificationHelper.getBaseNotification(
                context, "Processing Complete", "Added $totalValid cards to \"$title\""
            ).setOngoing(false).setAutoCancel(true).build()
            notificationManager.notify(COMPLETION_NOTIFICATION_ID, completionNotification)
            cancelNotification()

            return Result.success(workDataOf(
                KEY_MATERIAL_ID to material.id,
                KEY_CONCEPTS_COUNT to totalConcepts,
                KEY_FLASHCARDS_COUNT to totalValid,
                KEY_SUMMARY to summary,
                KEY_HIVE_ID to hiveId
            ))

        } catch (e: Exception) {
            cancelNotification()
            return Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error"), KEY_HIVE_ID to hiveId))
        } finally {
            aiDataSource.releaseModelRef()
        }
    }

    private fun postNotification(title: String, status: String, progress: Int) {
        val notification = NotificationHelper.getBaseNotification(context, title, status)
            .setProgress(100, progress, progress == 0)
            .build()
        notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
    }

    private fun detectMaterialType(uri: Uri): MaterialType {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("pdf") == true -> MaterialType.PDF
            mimeType?.contains("powerpoint") == true || mimeType?.contains("presentation") == true -> MaterialType.SLIDES
            mimeType?.contains("text") == true -> MaterialType.TEXT
            else -> MaterialType.PDF
        }
    }

    companion object {
        private const val TAG = "MaterialWorker"

        const val KEY_URI = "uri"
        const val KEY_HIVE_ID = "hiveId"
        const val KEY_TITLE = "title"
        const val KEY_HIVE_CONTEXT = "hiveContext"

        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_SUMMARY = "summary"

        const val KEY_MATERIAL_ID = "materialId"
        const val KEY_CONCEPTS_COUNT = "conceptsCount"
        const val KEY_FLASHCARDS_COUNT = "flashcardsCount"

        const val KEY_VALID_COUNT = "validCount"
        const val KEY_REJECTED_COUNT = "rejectedCount"
        const val KEY_CURRENT_CONCEPT = "currentConcept"
        const val KEY_TOTAL_CONCEPTS = "totalConcepts"

        private const val SMALL_FILE_PAGE_THRESHOLD = 5
        private const val COMPLETION_NOTIFICATION_ID = NotificationHelper.NOTIFICATION_ID + 1
    }
}