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

    suspend fun getDue(maxBox: Int) =
        flashcardDao.getDue(maxBox)

    suspend fun getDueForHive(hiveId: String, maxBox: Int) =
        flashcardDao.getDueForHive(hiveId, maxBox)

    suspend fun getAllForHiveStudy(hiveId: String) =
        flashcardDao.getAllForHiveStudy(hiveId)

    suspend fun updateLeitner(id: String, box: Int, time: Long) =
        flashcardDao.updateLeitner(id, box, time)

    suspend fun deleteById(flashcardId: String) =
        flashcardDao.deleteById(flashcardId)

    suspend fun deleteAllForConcept(conceptId: String) =
        flashcardDao.deleteAllForConcept(conceptId)
}