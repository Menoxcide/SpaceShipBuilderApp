package com.example.spaceshipbuilderapp

import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

enum class GameState { BUILD, FLIGHT, GAME_OVER }

class GameStateManager @Inject constructor() {
    var gameState: GameState = GameState.BUILD
        private set

    private var onLaunchListener: ((Boolean) -> Unit)? = null
    private var onGameOverListener: ((Boolean, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit)? = null
    private var pausedState: PausedGameState? = null
    private val db = FirebaseFirestore.getInstance()

    // Data class to hold the paused game state
    data class PausedGameState(
        // ShipManager state
        val shipX: Float,
        val shipY: Float,
        val hp: Float,
        val fuel: Float,
        val missileCount: Int,
        val lastMissileRechargeTime: Long,
        // PowerUpManager state
        val shieldActive: Boolean,
        val shieldEndTime: Long,
        val speedBoostActive: Boolean,
        val speedBoostEndTime: Long,
        val stealthActive: Boolean,
        val stealthEndTime: Long,
        val invincibilityActive: Boolean,
        val invincibilityEndTime: Long,
        // GameObjectManager state
        val powerUps: List<PowerUp>,
        val asteroids: List<Asteroid>,
        val projectiles: List<Projectile>,
        val enemyShips: List<EnemyShip>,
        val enemyProjectiles: List<Projectile>,
        val homingProjectiles: List<HomingProjectile>,
        val boss: BossShip?,
        // FlightModeManager state
        val currentScore: Int,
        val distanceTraveled: Float,
        val sessionDistanceTraveled: Float,
        val level: Int,
        val lastScoreUpdateTime: Long,
        val lastDistanceUpdateTime: Long,
        val glowStartTime: Long,
        val levelUpAnimationStartTime: Long,
        val continuesUsed: Int
    ) {
        // Convert to a map for Firebase storage
        fun toMap(): Map<String, Any?> {
            return mapOf(
                "shipX" to shipX,
                "shipY" to shipY,
                "hp" to hp,
                "fuel" to fuel,
                "missileCount" to missileCount,
                "lastMissileRechargeTime" to lastMissileRechargeTime,
                "shieldActive" to shieldActive,
                "shieldEndTime" to shieldEndTime,
                "speedBoostActive" to speedBoostActive,
                "speedBoostEndTime" to speedBoostEndTime,
                "stealthActive" to stealthActive,
                "stealthEndTime" to stealthEndTime,
                "invincibilityActive" to invincibilityActive,
                "invincibilityEndTime" to invincibilityEndTime,
                "powerUps" to powerUps.map { it.toMap() },
                "asteroids" to asteroids.map { it.toMap() },
                "projectiles" to projectiles.map { it.toMap() },
                "enemyShips" to enemyShips.map { it.toMap() },
                "enemyProjectiles" to enemyProjectiles.map { it.toMap() },
                "homingProjectiles" to homingProjectiles.map { it.toMap() },
                "boss" to boss?.toMap(),
                "currentScore" to currentScore,
                "distanceTraveled" to distanceTraveled,
                "sessionDistanceTraveled" to sessionDistanceTraveled,
                "level" to level,
                "lastScoreUpdateTime" to lastScoreUpdateTime,
                "lastDistanceUpdateTime" to lastDistanceUpdateTime,
                "glowStartTime" to glowStartTime,
                "levelUpAnimationStartTime" to levelUpAnimationStartTime,
                "continuesUsed" to continuesUsed
            )
        }

        companion object {
            // Convert from a map (loaded from Firebase) to PausedGameState
            fun fromMap(map: Map<String, Any?>, gameObjectManager: GameObjectManager): PausedGameState {
                return PausedGameState(
                    shipX = (map["shipX"] as? Double)?.toFloat() ?: 0f,
                    shipY = (map["shipY"] as? Double)?.toFloat() ?: 0f,
                    hp = (map["hp"] as? Double)?.toFloat() ?: 100f,
                    fuel = (map["fuel"] as? Double)?.toFloat() ?: 50f,
                    missileCount = (map["missileCount"] as? Long)?.toInt() ?: 3,
                    lastMissileRechargeTime = map["lastMissileRechargeTime"] as? Long ?: System.currentTimeMillis(),
                    shieldActive = map["shieldActive"] as? Boolean ?: false,
                    shieldEndTime = map["shieldEndTime"] as? Long ?: 0L,
                    speedBoostActive = map["speedBoostActive"] as? Boolean ?: false,
                    speedBoostEndTime = map["speedBoostEndTime"] as? Long ?: 0L,
                    stealthActive = map["stealthActive"] as? Boolean ?: false,
                    stealthEndTime = map["stealthEndTime"] as? Long ?: 0L,
                    invincibilityActive = map["invincibilityActive"] as? Boolean ?: false,
                    invincibilityEndTime = map["invincibilityEndTime"] as? Long ?: 0L,
                    powerUps = (map["powerUps"] as? List<Map<String, Any>>)?.map { PowerUp.fromMap(it) } ?: emptyList(),
                    asteroids = (map["asteroids"] as? List<Map<String, Any>>)?.map { Asteroid.fromMap(it) } ?: emptyList(),
                    projectiles = (map["projectiles"] as? List<Map<String, Any>>)?.map { Projectile.fromMap(it) } ?: emptyList(),
                    enemyShips = (map["enemyShips"] as? List<Map<String, Any>>)?.map { EnemyShip.fromMap(it) } ?: emptyList(),
                    enemyProjectiles = (map["enemyProjectiles"] as? List<Map<String, Any>>)?.map { Projectile.fromMap(it) } ?: emptyList(),
                    homingProjectiles = (map["homingProjectiles"] as? List<Map<String, Any>>)?.map { HomingProjectile.fromMap(it, gameObjectManager) } ?: emptyList(),
                    boss = (map["boss"] as? Map<String, Any>)?.let { BossShip.fromMap(it) },
                    currentScore = (map["currentScore"] as? Long)?.toInt() ?: 0,
                    distanceTraveled = (map["distanceTraveled"] as? Double)?.toFloat() ?: 0f,
                    sessionDistanceTraveled = (map["sessionDistanceTraveled"] as? Double)?.toFloat() ?: 0f,
                    level = (map["level"] as? Long)?.toInt() ?: 1,
                    lastScoreUpdateTime = map["lastScoreUpdateTime"] as? Long ?: System.currentTimeMillis(),
                    lastDistanceUpdateTime = map["lastDistanceUpdateTime"] as? Long ?: System.currentTimeMillis(),
                    glowStartTime = map["glowStartTime"] as? Long ?: 0L,
                    levelUpAnimationStartTime = map["levelUpAnimationStartTime"] as? Long ?: 0L,
                    continuesUsed = (map["continuesUsed"] as? Long)?.toInt() ?: 0
                )
            }
        }
    }

    fun setGameState(
        newState: GameState,
        screenWidth: Float,
        screenHeight: Float,
        resetFlightData: () -> Unit,
        savePersistentData: (String) -> Unit,
        userId: String?,
        gameEngine: GameEngine? = null
    ) {
        if (gameState == newState) return
        Timber.d("Transitioning game state from $gameState to $newState")

        // Save state when pausing (FLIGHT -> BUILD)
        if (gameState == GameState.FLIGHT && newState == GameState.BUILD && gameEngine != null) {
            pausedState = saveGameState(gameEngine)
            Timber.d("Saved game state on pause: $pausedState")
            // Save the paused state to Firebase
            if (userId != null) {
                savePausedStateToFirebase(userId)
            }
        }

        gameState = newState
        when (newState) {
            GameState.FLIGHT -> {
                // Restore state if resuming from a pause
                if (pausedState != null && gameEngine != null) {
                    restoreGameState(gameEngine, pausedState!!)
                    Timber.d("Restored game state on resume")
                }
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
                // Clear paused state on game over
                pausedState = null
                if (userId != null) {
                    savePausedStateToFirebase(userId)
                }
            }
        }
    }

    private fun saveGameState(gameEngine: GameEngine): PausedGameState {
        return PausedGameState(
            // ShipManager state
            shipX = gameEngine.shipX,
            shipY = gameEngine.shipY,
            hp = gameEngine.hp,
            fuel = gameEngine.fuel,
            missileCount = gameEngine.missileCount,
            lastMissileRechargeTime = gameEngine.flightModeManager.shipManager.lastMissileRechargeTime,
            // PowerUpManager state
            shieldActive = gameEngine.shieldActive,
            shieldEndTime = gameEngine.flightModeManager.powerUpManager.getShieldEndTime(),
            speedBoostActive = gameEngine.speedBoostActive,
            speedBoostEndTime = gameEngine.flightModeManager.powerUpManager.getSpeedBoostEndTime(),
            stealthActive = gameEngine.stealthActive,
            stealthEndTime = gameEngine.flightModeManager.powerUpManager.getStealthEndTime(),
            invincibilityActive = gameEngine.invincibilityActive,
            invincibilityEndTime = gameEngine.flightModeManager.powerUpManager.getInvincibilityEndTime(),
            // GameObjectManager state
            powerUps = gameEngine.powerUps.toList(),
            asteroids = gameEngine.asteroids.toList(),
            projectiles = gameEngine.projectiles.toList(),
            enemyShips = gameEngine.enemyShips.toList(),
            enemyProjectiles = gameEngine.enemyProjectiles.toList(),
            homingProjectiles = gameEngine.homingProjectiles.toList(),
            boss = gameEngine.getBoss(),
            // FlightModeManager state
            currentScore = gameEngine.currentScore,
            distanceTraveled = gameEngine.distanceTraveled,
            sessionDistanceTraveled = gameEngine.flightModeManager.getSessionDistanceTraveled(),
            level = gameEngine.level,
            lastScoreUpdateTime = gameEngine.flightModeManager.getLastScoreUpdateTime(),
            lastDistanceUpdateTime = gameEngine.flightModeManager.getLastDistanceUpdateTime(),
            glowStartTime = gameEngine.glowStartTime,
            levelUpAnimationStartTime = gameEngine.levelUpAnimationStartTime,
            continuesUsed = gameEngine.flightModeManager.getContinuesUsed()
        )
    }

    private fun restoreGameState(gameEngine: GameEngine, state: PausedGameState) {
        // Restore ShipManager state
        gameEngine.shipX = state.shipX
        gameEngine.shipY = state.shipY
        gameEngine.hp = state.hp
        gameEngine.fuel = state.fuel
        gameEngine.missileCount = state.missileCount
        gameEngine.flightModeManager.shipManager.lastMissileRechargeTime = state.lastMissileRechargeTime

        // Restore PowerUpManager state
        gameEngine.shieldActive = state.shieldActive
        gameEngine.flightModeManager.powerUpManager.setShieldEndTime(state.shieldEndTime)
        gameEngine.speedBoostActive = state.speedBoostActive
        gameEngine.flightModeManager.powerUpManager.setSpeedBoostEndTime(state.speedBoostEndTime)
        gameEngine.stealthActive = state.stealthActive
        gameEngine.flightModeManager.powerUpManager.setStealthEndTime(state.stealthEndTime)
        gameEngine.invincibilityActive = state.invincibilityActive
        gameEngine.flightModeManager.powerUpManager.setInvincibilityEndTime(state.invincibilityEndTime)

        // Restore GameObjectManager state
        gameEngine.flightModeManager.gameObjectManager.powerUps.clear()
        gameEngine.flightModeManager.gameObjectManager.powerUps.addAll(state.powerUps)
        gameEngine.flightModeManager.gameObjectManager.asteroids.clear()
        gameEngine.flightModeManager.gameObjectManager.asteroids.addAll(state.asteroids)
        gameEngine.flightModeManager.gameObjectManager.projectiles.clear()
        gameEngine.flightModeManager.gameObjectManager.projectiles.addAll(state.projectiles)
        gameEngine.flightModeManager.gameObjectManager.enemyShips.clear()
        gameEngine.flightModeManager.gameObjectManager.enemyShips.addAll(state.enemyShips)
        gameEngine.flightModeManager.gameObjectManager.enemyProjectiles.clear()
        gameEngine.flightModeManager.gameObjectManager.enemyProjectiles.addAll(state.enemyProjectiles)
        gameEngine.flightModeManager.gameObjectManager.homingProjectiles.clear()
        gameEngine.flightModeManager.gameObjectManager.homingProjectiles.addAll(state.homingProjectiles)
        gameEngine.flightModeManager.gameObjectManager.setBoss(state.boss)

        // Restore FlightModeManager state
        gameEngine.currentScore = state.currentScore
        gameEngine.distanceTraveled = state.distanceTraveled
        gameEngine.flightModeManager.setSessionDistanceTraveled(state.sessionDistanceTraveled)
        gameEngine.level = state.level
        gameEngine.flightModeManager.setLastScoreUpdateTime(state.lastScoreUpdateTime)
        gameEngine.flightModeManager.setLastDistanceUpdateTime(state.lastDistanceUpdateTime)
        gameEngine.glowStartTime = state.glowStartTime
        gameEngine.levelUpAnimationStartTime = state.levelUpAnimationStartTime
        gameEngine.flightModeManager.setContinuesUsed(state.continuesUsed)
    }

    fun getPausedState(): PausedGameState? = pausedState

    suspend fun loadPausedStateFromFirebase(userId: String, gameObjectManager: GameObjectManager) {
        try {
            Timber.d("Loading paused state for userId: $userId")
            val doc = db.collection("users").document(userId).collection("gameState").document("pausedState").get().await()
            if (doc.exists()) {
                pausedState = PausedGameState.fromMap(doc.data!!, gameObjectManager)
                Timber.d("Loaded paused state from Firebase: $pausedState")
            } else {
                pausedState = null
                Timber.d("No paused state found for userId: $userId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load paused state from Firebase: ${e.message}")
            pausedState = null
        }
    }

    private fun savePausedStateToFirebase(userId: String) {
        try {
            if (pausedState != null) {
                db.collection("users").document(userId).collection("gameState").document("pausedState")
                    .set(pausedState!!.toMap())
                    .addOnSuccessListener { Timber.d("Saved paused state to Firebase for userId: $userId") }
                    .addOnFailureListener { e -> Timber.e(e, "Failed to save paused state to Firebase: ${e.message}") }
            } else {
                // If there is no paused state, delete the document to indicate no paused game
                db.collection("users").document(userId).collection("gameState").document("pausedState")
                    .delete()
                    .addOnSuccessListener { Timber.d("Cleared paused state in Firebase for userId: $userId") }
                    .addOnFailureListener { e -> Timber.e(e, "Failed to clear paused state in Firebase: ${e.message}") }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while saving paused state to Firebase: ${e.message}")
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