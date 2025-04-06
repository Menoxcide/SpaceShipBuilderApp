package com.example.spaceshipbuilderapp

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
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
    private val gameEngine: Lazy<GameEngine>,
    @ApplicationContext private val context: Context
) {
    private val db = FirebaseFirestore.getInstance()

    var currentScore = 0
    var highestScore = 0

    var glowStartTime: Long = 0L
    val glowDuration = 1000L

    private var continuesUsed = 0
    private var adContinuesUsed = 0
    private val maxContinues = 2
    private val maxAdContinues = 3

    var statusBarHeight: Float = 0f

    var distanceTraveled = 0f
    var longestDistanceTraveled = 0f
    private var sessionDistanceTraveled = 0f
    private val distancePerLevel: Float = DISTANCE_PER_LEVEL
    var levelUpAnimationStartTime = 0L

    private var lastScoreUpdateTime = System.currentTimeMillis()
    private var lastDistanceUpdateTime = System.currentTimeMillis()
    private var lastBossCollisionDamageTime = 0L

    private var userId: String = "default_user"

    private var lastHomingMissileTime: Long = 0L
    private val homingMissileCooldown: Long = 500L // Updated to 500ms cooldown

    enum class Environment {
        NORMAL,
        ASTEROID_FIELD,
        NEBULA
    }

    var currentEnvironment: Environment = Environment.NORMAL
        private set

    private var lastEnvironmentChangeTime = System.currentTimeMillis()
    private val environmentChangeInterval = 30000L

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
        const val BOSS_COLLISION_DAMAGE_PER_SECOND = 50f
        const val DISTANCE_PER_LEVEL = 100f
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

            // Update environment
            if (currentTime - lastEnvironmentChangeTime >= environmentChangeInterval) {
                currentEnvironment = Environment.values().random()
                lastEnvironmentChangeTime = currentTime
                audioManager.playEnvironmentChangeSound()
                Timber.d("Environment changed to $currentEnvironment")
            }

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
                environment = currentEnvironment,
                onBossDefeated = {
                    currentScore += 500
                    shipManager.level++
                    onLevelChange(shipManager.level)
                    levelUpAnimationStartTime = currentTime
                    audioManager.playLevelUpSound()
                    repeat(5) {
                        val x = Random.nextFloat() * shipManager.screenWidth
                        val y = Random.nextFloat() * shipManager.screenHeight
                        gameObjectManager.renderer.shipRendererInstance.addCollectionParticles(x, y)
                    }
                    onBossDefeatedChange()
                    // Switch back to regular flight music after boss defeat
                    audioManager.playBackgroundMusic(R.raw.battle_in_the_stars)
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
                    audioManager.playLevelUpSound()
                    repeat(5) {
                        val x = Random.nextFloat() * shipManager.screenWidth
                        val y = Random.nextFloat() * shipManager.screenHeight
                        gameObjectManager.renderer.shipRendererInstance.addCollectionParticles(x, y)
                    }
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

            val (scoreDelta, newGlowStartTime, isCollidingWithBoss) = collisionManager.checkCollisions(
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
                            gameObjectManager.renderer.shipRendererInstance.addPowerUpTextParticle(powerUp.x, powerUp.y, "Fuel +20", powerUp.type)
                            Timber.d("Collected power-up: Fuel +20, HP +10")
                            fuelHpGained = true
                        }
                        "shield", "speed", "stealth", "invincibility" -> {
                            powerUpManager.applyPowerUpEffect(powerUp.type, shipManager)
                            gameObjectManager.renderer.shipRendererInstance.addPowerUpTextParticle(powerUp.x, powerUp.y, powerUp.type.replaceFirstChar { it.uppercase() }, powerUp.type)
                        }
                        "warp" -> {
                            shipManager.shipX = Random.nextFloat() * (shipManager.screenWidth - 2 * shipManager.maxPartHalfWidth) + shipManager.maxPartHalfWidth
                            shipManager.shipY = Random.nextFloat() * (shipManager.screenHeight - shipManager.totalShipHeight) + shipManager.totalShipHeight / 2
                            gameObjectManager.renderer.shipRendererInstance.addPowerUpTextParticle(powerUp.x, powerUp.y, "Warp", powerUp.type)
                            Timber.d("Collected warp power-up")
                        }
                        "star" -> {
                            currentScore += 50
                            shipManager.starsCollected += 1
                            onStarsCollectedChange(shipManager.starsCollected)
                            gameObjectManager.renderer.shipRendererInstance.addPowerUpTextParticle(powerUp.x, powerUp.y, "Star +50", powerUp.type)
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
                    if (enemy.shotInterval == 0L && gameObjectManager.getBoss() != null) {
                        // Damage handled below per second
                    } else {
                        shipManager.hp -= enemy.damage
                    }
                }
            )

            currentScore += scoreDelta
            if (newGlowStartTime > 0L) {
                glowStartTime = newGlowStartTime
            }

            if (isCollidingWithBoss) {
                val timeSinceLastDamage = (currentTime - lastBossCollisionDamageTime).toFloat() / 1000f
                val damageToApply = BOSS_COLLISION_DAMAGE_PER_SECOND * timeSinceLastDamage
                shipManager.hp -= damageToApply
                Timber.d("Boss collision damage applied: $damageToApply HP, new HP: ${shipManager.hp}")

                if (timeSinceLastDamage >= 1f) {
                    gameObjectManager.renderer.shipRendererInstance.addDamageTextParticle(shipManager.shipX, shipManager.shipY, 50)
                    lastBossCollisionDamageTime = currentTime
                }
            } else {
                lastBossCollisionDamageTime = currentTime
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
                currentTime = currentTime,
                fuel = shipManager.fuel,
                hp = shipManager.hp,
                distanceTraveled = distanceTraveled
            )

            Timber.d("After cleanup: ${gameObjectManager.powerUps.size} power-ups, ${gameObjectManager.asteroids.size} asteroids, ${gameObjectManager.projectiles.size} projectiles, ${gameObjectManager.enemyShips.size} enemy ships, ${gameObjectManager.enemyProjectiles.size} enemy projectiles, ${gameObjectManager.homingProjectiles.size} homing projectiles, HP: ${shipManager.hp}, Fuel: ${shipManager.fuel}, Current Score: $currentScore")
            Timber.d("update called with userId: $userId, currentScore: $currentScore")

            if (shipManager.fuel <= 0 || shipManager.hp <= 0) {
                Timber.d("Game over condition met. Fuel: ${shipManager.fuel}, HP: ${shipManager.hp}, Current score: $currentScore, Continues used: $continuesUsed, Ad continues used: $adContinuesUsed, Revives: ${shipManager.reviveCount}")
                gameStateManager.setGameState(
                    GameState.GAME_OVER,
                    shipManager.screenWidth,
                    shipManager.screenHeight,
                    ::resetFlightData,
                    { _ -> },
                    userId,
                    gameEngine.get()
                )
                val canContinueWithAd = adContinuesUsed < maxAdContinues
                val canUseRevive = shipManager.reviveCount > 0
                val onContinueWithAd: () -> Unit = {
                    Timber.d("Player chose to continue with ad")
                    continuesUsed++
                    adContinuesUsed++
                    shipManager.hp = shipManager.maxHp
                    shipManager.fuel = shipManager.fuelCapacity
                    gameStateManager.setGameState(
                        GameState.FLIGHT,
                        shipManager.screenWidth,
                        shipManager.screenHeight,
                        ::resetFlightData,
                        { _ -> },
                        userId,
                        gameEngine.get()
                    )
                    Timber.d("Player continued after ad, continues used: $continuesUsed, ad continues used: $adContinuesUsed")
                }
                val onContinueWithRevive: () -> Unit = {
                    if (shipManager.reviveCount > 0) {
                        Timber.d("Player chose to use revive")
                        shipManager.reviveCount--
                        continuesUsed++
                        shipManager.hp = shipManager.maxHp
                        shipManager.fuel = shipManager.fuelCapacity
                        gameStateManager.setGameState(
                            GameState.FLIGHT,
                            shipManager.screenWidth,
                            shipManager.screenHeight,
                            ::resetFlightData,
                            { _ -> },
                            userId,
                            gameEngine.get()
                        )
                        Timber.d("Player used a revive, revives remaining: ${shipManager.reviveCount}, continues used: $continuesUsed")
                    } else {
                        Timber.d("No revives available")
                    }
                }
                val onReturnToBuild: () -> Unit = {
                    Timber.d("Player chose to return to build, saving score: $currentScore")
                    if (currentScore > 0) {
                        gameEngine.get().addExperience(currentScore)
                        gameEngine.get().savePersistentData(userId)
                        currentScore = 0
                    }
                    runBlocking {
                        try {
                            val gameStateRef = db.collection("users").document(userId).collection("gameState").document("gameState")
                            val pausedStateRef = db.collection("users").document(userId).collection("gameState").document("pausedState")
                            gameStateRef.set(mapOf("shouldLoadPausedState" to false)).await()
                            Timber.d("Set shouldLoadPausedState to false in Firebase for userId: $userId")
                            pausedStateRef.delete().await()
                            Timber.d("Deleted pausedState document in Firebase for userId: $userId")
                            val updatedGameState = gameStateRef.get().await()
                            val updatedShouldLoad = updatedGameState.getBoolean("shouldLoadPausedState") ?: true
                            if (!updatedShouldLoad) {
                                Timber.d("Verified shouldLoadPausedState is false in Firebase for userId: $userId")
                            } else {
                                Timber.w("Verification failed: shouldLoadPausedState still true in Firebase")
                            }
                            val pausedStateExists = pausedStateRef.get().await().exists()
                            if (!pausedStateExists) {
                                Timber.d("Verified pausedState document is deleted in Firebase for userId: $userId")
                            } else {
                                Timber.w("Verification failed: pausedState document still exists in Firebase")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to ensure Firebase state cleanup: ${e.message}")
                            gameEngine.get().resetPausedState()
                            gameStateManager.resetPausedState()
                        }
                    }
                    gameEngine.get().resetPausedState()
                    gameStateManager.setGameState(
                        GameState.BUILD,
                        shipManager.screenWidth,
                        shipManager.screenHeight,
                        ::resetFlightData,
                        { _ -> },
                        userId,
                        gameEngine.get()
                    )
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
                    Timber.d("Player declined continue, returned to build screen, score saved, paused state cleared")
                }
                gameStateManager.notifyGameOver(
                    canContinue = canContinueWithAd,
                    canUseRevive = canUseRevive,
                    onContinueWithAd = onContinueWithAd,
                    onContinueWithRevive = onContinueWithRevive,
                    onReturnToBuild = onReturnToBuild
                )
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
        adContinuesUsed = 0
        lastBossCollisionDamageTime = 0L
        currentEnvironment = Environment.NORMAL
        lastEnvironmentChangeTime = System.currentTimeMillis()
        Timber.d("Flight data reset")
    }

    fun saveScore() {
        Timber.d("saveScore called, but score will be saved only under specific conditions")
    }

    fun launchShip(screenWidth: Float, screenHeight: Float, sortedParts: List<Part>, userId: String?) {
        this.userId = userId ?: "default_user"
        val isResuming = gameStateManager.getPausedState() != null
        shipManager.launchShip(screenWidth, screenHeight, sortedParts, isResuming, gameEngine.get().selectedWeapon)
        gameStateManager.setGameState(GameState.FLIGHT, screenWidth, screenHeight, ::resetFlightData, { _ -> }, this.userId, gameEngine.get())
        Timber.d("launchShip called with userId: $userId, isResuming: $isResuming, selectedWeapon: ${gameEngine.get().selectedWeapon}")
    }

    fun spawnProjectile() {
        val projectileX = shipManager.shipX
        val projectileY = shipManager.shipY - (shipManager.mergedShipBitmap?.height ?: 0) / 2f
        val projectile = when (shipManager.selectedWeapon) {
            WeaponType.Default -> Projectile(
                projectileX,
                projectileY,
                0f,
                -shipManager.currentProjectileSpeed,
                shipManager.screenHeight,
                shipManager.screenWidth,
                WeaponType.Default
            )
            WeaponType.Plasma -> PlasmaProjectile(
                projectileX,
                projectileY,
                0f,
                -shipManager.currentProjectileSpeed,
                shipManager.screenHeight,
                shipManager.screenWidth,
                WeaponType.Plasma
            )
            WeaponType.Missile -> MissileProjectile(
                projectileX,
                projectileY,
                0f,
                -shipManager.currentProjectileSpeed,
                shipManager.screenHeight,
                shipManager.screenWidth,
                WeaponType.Missile
            )
            WeaponType.HomingMissile -> {
                throw IllegalStateException("Homing missiles should be launched via launchHomingMissile, not spawnProjectile")
            }
            WeaponType.Laser -> LaserProjectile(
                projectileX,
                projectileY,
                0f,
                -shipManager.currentProjectileSpeed,
                shipManager.screenHeight,
                shipManager.screenWidth,
                WeaponType.Laser
            )
        }
        gameObjectManager.projectiles.add(projectile)
        audioManager.playShootSound()
        Timber.d("Spawned projectile of type ${projectile.javaClass.simpleName} at ($projectileX, $projectileY) with weaponType ${projectile.weaponType}")
    }

    fun launchHomingMissile(target: Any) {
        val currentTime = System.currentTimeMillis()
        if (shipManager.missileCount > 0 && currentTime - lastHomingMissileTime >= homingMissileCooldown && gameStateManager.gameState == GameState.FLIGHT) {
            val projectileX = shipManager.shipX
            val projectileY = shipManager.shipY - (shipManager.mergedShipBitmap?.height ?: 0) / 2f
            val homingProjectile = HomingProjectile().apply { // No target in constructor, set it after
                this.x = projectileX
                this.y = projectileY
                this.screenHeight = shipManager.screenHeight
                this.screenWidth = shipManager.screenWidth
                this.weaponType = WeaponType.HomingMissile
                this.target = target // Set the inherited target property
            }
            gameObjectManager.homingProjectiles.add(homingProjectile)
            shipManager.missileCount--
            lastHomingMissileTime = currentTime // Update last launch time
            audioManager.playMissileLaunchSound()
            Timber.d("Launched homing missile towards target. Remaining missiles: ${shipManager.missileCount}")
        } else {
            Timber.d("Cannot launch missile: count=${shipManager.missileCount}, gameState=${gameStateManager.gameState}, timeSinceLast=${currentTime - lastHomingMissileTime}ms")
        }
    }

    fun destroyAll(): Boolean {
        if (shipManager.destroyAllCharges <= 0) {
            Timber.d("No Destroy All charges available")
            return false
        }
        shipManager.destroyAllCharges--
        gameObjectManager.asteroids.forEach { asteroid ->
            gameObjectManager.renderer.shipRendererInstance.addExplosionParticles(asteroid.x, asteroid.y)
            currentScore += ASTEROID_DESTROY_POINTS
            gameObjectManager.renderer.shipRendererInstance.addScoreTextParticle(asteroid.x, asteroid.y, "+$ASTEROID_DESTROY_POINTS")
        }
        gameObjectManager.enemyShips.forEach { enemy ->
            gameObjectManager.renderer.shipRendererInstance.addExplosionParticles(enemy.x, enemy.y)
            currentScore += 50
            gameObjectManager.renderer.shipRendererInstance.addScoreTextParticle(enemy.x, enemy.y, "+50")
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

    fun getSessionDistanceTraveled(): Float = sessionDistanceTraveled
    fun setSessionDistanceTraveled(value: Float) {
        sessionDistanceTraveled = value
    }

    fun getLastScoreUpdateTime(): Long = lastScoreUpdateTime
    fun setLastScoreUpdateTime(value: Long) {
        lastScoreUpdateTime = value
    }

    fun getLastDistanceUpdateTime(): Long = lastDistanceUpdateTime
    fun setLastDistanceUpdateTime(value: Long) {
        lastDistanceUpdateTime = value
    }

    fun getContinuesUsed(): Int = continuesUsed
    fun setContinuesUsed(value: Int) {
        continuesUsed = value
    }

    fun getDistancePerLevel(): Float = distancePerLevel
}