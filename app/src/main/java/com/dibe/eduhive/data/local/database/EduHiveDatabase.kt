package com.dibe.eduhive.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dibe.eduhive.data.local.dao.*
import com.dibe.eduhive.data.local.entity.*
import jakarta.inject.Inject

@Database(
    entities = [
        HiveEntity::class,
        MaterialEntity::class,
        ConceptEntity::class,
        FlashcardEntity::class,
        QuizEntity::class,
        QuizQuestionEntity::class,
        ReviewEventEntity::class
    ],
    version = 1
)
@TypeConverters(EnumConverters::class)
abstract class EduHiveDatabase : RoomDatabase() {

    abstract fun hiveDao(): HiveDao
    abstract fun materialDao(): MaterialDao
    abstract fun conceptDao(): ConceptDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizDao(): QuizDao
    abstract fun reviewDao(): ReviewDao
}
