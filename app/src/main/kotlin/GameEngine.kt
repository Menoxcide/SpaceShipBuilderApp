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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
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
                sessionDistanceTraveled = 0f
                levelUpAnimationStartTime = 0L
                shipX = screenWidth / 2f
                shipY = screenHeight / 2f
                applyShipColorEffects()
                applyShipSetCharacteristics()
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
                if (distanceTraveled > longestDistanceTraveled) {
                    longestDistanceTraveled = distanceTraveled
                }
                if (currentScore > highestScore) {
                    highestScore = currentScore
                }
                if (level > highestLevel) {
                    highestLevel = level
                }
                distanceTraveled = 0f
                currentScore = 0
                level = 1
                projectiles.clear()
                enemyShips.clear()
                enemyProjectiles.clear()
                homingProjectiles.clear()
                boss = null
                bossDefeated = false
                glowStartTime = 0L
                continuesUsed = 0
                missileCount = maxMissiles
                lastMissileRechargeTime = System.currentTimeMillis()
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
        get() = when (selectedShipSet) {
            0 -> 100f // Base HP for Ship Set 1
            1 -> 150f // +50 HP for Ship Set 2
            2 -> 200f // +50 HP for Ship Set 3
            else -> 100f
        }
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
    var onGameOverListener: ((Boolean, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit)? = null
    var currentScore = 0
    var highestScore = 0
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    var highestLevel = 1
        set(value) {
            field = value
            updateUnlockedShipSets()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    var starsCollected = 0
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

    var selectedShipSet: Int = 0
        set(value) {
            if (value in unlockedShipSets) {
                field = value
                applyShipSetCharacteristics()
                if (userId != null) {
                    savePersistentData(userId!!)
                }
                Timber.d("Selected ship set: $value")
            } else {
                Timber.w("Cannot select ship set $value, not unlocked yet")
            }
        }
    private val unlockedShipSets = mutableSetOf(0) // Default ship set is always unlocked

    var reviveCount: Int = 0
        set(value) {
            field = value.coerceIn(0, 3) // Cap at 3
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var destroyAllCharges: Int = 0
        set(value) {
            field = value.coerceIn(0, 3) // Cap at 3
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    val isDestroyAllUnlocked: Boolean
        get() = destroyAllCharges > 0 // Unlocked if the player has at least one charge

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
    val homingProjectiles = mutableListOf<HomingProjectile>()
    private var boss: BossShip? = null
    private var bossDefeated = false
    private val powerUpSpawnRateBase = 2000L
    private val asteroidSpawnRateBase = 1500L
    private val enemySpawnRateBase = 5000L
    private var lastPowerUpSpawnTime = System.currentTimeMillis()
    private var lastAsteroidSpawnTime = System.currentTimeMillis()
    private var lastEnemySpawnTime = System.currentTimeMillis()
    private var lastScoreUpdateTime = System.currentTimeMillis()
    private var lastDistanceUpdateTime = System.currentTimeMillis()

    var glowStartTime: Long = 0L
    val glowDuration = 1000L

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
    private var baseProjectileSpeed = 10f
    private var currentProjectileSpeed = baseProjectileSpeed

    var distanceTraveled = 0f
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    var longestDistanceTraveled = 0f
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }
    private var sessionDistanceTraveled = 0f
    private val distancePerLevel = 100f
    var levelUpAnimationStartTime = 0L
    private var userId: String? = null

    var missileCount = 3
        private set
    var maxMissiles = 3
        get() = when (selectedShipSet) {
            0 -> 3 // Base for Ship Set 1
            1 -> 4 // +1 for Ship Set 2
            2 -> 5 // +1 for Ship Set 3
            else -> 3
        }
    private val missileRechargeTime = 10000L // 10 seconds per missile recharge
    private var lastMissileRechargeTime = System.currentTimeMillis()

    companion object {
        const val ALIGNMENT_THRESHOLD = 75f
        const val PANEL_HEIGHT = 150f
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

    fun getBoss(): BossShip? = boss

    private fun applyShipSetCharacteristics() {
        // Reset to base values
        baseSpeed = 5f
        currentSpeed = baseSpeed
        baseProjectileSpeed = 10f
        currentProjectileSpeed = baseProjectileSpeed
        missileCount = maxMissiles
        hp = maxHp
        fuel = fuelCapacity

        // Apply upgrades based on ship set
        when (selectedShipSet) {
            1 -> {
                // Ship Set 2: 10% faster speed, 10% faster bullets, +1 missile, +50 HP
                baseSpeed *= 1.1f
                currentSpeed = baseSpeed
                baseProjectileSpeed *= 1.1f
                currentProjectileSpeed = baseProjectileSpeed
            }
            2 -> {
                // Ship Set 3: 20% faster speed, 20% faster bullets, +2 missiles, +100 HP
                baseSpeed *= 1.2f
                currentSpeed = baseSpeed
                baseProjectileSpeed *= 1.2f
                currentProjectileSpeed = baseProjectileSpeed
            }
        }
        Timber.d("Applied ship set $selectedShipSet characteristics: speed=$currentSpeed, projectileSpeed=$currentProjectileSpeed, maxMissiles=$maxMissiles, maxHp=$maxHp")
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
                reviveCount = doc.getLong("reviveCount")?.toInt() ?: 0
                destroyAllCharges = doc.getLong("destroyAllCharges")?.toInt() ?: 0
                updateUnlockedShipSets()
                applyShipSetCharacteristics()
                Timber.d("Loaded user data for $userId: level=$level, shipColor=$shipColor, selectedShipSet=$selectedShipSet, distanceTraveled=$distanceTraveled, longestDistanceTraveled=$longestDistanceTraveled, highestScore=$highestScore, highestLevel=$highestLevel, starsCollected=$starsCollected, reviveCount=$reviveCount, destroyAllCharges=$destroyAllCharges")
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
                    "starsCollected" to 0,
                    "reviveCount" to 0,
                    "destroyAllCharges" to 0
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
                reviveCount = 0
                destroyAllCharges = 0
                updateUnlockedShipSets()
                applyShipSetCharacteristics()
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
            reviveCount = 0
            destroyAllCharges = 0
            updateUnlockedShipSets()
            applyShipSetCharacteristics()
            Timber.w("Using default user data due to Firestore error")
        }
    }

    private fun updateUnlockedShipSets() {
        unlockedShipSets.clear()
        unlockedShipSets.add(0)
        if (highestLevel >= 20 && starsCollected >= 20) {
            unlockedShipSets.add(1)
        }
        if (highestLevel >= 40 && starsCollected >= 40) {
            unlockedShipSets.add(2)
        }
        if (selectedShipSet !in unlockedShipSets) {
            selectedShipSet = 0
        }
        Timber.d("Updated unlocked ship sets: $unlockedShipSets")
    }

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

    fun getUnlockedShipSets(): Set<Int> = unlockedShipSets.toSet()

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
            Timber.d("Shield effect ended")
        }
        if (speedBoostActive && currentTime > speedBoostEndTime) {
            speedBoostActive = false
            currentSpeed = baseSpeed
            Timber.d("Speed boost ended")
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

        if (missileCount < maxMissiles && currentTime - lastMissileRechargeTime >= missileRechargeTime) {
            missileCount++
            lastMissileRechargeTime = currentTime
            Timber.d("Missile recharged. Current count: $missileCount")
        }

        if (level % 10 != 0) {
            bossDefeated = false
        }

        if (level % 10 == 0 && level >= 10 && boss == null && !bossDefeated) {
            val tier = level / 10
            val hpMultiplier = Math.pow(1.1, (tier - 1).toDouble()).toFloat()
            val intervalMultiplier = Math.pow(0.9, (tier - 1).toDouble()).toFloat()
            val bossHp = maxHp * hpMultiplier
            boss = BossShip(
                x = screenWidth / 2f,
                y = screenHeight * 0.1f,
                hp = bossHp,
                maxHp = bossHp,
                shotInterval = (BOSS_SHOT_INTERVAL * intervalMultiplier).toLong(),
                movementInterval = (BOSS_MOVEMENT_INTERVAL * intervalMultiplier).toLong(),
                tier = tier
            )
            Timber.d("Boss spawned at level $level (tier $tier) with HP=$bossHp, shotInterval=${boss!!.shotInterval}, movementInterval=${boss!!.movementInterval}")
        }

        boss?.update(this, currentTime)

        if (boss != null) {
            val projectilesToRemove = mutableListOf<Projectile>()
            val homingProjectilesToRemove = mutableListOf<HomingProjectile>()
            for (projectile in projectiles) {
                if (boss == null) break
                val projectileRect = RectF(
                    projectile.x - PROJECTILE_SIZE,
                    projectile.y - PROJECTILE_SIZE,
                    projectile.x + PROJECTILE_SIZE,
                    projectile.y + PROJECTILE_SIZE
                )
                boss?.let { b ->
                    val bossRect = RectF(b.x - 75f, b.y - 75f, b.x + 75f, b.y + 75f)
                    if (projectileRect.intersect(bossRect)) {
                        b.hp -= 10f
                        projectilesToRemove.add(projectile)
                        renderer.particleSystem.addExplosionParticles(projectile.x, projectile.y)
                        Timber.d("Boss hit by regular projectile, HP decreased to ${b.hp}")
                        if (b.hp <= 0) {
                            currentScore += 500
                            renderer.particleSystem.addExplosionParticles(b.x, b.y)
                            renderer.particleSystem.addScoreTextParticle(b.x, b.y, "+500")
                            // Drop stars and fuel power-ups when boss is defeated
                            spawnPowerUp(b.x, b.y, "star")
                            spawnPowerUp(b.x + 20f, b.y + 20f, "power_up")
                            Timber.d("Boss defeated! Score increased by 500 to $currentScore, dropped star and fuel power-up at (x=${b.x}, y=${b.y})")
                            level++
                            levelUpAnimationStartTime = currentTime
                            if (level > highestLevel) {
                                highestLevel = level
                                val previousUnlocked = unlockedShipSets.toList()
                                updateUnlockedShipSets()
                                val newUnlocked = unlockedShipSets.filter { it !in previousUnlocked }
                                if (newUnlocked.isNotEmpty()) {
                                    renderer.showUnlockMessage(newUnlocked)
                                }
                            }
                            boss = null
                            bossDefeated = true
                        }
                    }
                }
            }
            boss?.let { b ->
                val bossRect = RectF(b.x - 75f, b.y - 75f, b.x + 75f, b.y + 75f)
                for (projectile in homingProjectiles) {
                    if (projectile.target == b && projectile.checkCollision(bossRect)) {
                        b.hp -= 20f
                        homingProjectilesToRemove.add(projectile)
                        renderer.particleSystem.addExplosionParticles(projectile.x, projectile.y)
                        Timber.d("Boss hit by homing missile, HP decreased to ${b.hp}")
                        if (b.hp <= 0) {
                            currentScore += 500
                            renderer.particleSystem.addExplosionParticles(b.x, b.y)
                            renderer.particleSystem.addScoreTextParticle(b.x, b.y, "+500")
                            // Drop stars and fuel power-ups when boss is defeated
                            spawnPowerUp(b.x, b.y, "star")
                            spawnPowerUp(b.x + 20f, b.y + 20f, "power_up")
                            Timber.d("Boss defeated! Score increased by 500 to $currentScore, dropped star and fuel power-up at (x=${b.x}, y=${b.y})")
                            level++
                            levelUpAnimationStartTime = currentTime
                            if (level > highestLevel) {
                                highestLevel = level
                                val previousUnlocked = unlockedShipSets.toList()
                                updateUnlockedShipSets()
                                val newUnlocked = unlockedShipSets.filter { it !in previousUnlocked }
                                if (newUnlocked.isNotEmpty()) {
                                    renderer.showUnlockMessage(newUnlocked)
                                }
                            }
                            boss = null
                            bossDefeated = true
                        }
                    }
                }
            }
            projectiles.removeAll(projectilesToRemove)
            homingProjectiles.removeAll(homingProjectilesToRemove)

            boss?.let { b ->
                val bossRect = RectF(b.x - 75f, b.y - 75f, b.x + 75f, b.y + 75f)
                if (checkCollision(bossRect) && !stealthActive && !invincibilityActive) {
                    hp -= 50f
                    renderer.particleSystem.addCollisionParticles(shipX, shipY)
                    renderer.particleSystem.addDamageTextParticle(shipX, shipY, 50)
                    playCollisionSound()
                    glowStartTime = currentTime
                    Timber.d("Collided with boss, HP decreased to $hp")
                }
            }
        } else {
            if (currentTime - lastDistanceUpdateTime >= 1000) {
                sessionDistanceTraveled += currentSpeed
                distanceTraveled += currentSpeed
                lastDistanceUpdateTime = currentTime
                Timber.d("Session distance traveled: $sessionDistanceTraveled, Total distance traveled: $distanceTraveled")
                val totalDistanceForLevel = distanceTraveled
                if (totalDistanceForLevel >= distancePerLevel * level) {
                    level++
                    levelUpAnimationStartTime = currentTime
                    if (level > highestLevel) {
                        highestLevel = level
                        val previousUnlocked = unlockedShipSets.toList()
                        updateUnlockedShipSets()
                        val newUnlocked = unlockedShipSets.filter { it !in previousUnlocked }
                        if (newUnlocked.isNotEmpty()) {
                            renderer.showUnlockMessage(newUnlocked)
                        }
                    }
                    Timber.d("Level advanced to $level based on total distance traveled: $totalDistanceForLevel")
                }
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
        }

        if (currentTime - lastScoreUpdateTime >= 1000) {
            currentScore += 10
            lastScoreUpdateTime = currentTime
        }

        powerUps.forEach { it.update(this.screenHeight) }
        projectiles.forEach { it.update() }
        enemyShips.forEach { enemy ->
            enemy.y += enemy.speedY
            if (currentTime - enemy.lastShotTime >= enemy.shotInterval) {
                val dx = shipX - enemy.x
                val dy = shipY - enemy.y
                val angle = atan2(dy, dx)
                val speedX = cos(angle) * currentProjectileSpeed
                val speedY = sin(angle) * currentProjectileSpeed
                enemyProjectiles.add(Projectile(enemy.x, enemy.y, speedX, speedY, this.screenHeight, this.screenWidth))
                enemy.lastShotTime = currentTime
                Timber.d("Enemy shot projectile towards player")
            }
        }
        enemyProjectiles.forEach { it.update() }

        val asteroidsCopy = asteroids.toMutableList()
        asteroidsCopy.forEach { it.update(this.screenWidth, this.screenHeight, level) }

        val homingProjectilesToRemove = mutableListOf<HomingProjectile>()
        homingProjectiles.forEach { projectile ->
            projectile.update(this, currentTime)
            if (projectile.isOffScreen() || projectile.hasHitTarget() || !projectile.isTargetValid(this)) {
                homingProjectilesToRemove.add(projectile)
                if (projectile.hasHitTarget()) {
                    renderer.particleSystem.addExplosionParticles(projectile.x, projectile.y)
                    Timber.d("Homing missile hit target at (x=${projectile.x}, y=${projectile.y})")
                } else if (!projectile.isTargetValid(this)) {
                    Timber.d("Homing missile target destroyed, removing missile at (x=${projectile.x}, y=${projectile.y})")
                }
            }
        }

        val powerUpsToRemove = mutableListOf<PowerUp>()
        val asteroidsToRemove = mutableListOf<Asteroid>()
        val projectilesToRemove = mutableListOf<Projectile>()
        val enemyShipsToRemove = mutableListOf<EnemyShip>()
        val enemyProjectilesToRemove = mutableListOf<Projectile>()

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
                    Timber.d("Projectile hit asteroid, score increased by $ASTEROID_DESTROY_POINTS to $currentScore")
                }
            }
        }

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
                    // 25% chance to drop a star or fuel power-up
                    if (Random.nextFloat() < 0.25f) {
                        val powerUpType = if (Random.nextBoolean()) "star" else "power_up"
                        spawnPowerUp(enemy.x, enemy.y, powerUpType)
                        Timber.d("Enemy ship dropped $powerUpType at (x=${enemy.x}, y=${enemy.y})")
                    }
                    Timber.d("Projectile hit enemy ship, score increased by 50 to $currentScore")
                }
            }
        }

        for (enemy in enemyShips) {
            val enemyRect = RectF(enemy.x - 50f, enemy.y - 50f, enemy.x + 50f, enemy.y + 50f)
            for (projectile in homingProjectiles) {
                if (projectile.target == enemy && projectile.checkCollision(enemyRect)) {
                    enemyShipsToRemove.add(enemy)
                    homingProjectilesToRemove.add(projectile)
                    currentScore += 50
                    renderer.particleSystem.addExplosionParticles(enemy.x, enemy.y)
                    renderer.particleSystem.addScoreTextParticle(enemy.x, enemy.y, "+50")
                    // 25% chance to drop a star or fuel power-up
                    if (Random.nextFloat() < 0.25f) {
                        val powerUpType = if (Random.nextBoolean()) "star" else "power_up"
                        spawnPowerUp(enemy.x, enemy.y, powerUpType)
                        Timber.d("Enemy ship dropped $powerUpType at (x=${enemy.x}, y=${enemy.y})")
                    }
                    Timber.d("Homing missile hit enemy ship, score increased by 50 to $currentScore")
                }
            }
        }

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
                        Timber.d("Collected power-up: Fuel +20, HP +10")
                    }
                    "shield" -> {
                        shieldActive = true
                        shieldEndTime = currentTime + effectDuration
                        currentFuelConsumption = baseFuelConsumption / 2f
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Shield", powerUp.type)
                        Timber.d("Collected shield power-up")
                    }
                    "speed" -> {
                        speedBoostActive = true
                        speedBoostEndTime = currentTime + effectDuration
                        currentSpeed = baseSpeed * 5f
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Speed Boost", powerUp.type)
                        Timber.d("Collected speed boost power-up")
                    }
                    "stealth" -> {
                        stealthActive = true
                        shieldEndTime = currentTime + effectDuration
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Stealth", powerUp.type)
                        Timber.d("Collected stealth power-up")
                    }
                    "warp" -> {
                        shipX = Random.nextFloat() * (this.screenWidth - 2 * maxPartHalfWidth) + maxPartHalfWidth
                        shipY = Random.nextFloat() * (this.screenHeight - totalShipHeight) + totalShipHeight / 2
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Warp", powerUp.type)
                        Timber.d("Collected warp power-up")
                    }
                    "star" -> {
                        currentScore += 50
                        starsCollected += 1
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Star +50", powerUp.type)
                        Timber.d("Collected star power-up, starsCollected=$starsCollected")
                    }
                    "invincibility" -> {
                        invincibilityActive = true
                        invincibilityEndTime = currentTime + effectDuration
                        renderer.particleSystem.addPowerUpTextParticle(powerUp.x, powerUp.y, "Invincibility", powerUp.type)
                        Timber.d("Collected invincibility power-up")
                    }
                }
                renderer.particleSystem.addPowerUpSpriteParticles(shipX, shipY, powerUp.type)
                powerUpsToRemove.add(powerUp)
                Timber.d("Collected ${powerUp.type} power-up")
                onPowerUpCollectedListener?.invoke(powerUp.x, powerUp.y)
                playPowerUpSound()
            }
        }

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
                Timber.d("Hit asteroid, HP decreased to $hp")
            }
            if (asteroid is GiantAsteroid && Random.nextFloat() < EXPLOSION_CHANCE) {
                asteroid.explode(this)
            }
        }

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
                Timber.d("Hit by enemy projectile, HP decreased to $hp")
            }
        }

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
                // 25% chance to drop a star or fuel power-up
                if (Random.nextFloat() < 0.25f) {
                    val powerUpType = if (Random.nextBoolean()) "star" else "power_up"
                    spawnPowerUp(enemy.x, enemy.y, powerUpType)
                    Timber.d("Enemy ship dropped $powerUpType at (x=${enemy.x}, y=${enemy.y})")
                }
                Timber.d("Collided with enemy ship, HP decreased to $hp")
            }
        }

        powerUps.removeAll(powerUpsToRemove)
        asteroids.removeAll(asteroidsToRemove)
        projectiles.removeAll(projectilesToRemove)
        enemyShips.removeAll(enemyShipsToRemove)
        enemyProjectiles.removeAll(enemyProjectilesToRemove)
        homingProjectiles.removeAll(homingProjectilesToRemove)

        powerUps.removeAll { it.isExpired(this.screenHeight) }
        asteroids.removeAll { it.isOffScreen(this.screenHeight, this.screenWidth) }
        projectiles.removeAll { it.isOffScreen() }
        enemyProjectiles.removeAll { it.isOffScreen() }
        enemyShips.removeAll { it.y > this.screenHeight + 50f }

        Timber.d("After cleanup: ${powerUps.size} power-ups, ${asteroids.size} asteroids, ${projectiles.size} projectiles, ${enemyShips.size} enemy ships, ${enemyProjectiles.size} enemy projectiles, ${homingProjectiles.size} homing projectiles, HP: $hp, Fuel: $fuel")

        if (fuel <= 0 || hp <= 0) {
            highscoreManager.addScore(userId, playerName, currentScore, level, distanceTraveled)
            if (continuesUsed < maxContinues) {
                gameState = GameState.GAME_OVER
                onGameOverListener?.invoke(
                    true,
                    reviveCount > 0,
                    {
                        continuesUsed++
                        hp = maxHp
                        fuel = fuelCapacity
                        gameState = GameState.FLIGHT
                        Timber.d("Player continued after ad, continues used: $continuesUsed")
                    },
                    {
                        if (reviveCount > 0) {
                            reviveCount--
                            continuesUsed++
                            hp = maxHp
                            fuel = fuelCapacity
                            gameState = GameState.FLIGHT
                            Timber.d("Player used a revive, revives remaining: $reviveCount, continues used: $continuesUsed")
                        }
                    },
                    {
                        gameState = GameState.BUILD
                        powerUps.clear()
                        asteroids.clear()
                        projectiles.clear()
                        enemyShips.clear()
                        enemyProjectiles.clear()
                        homingProjectiles.clear()
                        boss = null
                        bossDefeated = false
                        resetPowerUpEffects()
                        if (distanceTraveled > longestDistanceTraveled) {
                            longestDistanceTraveled = distanceTraveled
                        }
                        if (currentScore > highestScore) {
                            highestScore = currentScore
                        }
                        if (level > highestLevel) {
                            highestLevel = level
                        }
                        distanceTraveled = 0f
                        currentScore = 0
                        level = 1
                        hp = maxHp
                        fuel = 0f
                        missileCount = maxMissiles
                        lastMissileRechargeTime = System.currentTimeMillis()
                        Timber.d("Player declined continue, returning to build screen")
                    }
                )
            } else {
                gameState = GameState.BUILD
                powerUps.clear()
                asteroids.clear()
                projectiles.clear()
                enemyShips.clear()
                enemyProjectiles.clear()
                homingProjectiles.clear()
                boss = null
                bossDefeated = false
                resetPowerUpEffects()
                if (distanceTraveled > longestDistanceTraveled) {
                    longestDistanceTraveled = distanceTraveled
                }
                if (currentScore > highestScore) {
                    highestScore = currentScore
                }
                if (level > highestLevel) {
                    highestLevel = level
                }
                distanceTraveled = 0f
                currentScore = 0
                level = 1
                hp = maxHp
                fuel = 0f
                missileCount = maxMissiles
                lastMissileRechargeTime = System.currentTimeMillis()
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

    fun checkCollision(rect: RectF): Boolean {
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
        projectiles.add(Projectile(projectileX, projectileY, 0f, -currentProjectileSpeed, screenHeight, screenWidth))
    }

    fun launchHomingMissile(target: Any) {
        if (missileCount > 0 && gameState == GameState.FLIGHT) {
            val projectileX = shipX
            val projectileY = shipY - (mergedShipBitmap?.height ?: 0) / 2f
            homingProjectiles.add(HomingProjectile(projectileX, projectileY, target, screenHeight, screenWidth))
            missileCount--
            playMissileLaunchSound()
            Timber.d("Launched homing missile towards target. Remaining missiles: $missileCount")
        } else {
            Timber.d("Cannot launch missile: count=$missileCount, gameState=$gameState")
        }
    }

    fun destroyAll(): Boolean {
        if (destroyAllCharges <= 0) {
            Timber.d("No Destroy All charges available")
            return false
        }
        destroyAllCharges--
        asteroids.forEach { asteroid ->
            renderer.particleSystem.addExplosionParticles(asteroid.x, asteroid.y)
            currentScore += ASTEROID_DESTROY_POINTS
            renderer.particleSystem.addScoreTextParticle(asteroid.x, asteroid.y, "+$ASTEROID_DESTROY_POINTS")
        }
        enemyShips.forEach { enemy ->
            renderer.particleSystem.addExplosionParticles(enemy.x, enemy.y)
            currentScore += 50
            renderer.particleSystem.addScoreTextParticle(enemy.x, enemy.y, "+50")
        }
        asteroids.clear()
        enemyShips.clear()
        enemyProjectiles.clear()
        Timber.d("Destroyed all asteroids and enemy ships. New score: $currentScore, remaining charges: $destroyAllCharges")
        return true
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
        if (parts.size != 3) return false
        val sortedParts = parts.sortedBy { it.y }
        val isValidOrder = sortedParts[0].type == "cockpit" && sortedParts[1].type == "fuel_tank" && sortedParts[2].type == "engine"
        val isAlignedY = abs(sortedParts[0].y - cockpitY) <= ALIGNMENT_THRESHOLD &&
                abs(sortedParts[1].y - fuelTankY) <= ALIGNMENT_THRESHOLD &&
                abs(sortedParts[2].y - engineY) <= ALIGNMENT_THRESHOLD
        val maxAllowedY = screenHeight - PANEL_HEIGHT - statusBarHeight
        val isWithinBuildArea = sortedParts.all { it.y < maxAllowedY }
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
            "starsCollected" to starsCollected,
            "reviveCount" to reviveCount,
            "destroyAllCharges" to destroyAllCharges
        )
        db.collection("users").document(userId).set(userData)
            .addOnSuccessListener { Timber.d("Saved user data for $userId") }
            .addOnFailureListener { e -> Timber.e(e, "Failed to save user data for $userId") }
    }

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        onLaunchListener = listener
    }

    fun setGameOverListener(listener: (Boolean, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit) {
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

    private fun spawnPowerUp(x: Float, y: Float, type: String) {
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
    }

    private fun spawnEnemyShip(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        val speedY = 5f + level * 0.1f
        enemyShips.add(EnemyShip(x, y, speedY, shotInterval = 4000L))
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
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play collision sound")
        }
    }

    private fun playBossShootSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.boss_shoot)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play boss shoot sound")
        }
    }

    private fun playMissileLaunchSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.missile_launch)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play missile launch sound")
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

        fun isExpired(screenHeight: Float): Boolean = y > screenHeight
    }

    data class Projectile(var x: Float, var y: Float, var speedX: Float, var speedY: Float, val screenHeight: Float, val screenWidth: Float) {
        fun update() {
            x += speedX
            y += speedY
        }

        fun isOffScreen(): Boolean = y < -PROJECTILE_SIZE || y > screenHeight + PROJECTILE_SIZE || x < -PROJECTILE_SIZE || x > screenWidth + PROJECTILE_SIZE
    }

    data class EnemyShip(var x: Float, var y: Float, var speedY: Float, var lastShotTime: Long = 0L, val shotInterval: Long)

    data class BossShip(
        var x: Float,
        var y: Float,
        var hp: Float,
        val maxHp: Float,
        val shotInterval: Long,
        val movementInterval: Long,
        var speedX: Float = 0f,
        var speedY: Float = 0f,
        var lastShotTime: Long = 0L,
        var lastMovementChange: Long = 0L,
        val tier: Int
    ) {
        fun update(gameEngine: GameEngine, currentTime: Long) {
            x += speedX
            y += speedY
            x = x.coerceIn(75f, gameEngine.screenWidth - 75f)
            y = y.coerceIn(gameEngine.screenHeight * 0.05f, gameEngine.screenHeight * 0.3f)

            if (currentTime - lastMovementChange >= movementInterval) {
                val direction = Random.nextInt(4)
                speedX = 0f
                speedY = 0f
                val speed = 3f
                when (direction) {
                    0 -> speedY = speed
                    1 -> speedY = -speed
                    2 -> speedX = -speed
                    3 -> speedX = speed
                }
                lastMovementChange = currentTime
                Timber.d("Boss changed direction: speedX=$speedX, speedY=$speedY")
            }

            if (currentTime - lastShotTime >= shotInterval) {
                gameEngine.enemyProjectiles.add(
                    Projectile(
                        x,
                        y + 75f,
                        0f,
                        gameEngine.currentProjectileSpeed,
                        gameEngine.screenHeight,
                        gameEngine.screenWidth
                    )
                )
                lastShotTime = currentTime
                gameEngine.playBossShootSound()
                Timber.d("Boss shot projectile at (x=$x, y=$y)")
            }
        }
    }

    data class HomingProjectile(
        var x: Float,
        var y: Float,
        val target: Any,
        val screenHeight: Float,
        val screenWidth: Float
    ) {
        private val speed = 15f
        private var hasHit = false

        fun update(gameEngine: GameEngine, currentTime: Long) {
            if (!isTargetValid(gameEngine)) return

            val (targetX, targetY) = when (target) {
                is EnemyShip -> Pair(target.x, target.y)
                is BossShip -> Pair(target.x, target.y)
                else -> return
            }
            val dx = targetX - x
            val dy = targetY - y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance > 0) {
                val speedX = (dx / distance) * speed
                val speedY = (dy / distance) * speed
                x += speedX
                y += speedY
                gameEngine.renderer.particleSystem.addMissileExhaustParticles(x, y + 10f)
            }
        }

        fun isOffScreen(): Boolean = y < -PROJECTILE_SIZE || y > screenHeight + PROJECTILE_SIZE || x < -PROJECTILE_SIZE || x > screenWidth + PROJECTILE_SIZE

        fun hasHitTarget(): Boolean = hasHit

        fun checkCollision(targetRect: RectF): Boolean {
            val projectileRect = RectF(
                x - PROJECTILE_SIZE,
                y - PROJECTILE_SIZE,
                x + PROJECTILE_SIZE,
                y + PROJECTILE_SIZE
            )
            if (projectileRect.intersect(targetRect)) {
                hasHit = true
                return true
            }
            return false
        }

        fun isTargetValid(gameEngine: GameEngine): Boolean {
            return when (target) {
                is EnemyShip -> gameEngine.enemyShips.contains(target)
                is BossShip -> gameEngine.getBoss() == target
                else -> false
            }
        }
    }

    open class Asteroid(var x: Float, var y: Float, var size: Float, var rotation: Float = 0f, var angularVelocity: Float = Random.nextFloat() * 0.1f) {
        open fun update(screenWidth: Float, screenHeight: Float, level: Int) {
            val radius = screenWidth / 8f
            val angle = (System.currentTimeMillis() % 10000) / 10000f * 2 * Math.PI.toFloat()
            val speed = 5f + level * 0.1f
            x += cos(angle) * speed
            y += sin(angle) * speed + speed
            rotation += angularVelocity
        }

        open fun isOffScreen(screenHeight: Float, screenWidth: Float): Boolean = y > screenHeight + size || x < -size || x > screenWidth + size
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

        override fun isOffScreen(screenHeight: Float, screenWidth: Float): Boolean = y > screenHeight + size || x < -size || x > screenWidth + size
    }
}