package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.media.MediaPlayer
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.tasks.await

enum class GameState { BUILD, FLIGHT, GAME_OVER }

class GameEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    val renderer: Renderer,
    private val highscoreManager: HighscoreManager
) {
    private val db = FirebaseFirestore.getInstance()
    var gameState: GameState = GameState.BUILD
        set(value) {
            if (field == value) return
            field = value
            if (value == GameState.FLIGHT) {
                onLaunchListener?.invoke(true)
                sessionDistanceTraveled = 0f // Reset session distance for the new flight
                levelUpAnimationStartTime = 0L
                shipX = screenWidth / 2f
                shipY = screenHeight / 2f
                applyShipColorEffects()
            } else if (value == GameState.BUILD) {
                mergedShipBitmap?.recycle()
                mergedShipBitmap = null
                shipX = screenWidth / 2f
                shipY = screenHeight / 2f
                initializePlaceholders(
                    screenWidth,
                    screenHeight,
                    renderer.cockpitPlaceholderBitmap,
                    renderer.fuelTankPlaceholderBitmap,
                    renderer.enginePlaceholderBitmap,
                    statusBarHeight
                )
                parts.clear()
                onLaunchListener?.invoke(false)
                resetPowerUpEffects()
                // Update longestDistanceTraveled, highestScore, and highestLevel before resetting
                if (distanceTraveled > longestDistanceTraveled) {
                    longestDistanceTraveled = distanceTraveled
                }
                if (currentScore > highestScore) {
                    highestScore = currentScore
                }
                if (level > highestLevel) {
                    highestLevel = level
                }
                // Reset distanceTraveled, currentScore, and level
                distanceTraveled = 0f
                currentScore = 0
                level = 1
                projectiles.clear()
                enemyShips.clear()
                enemyProjectiles.clear()
                glowStartTime = 0L
                continuesUsed = 0
                // Save persistent data when returning to build mode
                if (userId != null) {
                    savePersistentData(userId!!)
                }
            }
        }

    val parts = mutableListOf<Part>()
    val placeholders = mutableListOf<Part>()
    var selectedPart: Part? = null
    var fuel = 0f
    var fuelCapacity = 100f
    var hp = 100f
    var maxHp = 100f
    var level = 1
        set(value) {
            if (field != value) {
                field = value
                updateUnlockedShipSets()
                if (userId != null) {
                    savePersistentData(userId!!)
                }
            }
        }
    var playerName: String = "Player"
    private var onLaunchListener: ((Boolean) -> Unit)? = null
    var onPowerUpCollectedListener: ((Float, Float) -> Unit)? = null
    var onGameOverListener: ((Boolean, () -> Unit, () -> Unit) -> Unit)? = null
    var currentScore = 0
    var highestScore = 0 // Highest score achieved, loaded from/saved to Firebase
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    var highestLevel = 1 // Highest level achieved, loaded from/saved to Firebase
        set(value) {
            field = value
            updateUnlockedShipSets()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    var starsCollected = 0 // Number of star power-ups collected, loaded from/saved to Firebase
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var shipColor: String = "default"
        set(value) {
            field = value
            Timber.d("Ship color set to $value")
            if (gameState == GameState.FLIGHT) applyShipColorEffects()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    // Ship set selection
    var selectedShipSet: Int = 0 // 0 = default, 1 = ship set 1, 2 = ship set 2
        set(value) {
            if (value in unlockedShipSets) {
                field = value
                if (userId != null) {
                    savePersistentData(userId!!)
                }
                Timber.d("Selected ship set: $value")
            } else {
                Timber.w("Cannot select ship set $value, not unlocked yet")
            }
        }
    private val unlockedShipSets = mutableSetOf(0) // Default ship set is always unlocked

    var shipX: Float = 0f
    var shipY: Float = 0f
    var totalShipHeight: Float = 0f
    var maxPartHalfWidth: Float = 0f
    var mergedShipBitmap: Bitmap? = null

    val powerUps = mutableListOf<PowerUp>()
    val asteroids = mutableListOf<Asteroid>()
    val projectiles = mutableListOf<Projectile>()
    val enemyShips = mutableListOf<EnemyShip>()
    val enemyProjectiles = mutableListOf<Projectile>()
    private val powerUpSpawnRateBase = 2000L
    private val asteroidSpawnRateBase = 1500L
    private val enemySpawnRateBase = 5000L
    private var lastPowerUpSpawnTime = System.currentTimeMillis()
    private var lastAsteroidSpawnTime = System.currentTimeMillis()
    private var lastEnemySpawnTime = System.currentTimeMillis()
    private var lastScoreUpdateTime = System.currentTimeMillis()
    private var lastDistanceUpdateTime = System.currentTimeMillis()

    // Glow effect variables
    var glowStartTime: Long = 0L
    val glowDuration = 1000L

    // Continue logic
    private var continuesUsed = 0
    private val maxContinues = 2

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f
    var statusBarHeight: Float = 0f

    var cockpitY: Float = 0f
    var fuelTankY: Float = 0f
    var engineY: Float = 0f

    private var mediaPlayer: MediaPlayer? = null

    var shieldActive = false
    private var shieldEndTime = 0L
    var speedBoostActive = false
    private var speedBoostEndTime = 0L
    var stealthActive = false
    private var stealthEndTime = 0L
    var invincibilityActive = false
    private var invincibilityEndTime = 0L
    private val effectDuration = 10000L
    private var baseFuelConsumption = 0.05f
    private var currentFuelConsumption = baseFuelConsumption
    private var baseSpeed = 5f
    private var currentSpeed = baseSpeed

    var distanceTraveled = 0f // Total distance traveled in the current game, reset after crash
        set(value) {
            field = value
            // Save persistent data whenever distanceTraveled changes significantly
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    var longestDistanceTraveled = 0f // Longest distance traveled, loaded from/saved to Firebase
        set(value) {
            field = value
            updateUnlockedShipSets()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    private var sessionDistanceTraveled = 0f // Distance traveled in the current flight session
    private val distancePerLevel = 100f
    var levelUpAnimationStartTime = 0L
    private var userId: String? = null

    companion object {
        const val ALIGNMENT_THRESHOLD = 75f
        const val PANEL_HEIGHT = 150f
        const val LEVEL_UP_ANIMATION_DURATION = 5000L
        const val GIANT_ASTEROID_PROBABILITY = 0.1f
        const val GIANT_ASTEROID_SCALE = 2.0f
        const val SMALL_ASTEROID_COUNT = 3
        const val EXPLOSION_CHANCE = 0.1f
        const val MAX_AURA_PARTICLES = 200
        const val PROJECTILE_SPEED = 10f
        const val PROJECTILE_SIZE = 5f
        const val ASTEROID_DESTROY_POINTS = 20
    }

    suspend fun loadUserData(userId: String) {
        this.userId = userId
        try {
            Timber.d("Attempting to load user data for userId: $userId")
            val doc = db.collection("users").document(userId).get().await()
            if (doc.exists()) {
                level = doc.getLong("level")?.toInt() ?: 1
                shipColor = doc.getString("shipColor") ?: "default"
                selectedShipSet = doc.getLong("selectedShipSet")?.toInt() ?: 0
                distanceTraveled = doc.getDouble("distanceTraveled")?.toFloat() ?: 0f
                longestDistanceTraveled = doc.getDouble("longestDistanceTraveled")?.toFloat() ?: 0f
                highestScore = doc.getLong("highestScore")?.toInt() ?: 0
                highestLevel = doc.getLong("highestLevel")?.toInt() ?: 1
                starsCollected = doc.getLong("starsCollected")?.toInt() ?: 0
                updateUnlockedShipSets()
                Timber.d("Loaded user data for $userId: level=$level, shipColor=$shipColor, selectedShipSet=$selectedShipSet, distanceTraveled=$distanceTraveled, longestDistanceTraveled=$longestDistanceTraveled, highestScore=$highestScore, highestLevel=$highestLevel, starsCollected=$starsCollected")
            } else {
                Timber.d("User data not found for $userId, initializing with defaults")
                val userData = hashMapOf(
                    "level" to 1,
                    "shipColor" to "default",
                    "selectedShipSet" to 0,
                    "distanceTraveled" to 0f,
                    "longestDistanceTraveled" to 0f,
                    "highestScore" to 0,
                    "highestLevel" to 1,
                    "starsCollected" to 0
                )
                db.collection("users").document(userId).set(userData).await()
                level = 1
                shipColor = "default"
                selectedShipSet = 0
                distanceTraveled = 0f
                longestDistanceTraveled = 0f
                highestScore = 0
                highestLevel = 1
                starsCollected = 0
                updateUnlockedShipSets()
                Timber.d("Initialized new user data for $userId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load user data from Firestore: ${e.message}")
            level = 1
            shipColor = "default"
            selectedShipSet = 0
            distanceTraveled = 0f
            longestDistanceTraveled = 0f
            highestScore = 0
            highestLevel = 1
            starsCollected = 0
            updateUnlockedShipSets()
            Timber.w("Using default user data due to Firestore error: level=$level, shipColor=$shipColor, selectedShipSet=$selectedShipSet, distanceTraveled=$distanceTraveled, longestDistanceTraveled=$longestDistanceTraveled, highestScore=$highestScore, highestLevel=$highestLevel, starsCollected=$starsCollected")
        }
    }

    private fun updateUnlockedShipSets() {
        unlockedShipSets.clear()
        unlockedShipSets.add(0) // Default ship set is always unlocked
        // Ship Set 2: Requires level 20 and 20 stars
        if (highestLevel >= 20 && starsCollected >= 20) {
            unlockedShipSets.add(1)
        }
        // Ship Set 3: Requires level 40 and 40 stars
        if (highestLevel >= 40 && starsCollected >= 40) {
            unlockedShipSets.add(2)
        }
        // Ensure selected ship set is still valid
        if (selectedShipSet !in unlockedShipSets) {
            selectedShipSet = 0
        }
        Timber.d("Updated unlocked ship sets: $unlockedShipSets")
    }

    // Add the unlockShipSet method to fix the compilation error
    fun unlockShipSet(set: Int) {
        if (set in 1..2) {
            unlockedShipSets.add(set)
            Timber.d("Manually unlocked ship set $set")
            updateUnlockedShipSets()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        } else {
            Timber.w("Invalid ship set $set to unlock")
        }
    }

    fun getUnlockedShipSets(): Set<Int> {
        return unlockedShipSets.toSet()
    }

    fun setScreenDimensions(width: Float, height: Float, statusBarHeight: Float = this.statusBarHeight) {
        screenWidth = width
        screenHeight = height
        this.statusBarHeight = statusBarHeight
        Timber.d("GameEngine screen dimensions set: width=$screenWidth, height=$screenHeight, statusBarHeight=$statusBarHeight")
    }

    fun initializePlaceholders(
        screenWidth: Float,
        screenHeight: Float,
        cockpitPlaceholderBitmap: Bitmap,
        fuelTankPlaceholderBitmap: Bitmap,
        enginePlaceholderBitmap: Bitmap,
        statusBarHeight: Float = this.statusBarHeight
    ) {
        val contentHeight = screenHeight - statusBarHeight - PANEL_HEIGHT
        val cockpitHeight = renderer.cockpitBitmap.height.toFloat()
        val fuelTankHeight = renderer.fuelTankBitmap.height.toFloat()
        val engineHeight = renderer.engineBitmap.height.toFloat()

        val totalHeight = cockpitHeight + fuelTankHeight + engineHeight
        val startY = statusBarHeight + (contentHeight - totalHeight) / 2f

        cockpitY = startY + cockpitHeight / 2f
        fuelTankY = cockpitY + cockpitHeight / 2f + fuelTankHeight / 2f
        engineY = fuelTankY + fuelTankHeight / 2f + engineHeight / 2f

        placeholders.clear()
        placeholders.add(Part("cockpit", cockpitPlaceholderBitmap, screenWidth / 2f, cockpitY, 0f, 1f))
        placeholders.add(Part("fuel_tank", fuelTankPlaceholderBitmap, screenWidth / 2f, fuelTankY, 0f, 1f))
        placeholders.add(Part("engine", enginePlaceholderBitmap, screenWidth / 2f, engineY, 0f, 1f))
        Timber.d("Placeholders initialized: cockpitY=$cockpitY, fuelTankY=$fuelTankY, engineY=$engineY")
    }

    fun update(screenWidth: Float, screenHeight: Float, userId: String) {
        if (gameState != GameState.FLIGHT) {
            Timber.d("GameEngine update skipped: gameState=$gameState")
            return
        }

        if (this.screenHeight == 0f || this.screenWidth == 0f) {
            Timber.w("screenHeight or screenWidth not set, using passed values: width=$screenWidth, height=$screenHeight")
            this.screenWidth = screenWidth
            this.screenHeight = screenHeight
        }

        val currentTime = System.currentTimeMillis()
        if (shieldActive && currentTime > shieldEndTime) {
            shieldActive = false
            currentFuelConsumption = baseFuelConsumption
            Timber.d("Shield effect ended, fuel consumption reset to $currentFuelConsumption")
        }
        if (speedBoostActive && currentTime > speedBoostEndTime) {
            speedBoostActive = false
            currentSpeed = baseSpeed
            Timber.d("Speed boost ended, speed reset to $currentSpeed")
        }
        if (stealthActive && currentTime > stealthEndTime) {
            stealthActive = false
            Timber.d("Stealth effect ended")
        }
        if (invincibilityActive && currentTime > invincibilityEndTime) {
            invincibilityActive = false
            Timber.d("Invincibility ended")
        }

        fuel -= currentFuelConsumption
        shipX = shipX.coerceIn(maxPartHalfWidth, this.screenWidth - maxPartHalfWidth)
        shipY = shipY.coerceIn(totalShipHeight / 2, this.screenHeight - totalShipHeight / 2)

        if (currentTime - lastDistanceUpdateTime >= 1000) {
            sessionDistanceTraveled += currentSpeed
            distanceTraveled += currentSpeed // Update total distance for the current game
            lastDistanceUpdateTime = currentTime
            Timber.d("Session distance traveled: $sessionDistanceTraveled, Total distance traveled: $distanceTraveled")

            // Use total distanceTraveled for level calculation
            val totalDistanceForLevel = distanceTraveled
            if (totalDistanceForLevel >= distancePerLevel * level) {
                level++
                levelUpAnimationStartTime = currentTime
                Timber.d("Level advanced to $level based on total distance traveled: $totalDistanceForLevel")
            }
        }

        if (currentTime - lastScoreUpdateTime >= 1000) {
            currentScore += 10
            lastScoreUpdateTime = currentTime
        }

        val powerUpSpawnRate = (powerUpSpawnRateBase * (1 + level * 0.05f)).toLong()
        val asteroidSpawnRate = (asteroidSpawnRateBase / (1 + level * 0.1f)).toLong()
        val enemySpawnRate = (enemySpawnRateBase / (1 + level * 0.1f)).toLong()

        if (currentTime - lastPowerUpSpawnTime >= powerUpSpawnRate) {
            spawnPowerUp(this.screenWidth)
            lastPowerUpSpawnTime = currentTime
            Timber.d("Spawned power-up at time: $currentTime, count: ${powerUps.size}")
        }

        if (currentTime - lastAsteroidSpawnTime >= asteroidSpawnRate) {
            spawnAsteroid(this.screenWidth)
            lastAsteroidSpawnTime = currentTime
            Timber.d("Spawned asteroid at time: $currentTime, count: ${asteroids.size}")
        }

        if (currentTime - lastEnemySpawnTime >= enemySpawnRate) {
            spawnEnemyShip(this.screenWidth)
            lastEnemySpawnTime = currentTime
            Timber.d("Spawned enemy ship at time: $currentTime, count: ${enemyShips.size}")
        }

        powerUps.forEach { it.update(this.screenHeight) }
        projectiles.forEach { it.update() }
        enemyShips.forEach { enemy ->
            enemy.y += enemy.speedY
            if (currentTime - enemy.lastShotTime >= enemy.shotInterval) {
                enemyProjectiles.add(Projectile(enemy.x, enemy.y, PROJECTILE_SPEED, this.screenHeight))
                enemy.lastShotTime = currentTime
            }
        }
        enemyProjectiles.forEach { it.update() }

        val asteroidsCopy = asteroids.toMutableList()
        asteroidsCopy.forEach { it.update(this.screenWidth, this.screenHeight, level) }

        val powerUpsToRemove = mutableListOf<PowerUp>()
        val asteroidsToRemove = mutableListOf<Asteroid>()
        val projectilesToRemove = mutableListOf<Projectile>()
        val enemyShipsToRemove = mutableListOf<EnemyShip>()
        val enemyProjectilesToRemove = mutableListOf<Projectile>()

        // Projectile-asteroid collisions
        for (projectile in projectiles) {
            val projectileRect = RectF(
                projectile.x - PROJECTILE_SIZE,
                projectile.y - PROJECTILE_SIZE,
                projectile.x + PROJECTILE_SIZE,
                projectile.y + PROJECTILE_SIZE
            )
            for (asteroid in asteroidsCopy) {
                val asteroidRect = RectF(
                    asteroid.x - asteroid.size,
                    asteroid.y - asteroid.size,
                    asteroid.x + asteroid.size,
                    asteroid.y + asteroid.size
                )
                if (projectileRect.intersect(asteroidRect)) {
                    asteroidsToRemove.add(asteroid)
                    projectilesToRemove.add(projectile)
                    currentScore += ASTEROID_DESTROY_POINTS
                    renderer.particleSystem.addExplosionParticles(asteroid.x, asteroid.y)
                    renderer.particleSystem.addScoreTextParticle(asteroid.x, asteroid.y, "+$ASTEROID_DESTROY_POINTS")
                    Timber.d("Projectile hit asteroid at (x=${asteroid.x}, y=${asteroid.y}), score increased by $ASTEROID_DESTROY_POINTS to $currentScore")
                }
            }
        }

        // Projectile-enemy collisions
        for (projectile in projectiles) {
            val projectileRect = RectF(
                projectile.x - PROJECTILE_SIZE,
                projectile.y - PROJECTILE_SIZE,
                projectile.x + PROJECTILE_SIZE,
                projectile.y + PROJECTILE_SIZE
            )
            for (enemy in enemyShips) {
                val enemyRect = RectF(
                    enemy.x - 50f,
                    enemy.y - 50f,
                    enemy.x + 50f,
                    enemy.y + 50f
                )
                if (projectileRect.intersect(enemyRect)) {
                    enemyShipsToRemove.add(enemy)
                    projectilesToRemove.add(projectile)
                    currentScore += 50
                    renderer.particleSystem.addExplosionParticles(enemy.x, enemy.y)
                    renderer.particleSystem.addScoreTextParticle(enemy.x, enemy.y, "+50")
                    Timber.d("Projectile hit enemy ship at (x=${enemy.x}, y=${enemy.y}), score increased by 50 to $currentScore")
                }
            }
        }

        // Power-up collection
        for (powerUp in powerUps) {
            val powerUpRect = RectF(
                powerUp.x - 20f, powerUp.y - 20f,
                powerUp.x + 20f, powerUp.y + 20f
            )
            if (checkCollision(powerUpRect)) {
                when (powerUp.type) {
                    "power_up" -> {
                        fuel = (fuel + 20f).coerceAtMost(fuelCapacity)
                        hp = (hp + 10f).coerceAtMost(maxHp)
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Fuel +20", powerUp.type)
                        Timber.d("Collected power-up: Fuel +20, HP +10 at (x=${powerUp.x}, y=${powerUp.y})")
                    }
                    "shield" -> {
                        shieldActive = true
                        shieldEndTime = currentTime + effectDuration
                        currentFuelConsumption = baseFuelConsumption / 2f
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Shield", powerUp.type)
                        Timber.d("Added power-up text for Shield at (x=${powerUp.x}, y=${powerUp.y})")
                    }
                    "speed" -> {
                        speedBoostActive = true
                        speedBoostEndTime = currentTime + effectDuration
                        currentSpeed = baseSpeed * 5f
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Speed Boost", powerUp.type)
                        Timber.d("Added power-up text for Speed Boost at (x=${powerUp.x}, y=${powerUp.y}) with 5x speed")
                    }
                    "stealth" -> {
                        stealthActive = true
                        shieldEndTime = currentTime + effectDuration
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Stealth", powerUp.type)
                        Timber.d("Added power-up text for Stealth at (x=${powerUp.x}, y=${powerUp.y})")
                    }
                    "warp" -> {
                        shipX = Random.nextFloat() * (this.screenWidth - 2 * maxPartHalfWidth) + maxPartHalfWidth
                        shipY = Random.nextFloat() * (this.screenHeight - totalShipHeight) + totalShipHeight / 2
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Warp", powerUp.type)
                        Timber.d("Added power-up text for Warp at (x=${powerUp.x}, y=${powerUp.y})")
                    }
                    "star" -> {
                        currentScore += 50
                        starsCollected += 1 // Increment starsCollected when a star power-up is collected
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Star +50", powerUp.type)
                        Timber.d("Added power-up text for Star +50 at (x=${powerUp.x}, y=${powerUp.y}), starsCollected=$starsCollected")
                    }
                    "invincibility" -> {
                        invincibilityActive = true
                        invincibilityEndTime = currentTime + effectDuration
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Invincibility", powerUp.type)
                        Timber.d("Added power-up text for Invincibility at (x=${powerUp.x}, y=${powerUp.y})")
                    }
                }
                renderer.particleSystem.addPowerUpSpriteParticles(shipX, shipY, powerUp.type)
                powerUpsToRemove.add(powerUp)
                Timber.d("Collected ${powerUp.type} power-up")
                onPowerUpCollectedListener?.invoke(powerUp.x, powerUp.y)
                playPowerUpSound()
            }
        }

        // Asteroid collisions
        for (asteroid in asteroidsCopy) {
            val asteroidRect = RectF(
                asteroid.x - asteroid.size, asteroid.y - asteroid.size,
                asteroid.x + asteroid.size, asteroid.y + asteroid.size
            )
            if (checkCollision(asteroidRect) && !stealthActive && !invincibilityActive) {
                hp -= 10f
                asteroidsToRemove.add(asteroid)
                renderer.particleSystem.addCollisionParticles(shipX, shipY)
                renderer.particleSystem.addDamageTextParticle(shipX, shipY, 10)
                playCollisionSound()
                glowStartTime = currentTime
                Timber.d("Hit asteroid, HP decreased to $hp, glow triggered")
            }
            if (asteroid is GiantAsteroid) {
                if (Random.nextFloat() < EXPLOSION_CHANCE) {
                    asteroid.explode(this)
                }
            }
        }

        // Enemy projectile-player collisions
        for (projectile in enemyProjectiles) {
            val projectileRect = RectF(
                projectile.x - PROJECTILE_SIZE,
                projectile.y - PROJECTILE_SIZE,
                projectile.x + PROJECTILE_SIZE,
                projectile.y + PROJECTILE_SIZE
            )
            if (checkCollision(projectileRect) && !stealthActive && !invincibilityActive) {
                hp -= 10f
                enemyProjectilesToRemove.add(projectile)
                renderer.particleSystem.addCollisionParticles(shipX, shipY)
                renderer.particleSystem.addDamageTextParticle(shipX, shipY, 10)
                playCollisionSound()
                glowStartTime = currentTime
                Timber.d("Hit by enemy projectile, HP decreased to $hp, glow triggered")
            }
        }

        // Ship-enemy collisions
        for (enemy in enemyShips) {
            val enemyRect = RectF(
                enemy.x - 50f,
                enemy.y - 50f,
                enemy.x + 50f,
                enemy.y + 50f
            )
            if (checkCollision(enemyRect) && !stealthActive && !invincibilityActive) {
                hp -= 25f
                enemyShipsToRemove.add(enemy)
                renderer.particleSystem.addCollisionParticles(shipX, shipY)
                renderer.particleSystem.addDamageTextParticle(shipX, shipY, 25)
                playCollisionSound()
                glowStartTime = currentTime
                Timber.d("Collided with enemy ship, HP decreased to $hp, glow triggered")
            }
        }

        powerUps.removeAll(powerUpsToRemove)
        asteroids.removeAll(asteroidsToRemove)
        projectiles.removeAll(projectilesToRemove)
        enemyShips.removeAll(enemyShipsToRemove)
        enemyProjectiles.removeAll(enemyProjectilesToRemove)

        powerUps.removeAll { it.isExpired(this.screenHeight) }
        asteroids.removeAll { it.isOffScreen(this.screenHeight) }
        projectiles.removeAll { it.isOffScreen() }
        enemyProjectiles.removeAll { it.isOffScreen() }
        enemyShips.removeAll { it.y > this.screenHeight + 50f }

        Timber.d("After cleanup: ${powerUps.size} power-ups, ${asteroids.size} asteroids, ${projectiles.size} projectiles, ${enemyShips.size} enemy ships, ${enemyProjectiles.size} enemy projectiles, HP: $hp, Fuel: $fuel")

        if (fuel <= 0 || hp <= 0) {
            highscoreManager.addScore(userId, playerName, currentScore, level, distanceTraveled)
            if (continuesUsed < maxContinues) {
                gameState = GameState.GAME_OVER
                onGameOverListener?.invoke(true, {
                    continuesUsed++
                    hp = maxHp
                    fuel = fuelCapacity
                    gameState = GameState.FLIGHT
                    Timber.d("Player continued after ad, continues used: $continuesUsed")
                }, {
                    gameState = GameState.BUILD
                    powerUps.clear()
                    asteroids.clear()
                    projectiles.clear()
                    enemyShips.clear()
                    enemyProjectiles.clear()
                    resetPowerUpEffects()
                    // Update longestDistanceTraveled, highestScore, and highestLevel before resetting
                    if (distanceTraveled > longestDistanceTraveled) {
                        longestDistanceTraveled = distanceTraveled
                    }
                    if (currentScore > highestScore) {
                        highestScore = currentScore
                    }
                    if (level > highestLevel) {
                        highestLevel = level
                    }
                    // Reset distanceTraveled, currentScore, and level
                    distanceTraveled = 0f
                    currentScore = 0
                    level = 1
                    hp = maxHp
                    fuel = 0f
                    Timber.d("Player declined continue, returning to build screen")
                })
            } else {
                gameState = GameState.BUILD
                powerUps.clear()
                asteroids.clear()
                projectiles.clear()
                enemyShips.clear()
                enemyProjectiles.clear()
                resetPowerUpEffects()
                // Update longestDistanceTraveled, highestScore, and highestLevel before resetting
                if (distanceTraveled > longestDistanceTraveled) {
                    longestDistanceTraveled = distanceTraveled
                }
                if (currentScore > highestScore) {
                    highestScore = currentScore
                }
                if (level > highestLevel) {
                    highestLevel = level
                }
                // Reset distanceTraveled, currentScore, and level
                distanceTraveled = 0f
                currentScore = 0
                level = 1
                hp = maxHp
                fuel = 0f
                Timber.d("No more continues available, returning to build screen")
            }
        }
    }

    private fun resetPowerUpEffects() {
        shieldActive = false
        speedBoostActive = false
        stealthActive = false
        invincibilityActive = false
        applyShipColorEffects()
    }

    private fun checkCollision(rect: RectF): Boolean {
        val shipRect = RectF(
            shipX - maxPartHalfWidth, shipY - totalShipHeight / 2,
            shipX + maxPartHalfWidth, shipY + totalShipHeight / 2
        )
        return shipRect.intersect(rect)
    }

    fun launchShip(screenWidth: Float, screenHeight: Float): Boolean {
        if (isShipSpaceworthy(screenHeight)) {
            fuel = 50f
            hp = maxHp
            shipX = screenWidth / 2f
            shipY = screenHeight / 2f
            this.screenWidth = screenWidth
            this.screenHeight = screenHeight

            val sortedParts = parts.sortedBy { it.y }
            totalShipHeight = sortedParts.sumOf { (it.bitmap.height * it.scale).toDouble() }.toFloat()
            maxPartHalfWidth = sortedParts.maxOf { (it.bitmap.width * it.scale) / 2f }
            val maxWidth = sortedParts.maxOf { (it.bitmap.width * it.scale).toInt() }
            mergedShipBitmap?.recycle()
            mergedShipBitmap = Bitmap.createBitmap(maxWidth, totalShipHeight.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mergedShipBitmap!!)
            var currentY = 0f
            sortedParts.forEach { part ->
                val xOffset = (maxWidth - part.bitmap.width * part.scale) / 2f
                canvas.save()
                canvas.rotate(part.rotation, xOffset + (part.bitmap.width * part.scale) / 2f, currentY + (part.bitmap.height * part.scale) / 2f)
                canvas.drawBitmap(part.bitmap, xOffset, currentY, null)
                canvas.restore()
                currentY += part.bitmap.height * part.scale
            }

            parts.clear()
            Timber.d("Ship launched: totalShipHeight=$totalShipHeight, maxPartHalfWidth=$maxPartHalfWidth")
            return true
        } else {
            val reason = getSpaceworthinessFailureReason()
            Timber.w("Launch failed: $reason")
            return false
        }
    }

    fun spawnProjectile() {
        val projectileX = shipX
        val projectileY = shipY - (mergedShipBitmap?.height ?: 0) / 2f
        projectiles.add(Projectile(projectileX, projectileY, -PROJECTILE_SPEED, screenHeight))
    }

    fun rotatePart(partType: String) {
        parts.find { it.type == partType }?.let {
            it.rotation = (it.rotation + 90f) % 360f
            Timber.d("Rotated $partType to ${it.rotation} degrees")
        } ?: Timber.w("Part $partType not found")
    }

    private fun applyShipColorEffects() {
        currentFuelConsumption = baseFuelConsumption
        currentSpeed = baseSpeed
        when (shipColor) {
            "red" -> currentSpeed = baseSpeed * 1.5f
            "blue" -> {
                shieldActive = true
                shieldEndTime = Long.MAX_VALUE
            }
            "green" -> currentFuelConsumption = baseFuelConsumption * 0.5f
        }
        Timber.d("Applied ship color effects: speed=$currentSpeed, fuelConsumption=$currentFuelConsumption, shield=$shieldActive")
    }

    fun moveShip(direction: Int) {
        when (direction) {
            1 -> shipY -= currentSpeed
            2 -> shipX += currentSpeed
            3 -> shipY += currentSpeed
            4 -> shipX -= currentSpeed
        }
        Timber.d("Moved ship with speed $currentSpeed to (x=$shipX, y=$shipY)")
    }

    fun stopShip() {
        Timber.d("stopShip called (no velocity to stop with dragging)")
    }

    fun isShipSpaceworthy(screenHeight: Float): Boolean {
        if (parts.size != 3) {
            Timber.d("isShipSpaceworthy failed: parts.size=${parts.size}, expected 3")
            return false
        }
        val sortedParts = parts.sortedBy { it.y }
        val cockpit = sortedParts[0]
        val fuelTank = sortedParts[1]
        val engine = sortedParts[2]
        val isValidOrder = cockpit.type == "cockpit" && fuelTank.type == "fuel_tank" && engine.type == "engine"
        val isAlignedY = abs(cockpit.y - cockpitY) <= ALIGNMENT_THRESHOLD &&
                abs(fuelTank.y - fuelTankY) <= ALIGNMENT_THRESHOLD &&
                abs(engine.y - engineY) <= ALIGNMENT_THRESHOLD
        val maxAllowedY = screenHeight - PANEL_HEIGHT - statusBarHeight
        val isWithinBuildArea = sortedParts.all { it.y < maxAllowedY }

        Timber.d("isShipSpaceworthy: isValidOrder=$isValidOrder, isAlignedY=$isAlignedY, isWithinBuildArea=$isWithinBuildArea")
        return isValidOrder && isAlignedY && isWithinBuildArea
    }

    fun getSpaceworthinessFailureReason(): String {
        val sortedParts = parts.sortedBy { it.y }
        return when {
            parts.size != 3 -> "Need exactly 3 parts! Current parts: ${parts.size}"
            sortedParts[0].type != "cockpit" -> "Cockpit must be topmost! Top part: ${sortedParts[0].type}"
            sortedParts[1].type != "fuel_tank" -> "Fuel tank must be middle! Middle part: ${sortedParts[1].type}"
            sortedParts[2].type != "engine" -> "Engine must be bottom! Bottom part: ${sortedParts[2].type}"
            abs(sortedParts[0].y - cockpitY) > ALIGNMENT_THRESHOLD -> "Misaligned: Cockpit not at placeholder position!"
            abs(sortedParts[1].y - fuelTankY) > ALIGNMENT_THRESHOLD -> "Misaligned: Fuel Tank not at placeholder position!"
            abs(sortedParts[2].y - engineY) > ALIGNMENT_THRESHOLD -> "Misaligned: Engine not at placeholder position!"
            sortedParts.any { it.y >= screenHeight - PANEL_HEIGHT - statusBarHeight } -> "Parts must be above the panel!"
            else -> "Unknown failure!"
        }
    }

    fun isShipInCorrectOrder(): Boolean {
        if (parts.size != 3) return false
        val sortedParts = parts.sortedBy { it.y }
        return sortedParts[0].type == "cockpit" &&
                sortedParts[1].type == "fuel_tank" &&
                sortedParts[2].type == "engine"
    }

    private fun savePersistentData(userId: String) {
        val userData = hashMapOf(
            "level" to level,
            "shipColor" to shipColor,
            "selectedShipSet" to selectedShipSet,
            "distanceTraveled" to distanceTraveled,
            "longestDistanceTraveled" to longestDistanceTraveled,
            "highestScore" to highestScore,
            "highestLevel" to highestLevel,
            "starsCollected" to starsCollected
        )
        db.collection("users").document(userId).set(userData)
            .addOnSuccessListener {
                Timber.d("Saved user data for $userId: level=$level, shipColor=$shipColor, selectedShipSet=$selectedShipSet, distanceTraveled=$distanceTraveled, longestDistanceTraveled=$longestDistanceTraveled, highestScore=$highestScore, highestLevel=$highestLevel, starsCollected=$starsCollected")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to save user data for $userId")
            }
    }

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        onLaunchListener = listener
    }

    fun setGameOverListener(listener: (Boolean, () -> Unit, () -> Unit) -> Unit) {
        onGameOverListener = listener
    }

    fun notifyLaunchListener() {
        onLaunchListener?.invoke(gameState == GameState.FLIGHT)
        Timber.d("Notified launch listener, isLaunching=${gameState == GameState.FLIGHT}")
    }

    private fun spawnPowerUp(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        val types = listOf("power_up", "shield", "speed", "stealth", "warp", "star", "invincibility")
        val type = if (Random.nextFloat() < 0.4f) "power_up" else types.random()
        powerUps.add(PowerUp(x, y, type))
    }

    private fun spawnAsteroid(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = -screenHeight * 0.1f
        if (Random.nextFloat() < GIANT_ASTEROID_PROBABILITY) {
            asteroids.add(GiantAsteroid(x, y, 30f * GIANT_ASTEROID_SCALE))
        } else {
            asteroids.add(Asteroid(x, y, 30f))
        }
        Timber.d("Spawned asteroid at (x=$x, y=$y) with size=${if (Random.nextFloat() < GIANT_ASTEROID_PROBABILITY) 60f else 30f}")
    }

    private fun spawnEnemyShip(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        val speedY = (2f + level * 0.5f) * 1.5f
        enemyShips.add(EnemyShip(x, y, speedY, shotInterval = 500L))
    }

    private fun playPowerUpSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.power_up_sound)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play power-up sound")
        }
    }

    private fun playCollisionSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.asteroid_hit)
            if (mediaPlayer != null) {
                mediaPlayer?.start()
                mediaPlayer?.setOnCompletionListener { it.release() }
                Timber.d("Playing asteroid_hit sound")
            } else {
                Timber.e("Failed to create MediaPlayer for asteroid_hit")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play collision sound")
        }
    }

    fun onDestroy() {
        renderer.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        mergedShipBitmap?.recycle()
        mergedShipBitmap = null
        if (userId != null) {
            savePersistentData(userId!!)
        }
    }

    data class Part(val type: String, val bitmap: Bitmap, var x: Float, var y: Float, var rotation: Float, var scale: Float = 1f)

    data class PowerUp(var x: Float, var y: Float, val type: String) {
        fun update(screenHeight: Float) {
            y += 5f
        }

        fun isExpired(screenHeight: Float): Boolean {
            val expired = y > screenHeight
            if (expired) Timber.d("Power-up expired at y=$y, screenHeight=$screenHeight")
            return expired
        }
    }

    data class Projectile(var x: Float, var y: Float, var speedY: Float, val screenHeight: Float) {
        fun update() {
            y += speedY
        }

        fun isOffScreen(): Boolean {
            val offScreen = y < -PROJECTILE_SIZE || y > screenHeight + PROJECTILE_SIZE
            if (offScreen) Timber.d("Projectile off-screen at y=$y")
            return offScreen
        }
    }

    data class EnemyShip(var x: Float, var y: Float, var speedY: Float, var lastShotTime: Long = 0L, val shotInterval: Long)

    open class Asteroid(var x: Float, var y: Float, var size: Float, var rotation: Float = 0f, var angularVelocity: Float = Random.nextFloat() * 0.1f) {
        open fun update(screenWidth: Float, screenHeight: Float, level: Int) {
            val radius = screenWidth / 8f
            val angle = (System.currentTimeMillis() % 10000) / 10000f * 2 * Math.PI.toFloat()
            val speed = 15f + level * 0.3f
            x += cos(angle) * speed
            y += sin(angle) * speed + speed
            rotation += angularVelocity
            if (y > screenHeight + size) isOffScreen(screenHeight)
        }

        open fun isOffScreen(screenHeight: Float): Boolean {
            val offScreen = y > screenHeight + size
            if (offScreen) Timber.d("Asteroid off-screen at y=$y, screenHeight=$screenHeight")
            return offScreen
        }
    }

    class GiantAsteroid(x: Float, y: Float, size: Float) : Asteroid(x, y, size * GIANT_ASTEROID_SCALE, 0f, 0.15f) {
        fun explode(gameEngine: GameEngine) {
            Timber.d("Giant asteroid exploded at (x=$x, y=$y)")
            gameEngine.renderer.particleSystem.addExplosionParticles(x, y)
            repeat(SMALL_ASTEROID_COUNT) {
                gameEngine.asteroids.add(Asteroid(x, y, size / 2f, Random.nextFloat() * 360f, Random.nextFloat() * 0.2f))
            }
            gameEngine.asteroids.remove(this)
        }
    }
}