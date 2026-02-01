package com.dibe.eduhive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.dibe.eduhive.data.local.entity.QuizEntity
import com.dibe.eduhive.data.local.entity.QuizQuestionEntity
import com.dibe.eduhive.data.local.entity.QuizWithQuestions


@Dao
interface QuizDao {

    @Insert
    suspend fun insert(quiz: QuizEntity)

    @Insert
    suspend fun insertQuestions(questions: List<QuizQuestionEntity>)

    @Query("SELECT * FROM quizzes WHERE conceptId = :conceptId")
    suspend fun getForConcept(conceptId: String): List<QuizEntity>

    @Transaction
    @Query("SELECT * FROM quizzes WHERE quizId = :quizId")
    suspend fun getQuizWithQuestions(quizId: String): QuizWithQuestions?

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId")
    suspend fun getQuestionsForQuiz(quizId: String): List<QuizQuestionEntity>

    @Query("DELETE FROM quizzes WHERE quizId = :quizId")
    suspend fun delete(quizId: String)
}
