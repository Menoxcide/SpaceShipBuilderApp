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
    fun provideGameStateManager(): GameStateManager = GameStateManager()

    @Provides
    @Singleton
    fun provideBuildModeManager(renderer: Renderer): BuildModeManager = BuildModeManager(renderer)

    @Provides
    @Singleton
    fun provideSkillManager(): SkillManager = SkillManager()

    @Provides
    @Singleton
    fun provideShipManager(skillManager: SkillManager): ShipManager = ShipManager(skillManager)

    @Provides
    @Singleton
    fun provideGameObjectManager(
        renderer: Renderer,
        audioManager: AudioManager
    ): GameObjectManager = GameObjectManager(renderer, audioManager)

    @Provides
    @Singleton
    fun provideCollisionManager(
        renderer: Renderer,
        audioManager: AudioManager,
        gameObjectManager: GameObjectManager
    ): CollisionManager = CollisionManager(renderer, audioManager, gameObjectManager)

    @Provides
    @Singleton
    fun providePowerUpManager(): PowerUpManager = PowerUpManager()

    @Provides
    @Singleton
    fun provideAudioManager(@ApplicationContext context: Context): AudioManager = AudioManager(context)

    @Provides
    @Singleton
    fun provideFlightModeManager(
        shipManager: ShipManager,
        gameObjectManager: GameObjectManager,
        collisionManager: CollisionManager,
        powerUpManager: PowerUpManager,
        audioManager: AudioManager,
        gameStateManager: GameStateManager
    ): FlightModeManager = FlightModeManager(
        shipManager,
        gameObjectManager,
        collisionManager,
        powerUpManager,
        audioManager,
        gameStateManager
    )

    @Provides
    @Singleton
    fun provideGameEngine(
        @ApplicationContext context: Context,
        renderer: Renderer,
        highscoreManager: HighscoreManager,
        gameStateManager: GameStateManager,
        buildModeManager: BuildModeManager,
        flightModeManager: FlightModeManager,
        achievementManager: AchievementManager,
        skillManager: SkillManager
    ): GameEngine = GameEngine(
        context,
        renderer,
        highscoreManager,
        gameStateManager,
        buildModeManager,
        flightModeManager,
        achievementManager,
        skillManager
    )

    @Provides
    @Singleton
    fun provideHighscoreManager(@ApplicationContext context: Context): HighscoreManager {
        return HighscoreManager(context)
    }

    @Provides
    @Singleton
    fun provideVoiceCommandHandler(@ApplicationContext context: Context): VoiceCommandHandler {
        return VoiceCommandHandler(context) { /* Dummy callback, overridden in MainActivity */ }
    }

    @Provides
    @Singleton
    fun provideAchievementManager(
        @ApplicationContext context: Context
    ): AchievementManager {
        return AchievementManager(context)
    }
}