package com.xadras.app.di

import android.content.Context
import com.xadras.app.ml.ChessBoardDetector
import com.xadras.app.ml.FenTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideChessBoardDetector(@ApplicationContext context: Context): ChessBoardDetector {
        return ChessBoardDetector(context).also { it.initialize() }
    }

    @Provides
    @Singleton
    fun provideFenTracker(): FenTracker {
        return FenTracker()
    }
}