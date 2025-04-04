package com.example.spaceshipbuilderapp

import android.graphics.RectF
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameObjectManager @Inject constructor(
    val renderer: Renderer,
    val audioManager: AudioManager
) {
    val powerUps = mutableListOf<PowerUp>()
    val asteroids = mutableListOf<Asteroid>()
    val projectiles = mutableListOf<Projectile>()
    val enemyShips = mutableListOf<EnemyShip>()
    val enemyProjectiles = mutableListOf<Projectile>()
    val homingProjectiles = mutableListOf<HomingProjectile>()
    private var boss: BossShip? = null
    private var bossDefeated = false

    private val powerUpSpawnRateBase = 2000L
    private val asteroidSpawnRateBase = 500L
    private val enemySpawnRateBase = 5000L
    private var lastPowerUpSpawnTime = System.currentTimeMillis()
    private var lastAsteroidSpawnTime = System.currentTimeMillis()
    private var lastEnemySpawnTime = System.currentTimeMillis()

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    var currentProjectileSpeed: Float = 10f // Temporary field until we refactor further

    private val bitmapManager: BitmapManager = BitmapManager(audioManager.context)

    companion object {
        const val MAX_ASTEROIDS = 20 // Limit total asteroids on screen
    }

    fun getBoss(): BossShip? = boss

    fun spawnPowerUp(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        val types =
            listOf("power_up", "shield", "speed", "stealth", "warp", "star", "invincibility")
        val type = if (Random.nextFloat() < 0.4f) "power_up" else types.random()
        powerUps.add(PowerUp(x, y, type))
    }

    fun spawnPowerUp(x: Float, y: Float, type: String) {
        powerUps.add(PowerUp(x, y, type))
    }

    fun spawnAsteroid(screenWidth: Float, environment: FlightModeManager.Environment) {
        if (asteroids.size >= MAX_ASTEROIDS) return // Prevent overcrowding

        val x = Random.nextFloat() * screenWidth
        val y = -screenHeight * 0.1f
        val size = Random.nextFloat() * 30f + 20f // Random size between 20 and 50
        val rotation = Random.nextFloat() * 20f // Random initial rotation
        val angularVelocity = Random.nextFloat() * 0.2f - 0.1f // Random spin speed

        val asteroidProbability = if (environment == FlightModeManager.Environment.ASTEROID_FIELD) {
            FlightModeManager.GIANT_ASTEROID_PROBABILITY * 1.5f // More giant asteroids in asteroid fields
        } else {
            FlightModeManager.GIANT_ASTEROID_PROBABILITY
        }

        if (Random.nextFloat() < asteroidProbability) {
            asteroids.add(GiantAsteroid(x, y, size).apply {
                this.rotation = rotation
                this.angularVelocity = angularVelocity
            })
        } else {
            asteroids.add(Asteroid(x, y, size, rotation, angularVelocity))
        }
        Timber.d("Spawned asteroid at ($x, $y) with size=$size, rotation=$rotation, angularVelocity=$angularVelocity")
    }

    fun spawnEnemyShip(screenWidth: Float, level: Int, environment: FlightModeManager.Environment) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        val speedY = (5f + level * 0.05f).coerceAtMost(10f)
        val enemyType = when (Random.nextInt(3)) {
            0 -> EnemyShip(x, y, speedY, shotInterval = 4000L)
            1 -> DroneEnemy(x, y, speedY * 1.5f) // Faster drones
            2 -> ArmoredEnemy(x, y, speedY * 0.5f) // Slower armored enemies
            else -> EnemyShip(x, y, speedY, shotInterval = 4000L) // Fallback
        }
        enemyShips.add(enemyType)
        Timber.d("Spawned enemy of type ${enemyType.javaClass.simpleName} at (x=$x, y=$y) in environment $environment")
    }

    fun spawnBoss(level: Int, maxHp: Float, screenWidth: Float, screenHeight: Float) {
        val tier = level / 10
        val hpMultiplier = Math.pow(1.05, (tier - 1).toDouble()).toFloat()
        val intervalMultiplier = Math.pow(0.95, (tier - 1).toDouble()).toFloat()
        val bossHp = maxHp * hpMultiplier
        boss = BossShip(
            x = screenWidth / 2f,
            y = screenHeight * 0.1f,
            hp = bossHp,
            maxHp = bossHp,
            shotInterval = (FlightModeManager.BOSS_SHOT_INTERVAL * intervalMultiplier).toLong(),
            movementInterval = (FlightModeManager.BOSS_MOVEMENT_INTERVAL * intervalMultiplier).toLong(),
            tier = tier
        )
        Timber.d("Boss spawned at level $level (tier $tier) with HP=$bossHp, shotInterval=${boss!!.shotInterval}, movementInterval=${boss!!.movementInterval}")
    }

    fun updateGameObjects(
        level: Int,
        shipX: Float,
        shipY: Float,
        currentProjectileSpeed: Float,
        screenWidth: Float,
        screenHeight: Float,
        environment: FlightModeManager.Environment,
        onBossDefeated: () -> Unit
    ) {
        this.currentProjectileSpeed = currentProjectileSpeed
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        val currentTime = System.currentTimeMillis()

        if (level % 10 != 0) {
            bossDefeated = false
        }

        if (level % 10 == 0 && level >= 10 && boss == null && !bossDefeated) {
            spawnBoss(level, maxHp = 100f, screenWidth, screenHeight)
        }

        boss?.update(this, currentTime)

        if (boss != null) {
            val localBoss = boss
            val projectilesToRemove = mutableListOf<Projectile>()
            val homingProjectilesToRemove = mutableListOf<HomingProjectile>()
            for (projectile in projectiles) {
                if (localBoss == null) break
                val projectileRect = RectF(
                    projectile.x - FlightModeManager.PROJECTILE_SIZE,
                    projectile.y - FlightModeManager.PROJECTILE_SIZE,
                    projectile.x + FlightModeManager.PROJECTILE_SIZE,
                    projectile.y + FlightModeManager.PROJECTILE_SIZE
                )
                val bossRect = RectF(
                    localBoss.x - 75f,
                    localBoss.y - 75f,
                    localBoss.x + 75f,
                    localBoss.y + 75f
                )
                if (projectileRect.intersect(bossRect)) {
                    localBoss.hp -= 10f
                    projectilesToRemove.add(projectile)
                    renderer.shipRendererInstance.addExplosionParticles(projectile.x, projectile.y)
                    Timber.d("Boss hit by regular projectile, HP decreased to ${localBoss.hp}")
                    if (localBoss.hp <= 0) {
                        renderer.shipRendererInstance.addExplosionParticles(
                            localBoss.x,
                            localBoss.y
                        )
                        renderer.shipRendererInstance.addScoreTextParticle(
                            localBoss.x,
                            localBoss.y,
                            "+500"
                        )
                        spawnPowerUp(localBoss.x, localBoss.y, "star")
                        spawnPowerUp(localBoss.x + 20f, localBoss.y + 20f, "power_up")
                        Timber.d("Boss defeated! Dropped star and fuel power-up at (x=${localBoss.x}, y=${localBoss.y})")
                        onBossDefeated()
                        boss = null
                        bossDefeated = true
                    }
                }
            }
            if (localBoss != null) {
                val bossRect = RectF(
                    localBoss.x - 75f,
                    localBoss.y - 75f,
                    localBoss.x + 75f,
                    localBoss.y + 75f
                )
                for (projectile in homingProjectiles) {
                    if (projectile.target == localBoss && projectile.checkCollision(bossRect)) {
                        localBoss.hp -= 20f
                        homingProjectilesToRemove.add(projectile)
                        renderer.shipRendererInstance.addExplosionParticles(
                            projectile.x,
                            projectile.y
                        )
                        Timber.d("Boss hit by homing missile, HP decreased to ${localBoss.hp}")
                        if (localBoss.hp <= 0) {
                            renderer.shipRendererInstance.addExplosionParticles(
                                localBoss.x,
                                localBoss.y
                            )
                            renderer.shipRendererInstance.addScoreTextParticle(
                                localBoss.x,
                                localBoss.y,
                                "+500"
                            )
                            spawnPowerUp(localBoss.x, localBoss.y, "star")
                            spawnPowerUp(localBoss.x + 20f, localBoss.y + 20f, "power_up")
                            Timber.d("Boss defeated! Dropped star and fuel power-up at (x=${localBoss.x}, y=${localBoss.y})")
                            onBossDefeated()
                            boss = null
                            bossDefeated = true
                        }
                    }
                }
            }
            projectiles.removeAll(projectilesToRemove)
            homingProjectiles.removeAll(homingProjectilesToRemove)
        } else {
            val powerUpSpawnRate = (powerUpSpawnRateBase * (1 + level * 0.03f)).toLong()
            val asteroidSpawnRate = when (environment) {
                FlightModeManager.Environment.ASTEROID_FIELD -> (asteroidSpawnRateBase / (1 + level * 0.05f) / 1.5).toLong() // More frequent in asteroid fields
                else -> (asteroidSpawnRateBase / (1 + level * 0.05f)).toLong()
            }
            val enemySpawnRate = when (environment) {
                FlightModeManager.Environment.NEBULA -> (enemySpawnRateBase / (1 + level * 0.05f) / 1.5).toLong() // More frequent in nebula
                else -> (enemySpawnRateBase / (1 + level * 0.05f)).toLong()
            }

            if (currentTime - lastPowerUpSpawnTime >= powerUpSpawnRate) {
                spawnPowerUp(screenWidth)
                lastPowerUpSpawnTime = currentTime
                Timber.d("Spawned power-up at time: $currentTime, count: ${powerUps.size}")
            }

            if (currentTime - lastAsteroidSpawnTime >= asteroidSpawnRate) {
                spawnAsteroid(screenWidth, environment)
                lastAsteroidSpawnTime = currentTime
                Timber.d("Spawned asteroid at time: $currentTime, count: ${asteroids.size}")
            }

            if (currentTime - lastEnemySpawnTime >= enemySpawnRate) {
                spawnEnemyShip(screenWidth, level, environment)
                lastEnemySpawnTime = currentTime
                Timber.d("Spawned enemy ship at time: $currentTime, count: ${enemyShips.size}")
            }
        }

        powerUps.forEach { it.update(screenHeight) }
        projectiles.forEach { it.update() }
        enemyShips.forEach { enemy ->
            enemy.y += enemy.speedY
            if (currentTime - enemy.lastShotTime >= enemy.shotInterval) {
                val dx = shipX - enemy.x
                val dy = shipY - enemy.y
                val angle = atan2(dy, dx)
                val speedX = cos(angle) * currentProjectileSpeed
                val speedY = sin(angle) * currentProjectileSpeed
                enemyProjectiles.add(
                    Projectile(
                        enemy.x,
                        enemy.y,
                        speedX,
                        speedY,
                        screenHeight,
                        screenWidth
                    )
                )
                enemy.lastShotTime = currentTime
                Timber.d("Enemy shot projectile towards player")
            }
        }
        enemyProjectiles.forEach { it.update() }

        val asteroidsCopy = asteroids.toMutableList()
        asteroidsCopy.forEach { it.update(screenWidth, screenHeight, level) }

        homingProjectiles.forEach { projectile ->
            projectile.update(this, currentTime)
        }
    }

    fun clearGameObjects() {
        powerUps.clear()
        asteroids.clear()
        projectiles.clear()
        enemyShips.clear()
        enemyProjectiles.clear()
        homingProjectiles.clear()
        boss = null
        bossDefeated = false
    }

    fun cleanupGameObjects(screenHeight: Float, screenWidth: Float) {
        powerUps.removeAll { it.isExpired(screenHeight) }
        asteroids.removeAll { it.isOffScreen(screenHeight, screenWidth) }
        projectiles.removeAll { it.isOffScreen() }
        enemyProjectiles.removeAll { it.isOffScreen() }
        enemyShips.removeAll { it.y > screenHeight + 50f }
        homingProjectiles.removeAll { projectile ->
            projectile.isOffScreen() || projectile.hasHitTarget() || !projectile.isTargetValid(this)
        }
    }

    fun setBoss(bossShip: BossShip?) {
        boss = bossShip
        bossDefeated = bossShip == null
    }
}