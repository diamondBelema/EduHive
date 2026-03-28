package com.dibe.eduhive.manager

import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dibe.eduhive.workers.FlashcardGenerationWorker
import com.dibe.eduhive.workers.MaterialProcessingWorker
import com.dibe.eduhive.workers.QuizGenerationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalTaskManager @Inject constructor(
    private val workManager: WorkManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val activeTasks: Flow<List<TaskProgress>> = combine(
        workManager.getWorkInfosByTagLiveData("material_processing").asFlow(),
        workManager.getWorkInfosByTagLiveData("flashcard_generation").asFlow(),
        workManager.getWorkInfosByTagLiveData("quiz_generation").asFlow()
    ) { materials, flashcards, quizzes ->
        val allInfos = materials + flashcards + quizzes
        
        allInfos
            .filter { info ->
                info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
            }
            .map { info ->
                val type = when {
                    info.tags.contains("material_processing") -> TaskType.MATERIAL
                    info.tags.contains("flashcard_generation") -> TaskType.FLASHCARD
                    info.tags.contains("quiz_generation") -> TaskType.QUIZ
                    else -> TaskType.UNKNOWN
                }

                // Workers always receive hiveId through inputData; progress can be empty early on.
                val hiveId = info.progress.getString(MaterialProcessingWorker.KEY_HIVE_ID)
                    ?: info.progress.getString(FlashcardGenerationWorker.KEY_HIVE_ID)
                    ?: info.outputData.getString(MaterialProcessingWorker.KEY_HIVE_ID)
                    ?: info.outputData.getString(FlashcardGenerationWorker.KEY_HIVE_ID)
                    ?: info.id.toString()

                val (progress, total) = when (type) {
                    TaskType.MATERIAL -> {
                        val p = info.progress.getInt(MaterialProcessingWorker.KEY_PROGRESS, 0)
                        p to 100
                    }
                    TaskType.FLASHCARD -> {
                        val completed = info.progress.getInt(FlashcardGenerationWorker.KEY_COMPLETED, 0)
                        val expected = info.progress.getInt(FlashcardGenerationWorker.KEY_TOTAL, 0)
                        completed to expected
                    }
                    TaskType.QUIZ -> {
                        val completed = info.progress.getInt(QuizGenerationWorker.KEY_COMPLETED, 0)
                        val expected = info.progress.getInt(QuizGenerationWorker.KEY_TOTAL, 0)
                        completed to expected
                    }
                    TaskType.UNKNOWN -> 0 to 100
                }

                val title = when (type) {
                    TaskType.MATERIAL -> {
                        val materialTitle = info.progress.getString(MaterialProcessingWorker.KEY_TITLE)
                        if (materialTitle.isNullOrBlank()) "Adding Material" else "Adding $materialTitle"
                    }
                    TaskType.FLASHCARD -> "Generating Flashcards"
                    TaskType.QUIZ -> "Generating Quiz"
                    TaskType.UNKNOWN -> "Background Task"
                }

                val status = info.progress.getString(MaterialProcessingWorker.KEY_STATUS)
                    ?: when (info.state) {
                        WorkInfo.State.ENQUEUED -> "Queued..."
                        WorkInfo.State.RUNNING -> "Running..."
                        else -> "Queued..."
                    }

                TaskProgress(
                    id = info.id.toString(),
                    hiveId = hiveId,
                    type = type,
                    title = title,
                    status = status,
                    progress = if (total > 0) progress.toFloat() / total else 0f,
                    isIndeterminate = info.state == WorkInfo.State.ENQUEUED || total <= 0
                )
            }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
}

enum class TaskType { MATERIAL, FLASHCARD, QUIZ, UNKNOWN }

data class TaskProgress(
    val id: String,
    val hiveId: String,
    val type: TaskType,
    val title: String,
    val status: String,
    val progress: Float,
    val isIndeterminate: Boolean
)
