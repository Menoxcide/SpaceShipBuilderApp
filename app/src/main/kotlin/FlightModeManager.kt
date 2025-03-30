package com.example.spaceshipbuilderapp

import dagger.Lazy
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

class FlightModeManager @Inject constructor(
    val shipManager: ShipManager,
    val gameObjectManager: GameObjectManager,
    val collisionManager: CollisionManager,
    val powerUpManager: PowerUpManager,
    private val audioManager: AudioManager,
    private val gameStateManager: GameStateManager,
    private val aiAssistant: AIAssistant,
    private val gameEngine: Lazy<GameEngine>
) {
    var currentScore = 0
    var highestScore = 0

    var glowStartTime: Long = 0L
    val glowDuration = 1000L

    private var continuesUsed = 0
    private val maxContinues = 2

    var statusBarHeight: Float = 0f

    var distanceTraveled = 0f
    var longestDistanceTraveled = 0f
    private var sessionDistanceTraveled = 0f
    private val distancePerLevel = 100f
    var levelUpAnimationStartTime = 0L

    private var lastScoreUpdateTime = System.currentTimeMillis()
    private var lastDistanceUpdateTime = System.currentTimeMillis()

    private var userId: String = "default_user"

    companion object {
        const val LEVEL_UP_ANIMATION_DURATION = 5000L
        const val GIANT_ASTEROID_PROBABILITY = 0.1f
        const val GIANT_ASTEROID_SCALE = 2.0f
        const val SMALL_ASTEROID_COUNT = 3
        const val EXPLOSION_CHANCE = 0.1f
        const val MAX_AURA_PARTICLES = 200
        const val PROJECTILE_SIZE = 5f
        const val ASTEROID_DESTROY_POINTS = 20
        const val BOSS_SHOT_INTERVAL = 2000L
        const val BOSS_MOVEMENT_INTERVAL = 1000L
    }

    fun setScreenDimensions(width: Float, height: Float, statusBarHeight: Float) {
        shipManager.screenWidth = width
        shipManager.screenHeight = height
        gameObjectManager.screenWidth = width
        gameObjectManager.screenHeight = height
        this.statusBarHeight = statusBarHeight
        Timber.d("FlightModeManager screen dimensions set: width=$width, height=$height, statusBarHeight=$statusBarHeight")
    }

    fun update(
        userId: String,
        onLevelChange: (Int) -> Unit,
        onHighestLevelChange: (Int) -> Unit,
        onHighestScoreChange: (Int) -> Unit,
        onStarsCollectedChange: (Int) -> Unit,
        onDistanceTraveledChange: (Float) -> Unit,
        onLongestDistanceTraveledChange: (Float) -> Unit,
        onBossDefeatedChange: () -> Unit
    ) {
        try {
            if (gameStateManager.gameState != GameState.FLIGHT) {
                Timber.d("FlightModeManager update skipped: gameState=${gameStateManager.gameState}")
                return
            }

            val currentTime = System.currentTimeMillis()

            powerUpManager.updatePowerUpEffects(shipManager)
            shipManager.fuel -= shipManager.currentFuelConsumption

            if (shipManager.missileCount < shipManager.maxMissiles && currentTime - shipManager.lastMissileRechargeTime >= shipManager.missileRechargeTime) {
                shipManager.missileCount++
                shipManager.lastMissileRechargeTime = currentTime
                Timber.d("Missile recharged. Current count: ${shipManager.missileCount}")
            }

            gameObjectManager.updateGameObjects(
                level = shipManager.level,
                shipX = shipManager.shipX,
                shipY = shipManager.shipY,
                currentProjectileSpeed = shipManager.currentProjectileSpeed,
                screenWidth = shipManager.screenWidth,
                screenHeight = shipManager.screenHeight,
                onBossDefeated = {
                    currentScore += 500
                    shipManager.level++
                    onLevelChange(shipManager.level)
                    levelUpAnimationStartTime = currentTime
                    onBossDefeatedChange()
                    if (shipManager.level > shipManager.highestLevel) {
                        shipManager.highestLevel = shipManager.level
                        onHighestLevelChange(shipManager.highestLevel)
                        val previousUnlocked = shipManager.getUnlockedShipSets().toList()
                        shipManager.updateUnlockedShipSets(shipManager.highestLevel, shipManager.starsCollected)
                        val newUnlocked = shipManager.getUnlockedShipSets().filter { it !in previousUnlocked }
                        if (newUnlocked.isNotEmpty()) {
                            gameObjectManager.renderer.showUnlockMessage(newUnlocked.map { "Ship Set ${it + 1}" })
                        }
                    }
                    Timber.d("Boss defeated, level increased to ${shipManager.level}")
                }
            )

            if (currentTime - lastDistanceUpdateTime >= 1000 && gameObjectManager.getBoss() == null) {
                sessionDistanceTraveled += shipManager.currentSpeed
                distanceTraveled += shipManager.currentSpeed
                onDistanceTraveledChange(distanceTraveled)
                lastDistanceUpdateTime = currentTime
                Timber.d("Session distance traveled: $sessionDistanceTraveled, Total distance traveled: $distanceTraveled")
                val totalDistanceForLevel = distanceTraveled
                if (totalDistanceForLevel >= distancePerLevel * shipManager.level) {
                    shipManager.level++
                    onLevelChange(shipManager.level)
                    levelUpAnimationStartTime = currentTime
                    if (shipManager.level > shipManager.highestLevel) {
                        shipManager.highestLevel = shipManager.level
                        onHighestLevelChange(shipManager.highestLevel)
                        val previousUnlocked = shipManager.getUnlockedShipSets().toList()
                        shipManager.updateUnlockedShipSets(shipManager.highestLevel, shipManager.starsCollected)
                        val newUnlocked = shipManager.getUnlockedShipSets().filter { it !in previousUnlocked }
                        if (newUnlocked.isNotEmpty()) {
                            gameObjectManager.renderer.showUnlockMessage(newUnlocked.map { "Ship Set ${it + 1}" })
                        }
                    }
                    Timber.d("Level advanced to ${shipManager.level} based on total distance traveled: $totalDistanceForLevel")
                }
            }

            if (currentTime - lastScoreUpdateTime >= 1000) {
                currentScore += 10
                lastScoreUpdateTime = currentTime
            }

            var powerUpCollected: String? = null
            var fuelHpGained = false

            val (scoreDelta, newGlowStartTime) = collisionManager.checkCollisions(
                shipX = shipManager.shipX,
                shipY = shipManager.shipY,
                maxPartHalfWidth = shipManager.maxPartHalfWidth,
                totalShipHeight = shipManager.totalShipHeight,
                stealthActive = powerUpManager.stealthActive,
                invincibilityActive = powerUpManager.invincibilityActive,
                powerUps = gameObjectManager.powerUps,
                asteroids = gameObjectManager.asteroids,
                projectiles = gameObjectManager.projectiles,
                enemyShips = gameObjectManager.enemyShips,
                enemyProjectiles = gameObjectManager.enemyProjectiles,
                homingProjectiles = gameObjectManager.homingProjectiles,
                boss = gameObjectManager.getBoss(),
                onPowerUpCollected = { powerUp ->
                    powerUpCollected = powerUp.type
                    when (powerUp.type) {
                        "power_up" -> {
                            shipManager.fuel = (shipManager.fuel + 20f).coerceAtMost(shipManager.fuelCapacity)
                            shipManager.hp = (shipManager.hp + 10f).coerceAtMost(shipManager.maxHp)
                            gameObjectManager.renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Fuel +20", powerUp.type)
                            Timber.d("Collected power-up: Fuel +20, HP +10")
                            fuelHpGained = true
                        }
                        "shield", "speed", "stealth", "invincibility" -> {
                            powerUpManager.applyPowerUpEffect(powerUp.type, shipManager)
                            gameObjectManager.renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, powerUp.type.replaceFirstChar { it.uppercase() }, powerUp.type)
                        }
                        "warp" -> {
                            shipManager.shipX = Random.nextFloat() * (shipManager.screenWidth - 2 * shipManager.maxPartHalfWidth) + shipManager.maxPartHalfWidth
                            shipManager.shipY = Random.nextFloat() * (shipManager.screenHeight - shipManager.totalShipHeight) + shipManager.totalShipHeight / 2
                            gameObjectManager.renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Warp", powerUp.type)
                            Timber.d("Collected warp power-up")
                        }
                        "star" -> {
                            currentScore += 50
                            shipManager.starsCollected += 1
                            onStarsCollectedChange(shipManager.starsCollected)
                            gameObjectManager.renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Star +50", powerUp.type)
                            Timber.d("Collected star power-up, starsCollected=${shipManager.starsCollected}")
                        }
                    }
                },
                onAsteroidHit = { asteroid ->
                    shipManager.hp -= 10f
                },
                onEnemyProjectileHit = { projectile ->
                    shipManager.hp -= 10f
                },
                onEnemyShipHit = { enemy ->
                    shipManager.hp -= 25f
                }
            )

            currentScore += scoreDelta
            if (newGlowStartTime > 0L) {
                glowStartTime = newGlowStartTime
            }

            gameObjectManager.cleanupGameObjects(shipManager.screenHeight, shipManager.screenWidth)

            aiAssistant.update(
                gameState = gameStateManager.gameState,
                shipX = shipManager.shipX,
                shipY = shipManager.shipY,
                asteroids = gameObjectManager.asteroids,
                enemyShips = gameObjectManager.enemyShips,
                boss = gameObjectManager.getBoss(),
                missileCount = shipManager.missileCount,
                maxMissiles = shipManager.maxMissiles,
                powerUpCollected = powerUpCollected,
                fuelHpGained = fuelHpGained,
                currentTime = currentTime
            )

            Timber.d("After cleanup: ${gameObjectManager.powerUps.size} power-ups, ${gameObjectManager.asteroids.size} asteroids, ${gameObjectManager.projectiles.size} projectiles, ${gameObjectManager.enemyShips.size} enemy ships, ${gameObjectManager.enemyProjectiles.size} enemy projectiles, ${gameObjectManager.homingProjectiles.size} homing projectiles, HP: ${shipManager.hp}, Fuel: ${shipManager.fuel}, Current Score: $currentScore")
            Timber.d("update called with userId: $userId, currentScore: $currentScore")

            if (shipManager.fuel <= 0 || shipManager.hp <= 0) {
                Timber.d("Game over condition met. Fuel: ${shipManager.fuel}, HP: ${shipManager.hp}, Current score: $currentScore, Continues used: $continuesUsed, Revives: ${shipManager.reviveCount}")
                if (continuesUsed < maxContinues) {
                    gameStateManager.setGameState(
                        GameState.GAME_OVER,
                        shipManager.screenWidth,
                        shipManager.screenHeight,
                        ::resetFlightData,
                        { _ -> },
                        userId
                    )
                    gameStateManager.notifyGameOver(
                        true,
                        shipManager.reviveCount > 0,
                        {
                            Timber.d("Player chose to continue with ad")
                            continuesUsed++
                            shipManager.hp = shipManager.maxHp
                            shipManager.fuel = shipManager.fuelCapacity
                            gameStateManager.setGameState(GameState.FLIGHT, shipManager.screenWidth, shipManager.screenHeight, ::resetFlightData, { _ -> }, userId)
                            Timber.d("Player continued after ad, continues used: $continuesUsed")
                        },
                        {
                            if (shipManager.reviveCount > 0) {
                                Timber.d("Player chose to use revive")
                                shipManager.reviveCount--
                                continuesUsed++
                                shipManager.hp = shipManager.maxHp
                                shipManager.fuel = shipManager.fuelCapacity
                                gameStateManager.setGameState(GameState.FLIGHT, shipManager.screenWidth, shipManager.screenHeight, ::resetFlightData, { _ -> }, userId)
                                Timber.d("Player used a revive, revives remaining: ${shipManager.reviveCount}, continues used: $continuesUsed")
                            } else {
                                Timber.d("No revives available")
                            }
                        },
                        {
                            Timber.d("Player chose to return to build, saving score: $currentScore")
                            saveScore() // Single point of experience saving
                            gameStateManager.setGameState(GameState.BUILD, shipManager.screenWidth, shipManager.screenHeight, ::resetFlightData, { _ -> }, userId)
                            gameObjectManager.clearGameObjects()
                            powerUpManager.resetPowerUpEffects()
                            if (distanceTraveled > longestDistanceTraveled) {
                                longestDistanceTraveled = distanceTraveled
                                onLongestDistanceTraveledChange(longestDistanceTraveled)
                            }
                            if (currentScore > highestScore) {
                                highestScore = currentScore
                                onHighestScoreChange(highestScore)
                            }
                            distanceTraveled = 0f
                            onDistanceTraveledChange(distanceTraveled)
                            currentScore = 0
                            shipManager.level = 1
                            onLevelChange(shipManager.level)
                            shipManager.hp = shipManager.maxHp
                            shipManager.fuel = 0f
                            shipManager.missileCount = shipManager.maxMissiles
                            shipManager.lastMissileRechargeTime = System.currentTimeMillis()
                            Timber.d("Player declined continue, returned to build screen, score saved")
                        }
                    )
                } else {
                    Timber.d("No continues left, forcing return to build with score: $currentScore")
                    saveScore() // Single point of experience saving
                    gameStateManager.setGameState(GameState.BUILD, shipManager.screenWidth, shipManager.screenHeight, ::resetFlightData, { _ -> }, userId)
                    gameObjectManager.clearGameObjects()
                    powerUpManager.resetPowerUpEffects()
                    if (distanceTraveled > longestDistanceTraveled) {
                        longestDistanceTraveled = distanceTraveled
                        onLongestDistanceTraveledChange(longestDistanceTraveled)
                    }
                    if (currentScore > highestScore) {
                        highestScore = currentScore
                        onHighestScoreChange(highestScore)
                    }
                    distanceTraveled = 0f
                    onDistanceTraveledChange(distanceTraveled)
                    currentScore = 0
                    shipManager.level = 1
                    onLevelChange(shipManager.level)
                    shipManager.hp = shipManager.maxHp
                    shipManager.fuel = 0f
                    shipManager.missileCount = shipManager.maxMissiles
                    shipManager.lastMissileRechargeTime = System.currentTimeMillis()
                    Timber.d("No more continues available, returned to build screen, score saved")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in FlightModeManager.update: ${e.message}")
            throw e
        }
    }

    fun resetFlightData() {
        shipManager.reset()
        gameObjectManager.clearGameObjects()
        powerUpManager.resetPowerUpEffects()
        sessionDistanceTraveled = 0f
        levelUpAnimationStartTime = 0L
        glowStartTime = 0L
        continuesUsed = 0
        Timber.d("Flight data reset")
    }

    fun saveScore() {
        if (currentScore > 0) {
            Timber.d("Saving score: $currentScore for userId: $userId")
            gameEngine.get().addExperience(currentScore)
            gameEngine.get().savePersistentData(userId) // Explicitly save to Firestore here
            currentScore = 0 // Reset score after saving to prevent double-saving
        } else {
            Timber.d("No score to save: $currentScore")
        }
    }

    fun launchShip(screenWidth: Float, screenHeight: Float, sortedParts: List<Part>, userId: String?) {
        this.userId = userId ?: "default_user"
        shipManager.launchShip(screenWidth, screenHeight, sortedParts)
        gameStateManager.setGameState(GameState.FLIGHT, screenWidth, screenHeight, ::resetFlightData, { _ -> }, this.userId)
        Timber.d("launchShip called with userId: $userId")
    }

    fun spawnProjectile() {
        val projectileX = shipManager.shipX
        val projectileY = shipManager.shipY - (shipManager.mergedShipBitmap?.height ?: 0) / 2f
        gameObjectManager.projectiles.add(Projectile(projectileX, projectileY, 0f, -shipManager.currentProjectileSpeed, shipManager.screenHeight, shipManager.screenWidth))
    }

    fun launchHomingMissile(target: Any) {
        if (shipManager.missileCount > 0 && gameStateManager.gameState == GameState.FLIGHT) {
            val projectileX = shipManager.shipX
            val projectileY = shipManager.shipY - (shipManager.mergedShipBitmap?.height ?: 0) / 2f
            gameObjectManager.homingProjectiles.add(HomingProjectile(projectileX, projectileY, target, shipManager.screenHeight, shipManager.screenWidth))
            shipManager.missileCount--
            audioManager.playMissileLaunchSound()
            Timber.d("Launched homing missile towards target. Remaining missiles: ${shipManager.missileCount}")
        } else {
            Timber.d("Cannot launch missile: count=${shipManager.missileCount}, gameState=${gameStateManager.gameState}")
        }
    }

    fun destroyAll(): Boolean {
        if (shipManager.destroyAllCharges <= 0) {
            Timber.d("No Destroy All charges available")
            return false
        }
        shipManager.destroyAllCharges--
        gameObjectManager.asteroids.forEach { asteroid ->
            gameObjectManager.renderer.particleSystem.addExplosionParticles(asteroid.x, asteroid.y)
            currentScore += ASTEROID_DESTROY_POINTS
            gameObjectManager.renderer.particleSystem.addScoreTextParticle(asteroid.x, asteroid.y, "+$ASTEROID_DESTROY_POINTS")
        }
        gameObjectManager.enemyShips.forEach { enemy ->
            gameObjectManager.renderer.particleSystem.addExplosionParticles(enemy.x, enemy.y)
            currentScore += 50
            gameObjectManager.renderer.particleSystem.addScoreTextParticle(enemy.x, enemy.y, "+50")
        }
        gameObjectManager.asteroids.clear()
        gameObjectManager.enemyShips.clear()
        gameObjectManager.enemyProjectiles.clear()
        Timber.d("Destroyed all asteroids and enemy ships. New score: $currentScore, remaining charges: ${shipManager.destroyAllCharges}")
        return true
    }

    fun moveShip(direction: Int) {
        shipManager.moveShip(direction)
    }

    fun stopShip() {
        shipManager.stopShip()
    }

    fun onDestroy() {
        Timber.d("FlightModeManager onDestroy called")
        shipManager.onDestroy()
        audioManager.onDestroy()
        gameObjectManager.renderer.onDestroy()
    }
}