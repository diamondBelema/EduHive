package com.dibe.eduhive.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.FlashcardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FlashcardGenerationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val flashcardRepository: FlashcardRepository,
    private val conceptRepository: ConceptRepository
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    override suspend fun doWork(): Result {
        val conceptIds = inputData.getStringArray(KEY_CONCEPT_IDS)?.toList()
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing conceptIds"))
        val hiveId = inputData.getString(KEY_HIVE_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing hiveId"))

        val total = conceptIds.size
        setProgress(workDataOf(KEY_COMPLETED to 0, KEY_TOTAL to total))
        postNotification(0, total)

        return try {
            var completed = 0
            for (conceptId in conceptIds) {
                val concept = conceptRepository.getConceptById(conceptId) ?: continue

                postNotification(completed, total, concept.name)

                flashcardRepository.generateFlashcardsForConcept(
                    conceptId = conceptId,
                    conceptName = concept.name,
                    conceptDescription = concept.description,
                    count = 3
                )

                completed++
                setProgress(workDataOf(KEY_COMPLETED to completed, KEY_TOTAL to total))
            }

            cancelNotification()
            Result.success(workDataOf(KEY_HIVE_ID to hiveId, KEY_STATUS to STATUS_COMPLETE))
        } catch (e: Exception) {
            cancelNotification()
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Flashcard generation failed")))
            }
        }
    }

    private fun postNotification(completed: Int, total: Int, currentConcept: String? = null) {
        val content = if (currentConcept != null) {
            "Generating cards for: $currentConcept ($completed/$total)"
        } else {
            "Generating flashcards... ($completed/$total)"
        }
        val notification = NotificationHelper.getBaseNotification(
            context,
            "Study Hive: Flashcard Generation",
            content
        ).setProgress(total, completed, completed == 0 && total == 0).build()
        notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
    }

    companion object {
        const val KEY_CONCEPT_IDS = "conceptIds"
        const val KEY_HIVE_ID = "hiveId"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val KEY_STATUS = "status"
        const val STATUS_COMPLETE = "FLASHCARDS_COMPLETE"
        private const val MAX_RETRIES = 3
    }
}