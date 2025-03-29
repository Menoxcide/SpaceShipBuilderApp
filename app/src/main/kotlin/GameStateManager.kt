package com.example.spaceshipbuilderapp

import timber.log.Timber
import javax.inject.Inject

enum class GameState { BUILD, FLIGHT, GAME_OVER }

class GameStateManager @Inject constructor() {
    var gameState: GameState = GameState.BUILD
        private set

    private var onLaunchListener: ((Boolean) -> Unit)? = null
    private var onGameOverListener: ((Boolean, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit)? = null

    fun setGameState(
        newState: GameState,
        screenWidth: Float,
        screenHeight: Float,
        resetFlightData: () -> Unit,
        savePersistentData: (String) -> Unit,
        userId: String?
    ) {
        if (gameState == newState) return
        Timber.d("Transitioning game state from $gameState to $newState")
        gameState = newState
        when (newState) {
            GameState.FLIGHT -> {
                onLaunchListener?.invoke(true)
            }
            GameState.BUILD -> {
                onLaunchListener?.invoke(false)
                resetFlightData()
                if (userId != null) {
                    savePersistentData(userId)
                }
            }
            GameState.GAME_OVER -> {
                // Game over logic is handled in update, so no immediate action here
            }
        }
    }

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        onLaunchListener = listener
    }

    fun setGameOverListener(listener: (Boolean, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit) {
        onGameOverListener = listener
    }

    fun notifyLaunchListener(isLaunching: Boolean) {
        onLaunchListener?.invoke(isLaunching)
        Timber.d("Notified launch listener, isLaunching=$isLaunching")
    }

    fun notifyGameOver(
        canContinue: Boolean,
        canUseRevive: Boolean,
        onContinueWithAd: () -> Unit,
        onContinueWithRevive: () -> Unit,
        onReturnToBuild: () -> Unit
    ) {
        onGameOverListener?.invoke(canContinue, canUseRevive, onContinueWithAd, onContinueWithRevive, onReturnToBuild)
    }
}