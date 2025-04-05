package com.example.spaceshipbuilderapp

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.Lazy

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
    fun provideBitmapManager(@ApplicationContext context: Context): BitmapManager {
        return BitmapManager(context)
    }

    @Provides
    @Singleton
    fun provideBackgroundRenderer(@ApplicationContext context: Context): BackgroundRenderer {
        return BackgroundRenderer(context)
    }

    @Provides
    @Singleton
    fun provideShipRenderer(
        bitmapManager: BitmapManager,
        particleSystem: ParticleSystem
    ): ShipRenderer {
        return ShipRenderer(bitmapManager, particleSystem)
    }

    @Provides
    @Singleton
    fun provideGameObjectRenderer(
        @ApplicationContext context: Context,
        particleSystem: ParticleSystem // Add ParticleSystem dependency
    ): GameObjectRenderer {
        return GameObjectRenderer(context, particleSystem)
    }

    @Provides
    @Singleton
    fun provideUIRenderer(): UIRenderer {
        return UIRenderer()
    }

    @Provides
    @Singleton
    fun provideRenderer(
        bitmapManager: BitmapManager,
        backgroundRenderer: BackgroundRenderer,
        shipRenderer: ShipRenderer,
        gameObjectRenderer: GameObjectRenderer,
        uiRenderer: UIRenderer
    ): Renderer {
        return Renderer(
            bitmapManager,
            backgroundRenderer,
            shipRenderer,
            gameObjectRenderer,
            uiRenderer
        )
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
    fun provideAIAssistant(): AIAssistant = AIAssistant()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFlightModeManager(
        shipManager: ShipManager,
        gameObjectManager: GameObjectManager,
        collisionManager: CollisionManager,
        powerUpManager: PowerUpManager,
        audioManager: AudioManager,
        gameStateManager: GameStateManager,
        aiAssistant: AIAssistant,
        gameEngine: Lazy<GameEngine>,
        @ApplicationContext context: Context
    ): FlightModeManager = FlightModeManager(
        shipManager,
        gameObjectManager,
        collisionManager,
        powerUpManager,
        audioManager,
        gameStateManager,
        aiAssistant,
        gameEngine,
        context
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
        skillManager: SkillManager,
        aiAssistant: AIAssistant
    ): GameEngine = GameEngine(
        context,
        renderer,
        highscoreManager,
        gameStateManager,
        buildModeManager,
        flightModeManager,
        achievementManager,
        skillManager,
        aiAssistant
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