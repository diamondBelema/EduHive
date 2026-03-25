package com.dibe.eduhive.di


import android.content.Context
import com.dibe.eduhive.data.local.dao.*
import com.dibe.eduhive.data.source.ai.AIDataSource
import com.dibe.eduhive.data.source.ai.AIModelManager
import com.dibe.eduhive.data.source.ai.FlashcardValidator
import com.dibe.eduhive.data.source.ai.ModelPreferences
import com.dibe.eduhive.data.source.file.FileDataSource
import com.dibe.eduhive.data.source.local.ConceptLocalDataSource
import com.dibe.eduhive.data.source.local.FlashcardLocalDataSource
import com.dibe.eduhive.data.source.local.HiveLocalDataSource
import com.dibe.eduhive.data.source.local.MaterialLocalDataSource
import com.dibe.eduhive.data.source.local.QuizLocalDataSource
import com.dibe.eduhive.data.source.local.ReviewEventLocalDataSource
import com.dibe.eduhive.data.source.online.Downloader
import com.dibe.eduhive.data.source.online.ModelDownloader
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
    fun provideFlashcardValidator(): FlashcardValidator = FlashcardValidator()

    @Provides @Singleton
    fun provideAIDataSource(
        modelManager: AIModelManager,
        modelPreferences: ModelPreferences,
        flashcardValidator: FlashcardValidator
    ): AIDataSource =
        AIDataSource(modelManager, modelPreferences, flashcardValidator)

    @Provides @Singleton
    fun provideAIModelManager(
        @ApplicationContext context: Context,
        modelPreferences: ModelPreferences,
        downloader: Downloader
    ): AIModelManager =
        AIModelManager(context, modelPreferences, downloader)

    @Provides @Singleton
    fun provideModelPreferences(
        @ApplicationContext context: Context
    ): ModelPreferences =
        ModelPreferences(context)

    @Provides @Singleton
    fun provideDownloader(
        @ApplicationContext context: Context
    ): Downloader = ModelDownloader(context)

}
