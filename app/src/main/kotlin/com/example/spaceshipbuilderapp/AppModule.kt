package com.example.spaceshipbuilderapp

import android.content.Context
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
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideParticleSystem(@ApplicationContext context: Context): ParticleSystem {
        return ParticleSystem(context)
    }

    @Provides
    @Singleton
    fun provideRenderer(
        @ApplicationContext context: Context,
        particleSystem: ParticleSystem
    ): Renderer {
        return Renderer(context, particleSystem)
    }

    @Provides
    @Singleton
    fun provideGameEngine(
        @ApplicationContext context: Context,
        renderer: Renderer
    ): GameEngine {
        return GameEngine(context, renderer)
    }

    @Provides
    @Singleton
    fun provideHighscoreManager(@ApplicationContext context: Context): HighscoreManager {
        return HighscoreManager(context)
    }
}