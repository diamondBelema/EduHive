package com.dibe.eduhive.di


import android.content.Context
import androidx.room.Room
import com.dibe.eduhive.data.local.dao.*
import com.dibe.eduhive.data.local.database.EduHiveDatabase
import com.dibe.eduhive.data.repository.*
import com.dibe.eduhive.data.source.*
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.ai.AIModelManager
import com.dibe.eduhive.data.source.ai.ModelPreferences
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.data.source.local.ConceptLocalDataSource
import com.dibe.eduhive.data.source.local.FlashcardLocalDataSource
import com.dibe.eduhive.data.source.local.HiveLocalDataSource
import com.dibe.eduhive.data.source.local.MaterialLocalDataSource
import com.dibe.eduhive.data.source.local.QuizLocalDataSource
import com.dibe.eduhive.data.source.local.ReviewEventLocalDataSource
import com.dibe.eduhive.domain.engine.BayesianConfidenceStrategy
import com.dibe.eduhive.domain.engine.BayesianConfidenceStrategyV2
import com.dibe.eduhive.domain.engine.LearningEngine
import com.dibe.eduhive.domain.repository.*
import com.dibe.eduhive.domain.usecase.concept.CreateConceptUseCase
import com.dibe.eduhive.domain.usecase.concept.GetConceptDetailsUseCase
import com.dibe.eduhive.domain.usecase.concept.GetConceptsByHiveUseCase
import com.dibe.eduhive.domain.usecase.dashboard.GetDashboardOverviewUseCase
import com.dibe.eduhive.domain.usecase.flashcard.GetFlashcardsForConceptUseCase
import com.dibe.eduhive.domain.usecase.hive.CreateHiveUseCase
import com.dibe.eduhive.domain.usecase.hive.SelectHiveUseCase
import com.dibe.eduhive.domain.usecase.material.AddMaterialUseCase
import com.dibe.eduhive.domain.usecase.material.GetMaterialsForHiveUseCase
import com.dibe.eduhive.domain.usecase.material.ProcessMaterialUseCase
import com.dibe.eduhive.domain.usecase.progress.GetWeakConceptsUseCase
import com.dibe.eduhive.domain.usecase.quiz.GenerateQuizUseCase
import com.dibe.eduhive.domain.usecase.quiz.GetQuizForConceptUseCase
import com.dibe.eduhive.domain.usecase.review.ReviewFlashcardUseCase
import com.dibe.eduhive.domain.usecase.review.SubmitQuizResultUseCase
import com.dibe.eduhive.domain.usecase.study.GetNextReviewItemsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    // Local
    @Provides @Singleton
    fun provideHiveLocal(ds: HiveDao) = HiveLocalDataSource(ds)

    @Provides @Singleton
    fun provideMaterialLocal(ds: MaterialDao) = MaterialLocalDataSource(ds)

    @Provides @Singleton
    fun provideConceptLocal(ds: ConceptDao) = ConceptLocalDataSource(ds)

    @Provides @Singleton
    fun provideFlashcardLocal(ds: FlashcardDao) = FlashcardLocalDataSource(ds)

    @Provides @Singleton
    fun provideQuizLocal(ds: QuizDao) = QuizLocalDataSource(ds)

    @Provides @Singleton
    fun provideReviewLocal(ds: ReviewDao) = ReviewEventLocalDataSource(ds)

    // File
    @Provides @Singleton
    fun provideFileDataSource(
        @ApplicationContext context: Context
    ) = FileDataSource(context)

    // AI  ❗ FIXED BUG HERE
    @Provides @Singleton
    fun provideAIDataSource(
        modelManager: AIModelManager
    ): AIDataSource =
        AIDataSource(modelManager)

    @Provides @Singleton
    fun provideAIModelManager(
        @ApplicationContext context: Context,
        modelPreferences: ModelPreferences
    ): AIModelManager =
        AIModelManager(context, modelPreferences)

    @Provides @Singleton
    fun provideModelPreferences(
        @ApplicationContext context: Context
    ): ModelPreferences =
        ModelPreferences(context)

}
