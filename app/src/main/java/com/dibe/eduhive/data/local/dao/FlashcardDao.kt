package com.dibe.eduhive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dibe.eduhive.data.local.entity.FlashcardEntity

@Dao
interface FlashcardDao {

    @Insert
    suspend fun insertAll(cards: List<FlashcardEntity>)

    @Query("""
        SELECT * FROM flashcards 
        WHERE leitnerBox <= :maxBox 
        ORDER BY lastSeenAt ASC
    """)
    suspend fun getDue(maxBox: Int): List<FlashcardEntity>

    @Query("""
        UPDATE flashcards 
        SET leitnerBox = :box, lastSeenAt = :time 
        WHERE flashcardId = :id
    """)
    suspend fun updateLeitner(id: String, box: Int, time: Long)

    @Query("SELECT * FROM flashcards WHERE conceptId = :conceptId")
    suspend fun getForConcept(conceptId: String): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE flashcardId = :id")
    suspend fun getById(id: String): FlashcardEntity?

    @Query("SELECT COUNT(*) FROM flashcards WHERE conceptId = :conceptId")
    suspend fun getCountForConcept(conceptId: String): Int

    @Query("DELETE FROM flashcards WHERE conceptId = :conceptId")
    suspend fun deleteAllForConcept(conceptId: String)
}
