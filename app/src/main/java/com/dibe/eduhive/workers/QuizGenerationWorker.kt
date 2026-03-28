package com.dibe.eduhive.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dibe.eduhive.domain.repository.ConceptRepository
import com.dibe.eduhive.domain.repository.QuizRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class QuizGenerationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val quizRepository: QuizRepository,
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
        setProgress(
            workDataOf(
                KEY_HIVE_ID to hiveId,
                KEY_COMPLETED to 0,
                KEY_TOTAL to total,
                KEY_STATUS to "Queued..."
            )
        )
        postNotification(0, total)

        return try {
            var completed = 0
            for (conceptId in conceptIds) {
                val concept = conceptRepository.getConceptById(conceptId) ?: continue

                postNotification(completed, total, concept.name)

                quizRepository.generateQuizForConcept(
                    conceptId = conceptId,
                    conceptName = concept.name,
                    conceptDescription = concept.description,
                    questionCount = 3
                )

                completed++
                setProgress(
                    workDataOf(
                        KEY_HIVE_ID to hiveId,
                        KEY_COMPLETED to completed,
                        KEY_TOTAL to total,
                        KEY_STATUS to "Generating quiz..."
                    )
                )
            }

            cancelNotification()
            Result.success(workDataOf(KEY_HIVE_ID to hiveId, KEY_STATUS to STATUS_COMPLETE))
        } catch (e: Exception) {
            cancelNotification()
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Quiz generation failed")))
            }
        }
    }

    private fun postNotification(completed: Int, total: Int, currentConcept: String? = null) {
        val content = if (currentConcept != null) {
            "Generating quiz for: $currentConcept ($completed/$total)"
        } else {
            "Generating quizzes... ($completed/$total)"
        }
        val notification = NotificationHelper.getBaseNotification(
            context,
            "Study Hive: Quiz Generation",
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
        const val STATUS_COMPLETE = "QUIZ_COMPLETE"
        private const val MAX_RETRIES = 3
    }
}