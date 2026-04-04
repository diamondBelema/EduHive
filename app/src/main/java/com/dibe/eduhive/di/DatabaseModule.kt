package com.dibe.eduhive.di


import android.content.Context
import androidx.room.Room
import com.dibe.eduhive.data.local.database.EduHiveDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): EduHiveDatabase =
        Room.databaseBuilder(
            context,
            EduHiveDatabase::class.java,
            "eduhive_database"
        )
            .addMigrations(
                EduHiveDatabase.MIGRATION_1_2,
                EduHiveDatabase.MIGRATION_2_3,
                EduHiveDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHiveDao(db: EduHiveDatabase) = db.hiveDao()
    @Provides fun provideMaterialDao(db: EduHiveDatabase) = db.materialDao()
    @Provides fun provideConceptDao(db: EduHiveDatabase) = db.conceptDao()
    @Provides fun provideFlashcardDao(db: EduHiveDatabase) = db.flashcardDao()
    @Provides fun provideQuizDao(db: EduHiveDatabase) = db.quizDao()
    @Provides fun provideReviewDao(db: EduHiveDatabase) = db.reviewDao()
}