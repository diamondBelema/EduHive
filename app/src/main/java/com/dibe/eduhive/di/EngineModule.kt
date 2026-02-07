package com.dibe.eduhive.di

import com.dibe.eduhive.domain.engine.BayesianConfidenceStrategyV2
import com.dibe.eduhive.domain.engine.LearningEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideLearningEngine(): LearningEngine {
        val strategy = BayesianConfidenceStrategyV2(
            decayRatePerDay = 0.95
        )
        return LearningEngine(strategy)
    }
}
