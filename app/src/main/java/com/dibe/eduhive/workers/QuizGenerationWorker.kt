package com.dibe.eduhive.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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

    override suspend fun doWork(): Result {
        val conceptIds = inputData.getStringArray(KEY_CONCEPT_IDS)?.toList()
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing conceptIds"))
        val hiveId = inputData.getString(KEY_HIVE_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing hiveId"))

        // Show foreground notification
        setForeground(createForegroundInfo(0, conceptIds.size))

        return try {
            val total = conceptIds.size
            setProgress(workDataOf(KEY_COMPLETED to 0, KEY_TOTAL to total))

            var completed = 0
            for (conceptId in conceptIds) {
                val concept = conceptRepository.getConceptById(conceptId) ?: continue

                // Update notification for current progress
                setForeground(createForegroundInfo(completed, total, concept.name))

                quizRepository.generateQuizForConcept(
                    conceptId = conceptId,
                    conceptName = concept.name,
                    conceptDescription = concept.description,
                    questionCount = 3
                )

                completed++
                setProgress(workDataOf(KEY_COMPLETED to completed, KEY_TOTAL to total))
            }

            Result.success(workDataOf(KEY_HIVE_ID to hiveId, KEY_STATUS to STATUS_COMPLETE))
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Quiz generation failed")))
            }
        }
    }

    private fun createForegroundInfo(completed: Int, total: Int, currentConcept: String? = null): ForegroundInfo {
        val content = if (currentConcept != null) {
            "Generating quiz for: $currentConcept ($completed/$total)"
        } else {
            "Generating quizzes... ($completed/$total)"
        }

        val notification = NotificationHelper.getBaseNotification(
            context,
            "Study Hive: Quiz Generation",
            content
        ).setProgress(total, completed, false).build()

        return ForegroundInfo(NotificationHelper.NOTIFICATION_ID, notification)
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
