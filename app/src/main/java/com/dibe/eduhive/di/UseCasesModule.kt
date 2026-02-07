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
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    // ===== CONCEPT =====

    @Provides
    fun provideCreateConceptUseCase(
        conceptRepository: ConceptRepository
    ) = CreateConceptUseCase(conceptRepository)

    @Provides
    fun provideGetConceptDetailsUseCase(
        conceptRepository: ConceptRepository,
        flashcardRepository: FlashcardRepository
    ) = GetConceptDetailsUseCase(
        conceptRepository,
        flashcardRepository
    )

    @Provides
    fun provideGetConceptsByHiveUseCase(
        conceptRepository: ConceptRepository
    ) = GetConceptsByHiveUseCase(conceptRepository)

    @Provides
    fun provideGetWeakConceptsUseCase(
        conceptRepository: ConceptRepository
    ) = GetWeakConceptsUseCase(conceptRepository)

    // ===== DASHBOARD =====

    @Provides
    fun provideGetDashboardOverviewUseCase(
        conceptRepository: ConceptRepository,
        flashcardRepository: FlashcardRepository,
        materialRepository: MaterialRepository,
        reviewEventRepository: ReviewEventRepository
    ) = GetDashboardOverviewUseCase(
        conceptRepository,
        flashcardRepository,
        materialRepository,
        reviewEventRepository
    )

    // ===== FLASHCARD =====

    @Provides
    fun provideGetFlashcardsForConceptUseCase(
        flashcardRepository: FlashcardRepository
    ) = GetFlashcardsForConceptUseCase(flashcardRepository)

    @Provides
    fun provideReviewFlashcardUseCase(
        flashcardRepository: FlashcardRepository,
        conceptRepository: ConceptRepository,
        reviewEventRepository: ReviewEventRepository
    ) = ReviewFlashcardUseCase(
        flashcardRepository,
        conceptRepository,
        reviewEventRepository
    )

    // ===== HIVE =====

    @Provides
    fun provideCreateHiveUseCase(
        hiveRepository: HiveRepository
    ) = CreateHiveUseCase(hiveRepository)

    @Provides
    fun provideSelectHiveUseCase(
        hiveRepository: HiveRepository
    ) = SelectHiveUseCase(hiveRepository)

    // ===== MATERIAL =====

    @Provides
    fun provideAddMaterialUseCase(
        materialRepository: MaterialRepository,
        conceptRepository: ConceptRepository,
        flashcardRepository: FlashcardRepository,
        fileDataSource: FileDataSource
    ) = AddMaterialUseCase(
        fileDataSource,
        materialRepository,
        conceptRepository,
        flashcardRepository
    )

    @Provides
    fun provideGetMaterialsForHiveUseCase(
        materialRepository: MaterialRepository
    ) = GetMaterialsForHiveUseCase(materialRepository)

    @Provides
    fun provideProcessMaterialUseCase(
        materialRepository: MaterialRepository,
        fileDataSource: FileDataSource,
        conceptRepository: ConceptRepository,
        flashcardRepository: FlashcardRepository
    ) = ProcessMaterialUseCase(
        materialRepository,
        fileDataSource,
        conceptRepository,
        flashcardRepository
    )

    // ===== QUIZ =====

    @Provides
    fun provideGenerateQuizUseCase(
        quizRepository: QuizRepository,
        conceptRepository: ConceptRepository
    ) = GenerateQuizUseCase(
        quizRepository,
        conceptRepository
    )

    @Provides
    fun provideGetQuizForConceptUseCase(
        quizRepository: QuizRepository
    ) = GetQuizForConceptUseCase(quizRepository)

    // ===== REVIEW / STUDY =====

    @Provides
    fun provideSubmitQuizResultUseCase(
        conceptRepository: ConceptRepository,
        reviewEventRepository: ReviewEventRepository
    ) = SubmitQuizResultUseCase(
        conceptRepository,
        reviewEventRepository
    )

    @Provides
    fun provideGetNextReviewItemsUseCase(
        flashcardRepository: FlashcardRepository
    ) = GetNextReviewItemsUseCase(flashcardRepository)
}
