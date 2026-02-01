package com.dibe.eduhive.data.source.local

import com.dibe.eduhive.data.local.dao.QuizDao
import com.dibe.eduhive.data.local.entity.QuizEntity
import com.dibe.eduhive.data.local.entity.QuizQuestionEntity
import jakarta.inject.Inject


/**
 * Local data source for Quiz operations.
 */
class QuizLocalDataSource @Inject constructor(
    private val quizDao: QuizDao
) {
    suspend fun insert(quiz: QuizEntity) =
        quizDao.insert(quiz)

    suspend fun insertQuestions(questions: List<QuizQuestionEntity>) =
        quizDao.insertQuestions(questions)

    suspend fun getForConcept(conceptId: String) =
        quizDao.getForConcept(conceptId)

    suspend fun getQuestionsForQuiz(quizId: String) =
        quizDao.getQuestionsForQuiz(quizId)

    suspend fun getQuizWithQuestions(quizId: String) =
        quizDao.getQuizWithQuestions(quizId)

    suspend fun delete(quizId: String) =
        quizDao.delete(quizId)
}