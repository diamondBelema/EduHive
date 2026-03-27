package com.dibe.eduhive.manager

import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.dibe.eduhive.workers.FlashcardGenerationWorker
import com.dibe.eduhive.workers.MaterialProcessingWorker
import com.dibe.eduhive.workers.QuizGenerationWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates scheduling of background flashcard, quiz, and material generation tasks.
 *
 * Uses WorkManager so tasks survive app restarts and benefit from automatic
 * retry with exponential backoff.
 */
@Singleton
class BackgroundGenerationManager @Inject constructor(
    private val workManager: WorkManager
) {

    /**
     * Schedules a background task to process new educational material.
     */
    fun scheduleMaterialProcessing(
        uri: Uri,
        hiveId: String,
        title: String,
        hiveContext: String = ""
    ): UUID {
        val workRequest = OneTimeWorkRequestBuilder<MaterialProcessingWorker>()
            .setInputData(
                workDataOf(
                    MaterialProcessingWorker.KEY_URI to uri.toString(),
                    MaterialProcessingWorker.KEY_HIVE_ID to hiveId,
                    MaterialProcessingWorker.KEY_TITLE to title,
                    MaterialProcessingWorker.KEY_HIVE_CONTEXT to hiveContext
                )
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("material_processing")
            .build()

        workManager.enqueue(workRequest)
        return workRequest.id
    }

    /**
     * Schedules a background flashcard generation task for the given concepts.
     */
    fun scheduleFlashcardGeneration(hiveId: String, conceptIds: List<String>): UUID {
        val workRequest = OneTimeWorkRequestBuilder<FlashcardGenerationWorker>()
            .setInputData(
                workDataOf(
                    FlashcardGenerationWorker.KEY_CONCEPT_IDS to conceptIds.toTypedArray(),
                    FlashcardGenerationWorker.KEY_HIVE_ID to hiveId
                )
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("flashcard_generation")
            .build()

        workManager.enqueue(workRequest)
        return workRequest.id
    }

    /**
     * Schedules a background quiz generation task for the given concepts.
     */
    fun scheduleQuizGeneration(hiveId: String, conceptIds: List<String>): UUID {
        val workRequest = OneTimeWorkRequestBuilder<QuizGenerationWorker>()
            .setInputData(
                workDataOf(
                    QuizGenerationWorker.KEY_CONCEPT_IDS to conceptIds.toTypedArray(),
                    QuizGenerationWorker.KEY_HIVE_ID to hiveId
                )
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("quiz_generation")
            .build()

        workManager.enqueue(workRequest)
        return workRequest.id
    }
}
