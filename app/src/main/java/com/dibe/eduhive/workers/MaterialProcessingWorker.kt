package com.dibe.eduhive.workers

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dibe.eduhive.data.repository.ConceptExtractionProgress
import com.dibe.eduhive.data.repository.FlashcardGenerationProgress
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
    private val flashcardRepository: FlashcardRepository
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val hiveId = inputData.getString(KEY_HIVE_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val hiveContext = inputData.getString(KEY_HIVE_CONTEXT) ?: ""

        val uri = Uri.parse(uriString)

        // Post a plain (non-foreground) progress notification — safe to call from background
        postNotification("Processing \"$title\"", "Starting...", 0)

        try {
            // 1. Extract text pages
            setProgress(
                workDataOf(
                    KEY_HIVE_ID to hiveId,
                    KEY_TITLE to title,
                    KEY_STATUS to "Extracting text...",
                    KEY_PROGRESS to 10
                )
            )
            postNotification("Processing \"$title\"", "Extracting text...", 10)

            val extractedPages = fileDataSource.extractTextPages(uri).getOrElse { error ->
                cancelNotification()
                return Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Failed to extract text")))
            }

            if (extractedPages.isEmpty()) {
                cancelNotification()
                return Result.failure(workDataOf(KEY_ERROR to "No readable text found"))
            }

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
            setProgress(
                workDataOf(
                    KEY_HIVE_ID to hiveId,
                    KEY_TITLE to title,
                    KEY_STATUS to "Material saved",
                    KEY_PROGRESS to 20
                )
            )

            // 3. Extract concepts
            setProgress(
                workDataOf(
                    KEY_HIVE_ID to hiveId,
                    KEY_TITLE to title,
                    KEY_STATUS to "Analyzing content...",
                    KEY_PROGRESS to 30
                )
            )
            postNotification("Processing \"$title\"", "Analyzing content...", 30)

            var extractedConceptsList: List<com.dibe.eduhive.domain.model.Concept>? = null
            conceptRepository.extractConceptsFromPagesStreaming(
                pages = extractedPages,
                hiveId = hiveId,
                hiveContext = hiveContext
            ).collect { progress ->
                if (progress is ConceptExtractionProgress.Processing) {
                    val p = 30 + (progress.percent * 0.3).toInt()
                    setProgress(
                        workDataOf(
                            KEY_HIVE_ID to hiveId,
                            KEY_TITLE to title,
                            KEY_PROGRESS to p,
                            KEY_STATUS to "Analyzing... ${progress.percent}%"
                        )
                    )
                    postNotification("Processing \"$title\"", "Analyzing... ${progress.percent}%", p)
                } else if (progress is ConceptExtractionProgress.Success) {
                    extractedConceptsList = progress.concepts
                }
            }

            val finalConcepts = extractedConceptsList ?: run {
                cancelNotification()
                return Result.failure(workDataOf(KEY_ERROR to "No concepts extracted"))
            }

            // 4. Generate flashcards
            var totalValid = 0
            val totalConcepts = finalConcepts.size

            finalConcepts.forEachIndexed { index, concept ->
                val conceptProgress = 60 + ((index.toFloat() / totalConcepts) * 35).toInt()
                setProgress(workDataOf(
                    KEY_HIVE_ID to hiveId,
                    KEY_TITLE to title,
                    KEY_STATUS to "Generating cards for ${concept.name}",
                    KEY_PROGRESS to conceptProgress
                ))
                postNotification(
                    "Processing \"$title\"",
                    "Generating cards: ${concept.name} (${index + 1}/$totalConcepts)",
                    conceptProgress
                )

                flashcardRepository.generateFlashcardsForConceptStreaming(
                    conceptId = concept.id,
                    conceptName = concept.name,
                    conceptDescription = concept.description ?: "",
                    count = 3
                ).collect { progress ->
                    if (progress is FlashcardGenerationProgress.Success) {
                        totalValid += progress.flashcards.size
                    }
                }
            }

            // 5. Finalize
            materialRepository.markAsProcessed(material.id)
            setProgress(
                workDataOf(
                    KEY_HIVE_ID to hiveId,
                    KEY_TITLE to title,
                    KEY_PROGRESS to 100,
                    KEY_STATUS to "Complete"
                )
            )

            // Replace progress notification with a completion notification
            val completionNotification = NotificationHelper.getBaseNotification(
                context,
                "Processing Complete",
                "Added $totalValid cards to \"$title\""
            )
                .setOngoing(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false)
                .build()
            notificationManager.notify(COMPLETION_NOTIFICATION_ID, completionNotification)
            cancelNotification() // dismiss the progress one

            return Result.success(workDataOf(
                KEY_MATERIAL_ID to material.id,
                KEY_CONCEPTS_COUNT to finalConcepts.size,
                KEY_FLASHCARDS_COUNT to totalValid
            ))

        } catch (e: Exception) {
            cancelNotification()
            return Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
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
        const val KEY_URI = "uri"
        const val KEY_HIVE_ID = "hiveId"
        const val KEY_TITLE = "title"
        const val KEY_HIVE_CONTEXT = "hiveContext"

        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"

        const val KEY_MATERIAL_ID = "materialId"
        const val KEY_CONCEPTS_COUNT = "conceptsCount"
        const val KEY_FLASHCARDS_COUNT = "flashcardsCount"

        private const val COMPLETION_NOTIFICATION_ID = NotificationHelper.NOTIFICATION_ID + 1
    }
}