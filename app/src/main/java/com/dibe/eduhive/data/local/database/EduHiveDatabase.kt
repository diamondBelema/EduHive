package com.dibe.eduhive.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dibe.eduhive.data.local.dao.*
import com.dibe.eduhive.data.local.entity.*

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
    version = 4
)
@TypeConverters(EnumConverters::class)
abstract class EduHiveDatabase : RoomDatabase() {

    abstract fun hiveDao(): HiveDao
    abstract fun materialDao(): MaterialDao
    abstract fun conceptDao(): ConceptDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizDao(): QuizDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        /** Add isArchived column (default 0 = not archived) to hives table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE hives ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Add iconName column to hives table. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE hives ADD COLUMN iconName TEXT NOT NULL DEFAULT 'School'"
                )
            }
        }

        /**
         * Add nextReviewAt column to flashcards table.
         * NULL = card has never been reviewed → always considered due.
         * Existing cards get NULL so they immediately surface for review.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE flashcards ADD COLUMN nextReviewAt INTEGER DEFAULT NULL"
                )
            }
        }
    }
}