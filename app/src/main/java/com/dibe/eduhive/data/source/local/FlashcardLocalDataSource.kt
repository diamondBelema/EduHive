package com.dibe.eduhive.data.source.local

import com.dibe.eduhive.data.local.dao.FlashcardDao
import com.dibe.eduhive.data.local.entity.FlashcardEntity
import jakarta.inject.Inject


/**
 * Local data source for Flashcard operations.
 */
class FlashcardLocalDataSource @Inject constructor(
    private val flashcardDao: FlashcardDao
) {
    suspend fun insertAll(flashcards: List<FlashcardEntity>) =
        flashcardDao.insertAll(flashcards)

    suspend fun getById(id: String) =
        flashcardDao.getById(id)

    suspend fun getForConcept(conceptId: String) =
        flashcardDao.getForConcept(conceptId)

    suspend fun getDue(maxBox: Int, now: Long) =
        flashcardDao.getDue(maxBox, now)

    suspend fun getDueForHive(hiveId: String, maxBox: Int, now: Long) =
        flashcardDao.getDueForHive(hiveId, maxBox, now)

    suspend fun getAllForHiveStudy(hiveId: String) =
        flashcardDao.getAllForHiveStudy(hiveId)

    suspend fun getAllForStudy() =
        flashcardDao.getAllForStudy()

    suspend fun updateLeitner(id: String, box: Int, lastSeenAt: Long, nextReviewAt: Long) =
        flashcardDao.updateLeitner(id, box, lastSeenAt, nextReviewAt)

    suspend fun deleteById(flashcardId: String) =
        flashcardDao.deleteById(flashcardId)

    suspend fun deleteAllForConcept(conceptId: String) =
        flashcardDao.deleteAllForConcept(conceptId)
}