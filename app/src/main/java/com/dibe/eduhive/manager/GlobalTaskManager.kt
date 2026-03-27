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
        val allInfos = (materials ?: emptyList()) + (flashcards ?: emptyList()) + (quizzes ?: emptyList())
        
        allInfos
            .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            .map { info ->
                val type = when {
                    info.tags.contains("material_processing") -> TaskType.MATERIAL
                    info.tags.contains("flashcard_generation") -> TaskType.FLASHCARD
                    info.tags.contains("quiz_generation") -> TaskType.QUIZ
                    else -> TaskType.UNKNOWN
                }
                
                val progress = info.progress.getInt(MaterialProcessingWorker.KEY_PROGRESS, 
                    info.progress.getInt(FlashcardGenerationWorker.KEY_COMPLETED, 0))
                val total = info.progress.getInt(FlashcardGenerationWorker.KEY_TOTAL, 100)
                
                val status = info.progress.getString(MaterialProcessingWorker.KEY_STATUS) ?: "Processing..."
                val title = info.progress.getString(MaterialProcessingWorker.KEY_TITLE) ?: when(type) {
                    TaskType.MATERIAL -> "Adding Material"
                    TaskType.FLASHCARD -> "Generating Flashcards"
                    TaskType.QUIZ -> "Generating Quiz"
                    else -> "Background Task"
                }

                TaskProgress(
                    id = info.id.toString(),
                    type = type,
                    title = title,
                    status = status,
                    progress = if (total > 0) progress.toFloat() / total else progress.toFloat() / 100,
                    isIndeterminate = progress == 0 && total == 0
                )
            }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
}

enum class TaskType { MATERIAL, FLASHCARD, QUIZ, UNKNOWN }

data class TaskProgress(
    val id: String,
    val type: TaskType,
    val title: String,
    val status: String,
    val progress: Float,
    val isIndeterminate: Boolean
)
