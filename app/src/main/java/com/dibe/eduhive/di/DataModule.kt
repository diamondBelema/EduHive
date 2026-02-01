package com.dibe.eduhive.di

import android.content.Context
import androidx.room.Room
import com.dibe.eduhive.data.local.dao.*
import com.dibe.eduhive.data.local.database.EduHiveDatabase
import com.dibe.eduhive.data.repository.*
import com.dibe.eduhive.data.source.*
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.data.source.local.ConceptLocalDataSource
import com.dibe.eduhive.data.source.local.FlashcardLocalDataSource
import com.dibe.eduhive.data.source.local.HiveLocalDataSource
import com.dibe.eduhive.data.source.local.MaterialLocalDataSource
import com.dibe.eduhive.data.source.local.QuizLocalDataSource
import com.dibe.eduhive.data.source.local.ReviewEventLocalDataSource
import com.dibe.eduhive.domain.engine.BayesianConfidenceStrategy
import com.dibe.eduhive.domain.engine.LearningEngine
import com.dibe.eduhive.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // ========== DATABASE ==========

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): EduHiveDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            EduHiveDatabase::class.java,
            "eduhive_database"
        ).build()
    }

    // ========== DAOs ==========

    @Provides
    @Singleton
    fun provideHiveDao(database: EduHiveDatabase) = database.hiveDao()

    @Provides
    @Singleton
    fun provideMaterialDao(database: EduHiveDatabase) = database.materialDao()

    @Provides
    @Singleton
    fun provideConceptDao(database: EduHiveDatabase) = database.conceptDao()

    @Provides
    @Singleton
    fun provideFlashcardDao(database: EduHiveDatabase) = database.flashcardDao()

    @Provides
    @Singleton
    fun provideQuizDao(database: EduHiveDatabase) = database.quizDao()

    @Provides
    @Singleton
    fun provideReviewDao(database: EduHiveDatabase) = database.reviewDao()

    // ========== DATA SOURCES ==========

    @Provides
    @Singleton
    fun provideHiveLocalDataSource(
        hiveDao: HiveDao
    ): HiveLocalDataSource = HiveLocalDataSource(hiveDao)

    @Provides
    @Singleton
    fun provideMaterialLocalDataSource(
        materialDao: MaterialDao
    ): MaterialLocalDataSource = MaterialLocalDataSource(materialDao)

    @Provides
    @Singleton
    fun provideConceptLocalDataSource(
        conceptDao: ConceptDao
    ): ConceptLocalDataSource = ConceptLocalDataSource(conceptDao)

    @Provides
    @Singleton
    fun provideFlashcardLocalDataSource(
        flashcardDao: FlashcardDao
    ): FlashcardLocalDataSource = FlashcardLocalDataSource(flashcardDao)

    @Provides
    @Singleton
    fun provideQuizLocalDataSource(
        quizDao: QuizDao
    ): QuizLocalDataSource = QuizLocalDataSource(quizDao)

    @Provides
    @Singleton
    fun provideReviewEventLocalDataSource(
        reviewDao: ReviewDao
    ): ReviewEventLocalDataSource = ReviewEventLocalDataSource(reviewDao)

    // ========== FILE DATA SOURCE ==========

    @Provides
    @Singleton
    fun provideFileDataSource(
        @ApplicationContext context: Context
    ): FileDataSource = FileDataSource(context)

    // ========== AI ==========

    @Provides
    @Singleton
    fun provideAIDataSource(
        modelManager: AIDataSource
    ): AIDataSource {
        // You'll set the modelId after downloading the model
        // For now, we use a placeholder
        return AIDataSource(modelId = "qwen-0.5b")
    }

    // ========== LEARNING ENGINE ==========

    @Provides
    @Singleton
    fun provideLearningEngine(): LearningEngine {
        val strategy = BayesianConfidenceStrategy(decayRatePerDay = 0.95)
        return LearningEngine(strategy)
    }

    // ========== REPOSITORIES ==========

    @Provides
    @Singleton
    fun provideHiveRepository(
        localDataSource: HiveLocalDataSource
    ): HiveRepository = HiveRepositoryImpl(localDataSource)

    @Provides
    @Singleton
    fun provideMaterialRepository(
        localDataSource: MaterialLocalDataSource
    ): MaterialRepository = MaterialRepositoryImpl(localDataSource)

    @Provides
    @Singleton
    fun provideConceptRepository(
        localDataSource: ConceptLocalDataSource,
        aiDataSource: AIDataSource,
        learningEngine: LearningEngine
    ): ConceptRepository = ConceptRepositoryImpl(
        localDataSource,
        aiDataSource,
        learningEngine
    )

    @Provides
    @Singleton
    fun provideFlashcardRepository(
        localDataSource: FlashcardLocalDataSource,
        aiDataSource: AIDataSource
    ): FlashcardRepository = FlashcardRepositoryImpl(
        localDataSource,
        aiDataSource
    )

    @Provides
    @Singleton
    fun provideQuizRepository(
        localDataSource: QuizLocalDataSource,
        aiDataSource: AIDataSource
    ): QuizRepository = QuizRepositoryImpl(
        localDataSource,
        aiDataSource
    )

    @Provides
    @Singleton
    fun provideReviewEventRepository(
        localDataSource: ReviewEventLocalDataSource
    ): ReviewEventRepository = ReviewEventRepositoryImpl(localDataSource)
}