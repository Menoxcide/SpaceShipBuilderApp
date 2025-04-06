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
    private val asteroidSpawnRateBase = 1000L
    private val enemySpawnRateBase = 5000L
    private var lastPowerUpSpawnTime = System.currentTimeMillis()
    private var lastAsteroidSpawnTime = System.currentTimeMillis()
    private var lastEnemySpawnTime = System.currentTimeMillis()

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    var currentProjectileSpeed: Float = 10f

    private val bitmapManager: BitmapManager = BitmapManager(audioManager.context)

    companion object {
        const val BASE_MAX_ASTEROIDS = 5
        const val AIM_ASSIST_RANGE = 200f
        const val AIM_ASSIST_STRENGTH = 0.5f
    }

    fun getBoss(): BossShip? = boss

    fun spawnPowerUp(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        val types = listOf("power_up", "shield", "speed", "stealth", "warp", "star", "invincibility")
        val type = if (Random.nextFloat() < 0.4f) "power_up" else types.random()
        powerUps.add(PowerUp(x, y, type))
    }

    fun spawnPowerUp(x: Float, y: Float, type: String) {
        powerUps.add(PowerUp(x, y, type))
    }

    fun spawnAsteroid(screenWidth: Float, environment: FlightModeManager.Environment) {
        val maxAsteroids = BASE_MAX_ASTEROIDS + (15 * (level.toFloat() / 50f).coerceIn(0f, 1f)).toInt()
        if (asteroids.size >= maxAsteroids) return

        val x = Random.nextFloat() * screenWidth
        val y = -screenHeight * 0.1f
        val size = Random.nextFloat() * 30f + 20f
        val rotation = Random.nextFloat() * 20f
        val angularVelocity = Random.nextFloat() * 6f - 3f

        val asteroidProbability = if (environment == FlightModeManager.Environment.ASTEROID_FIELD) {
            FlightModeManager.GIANT_ASTEROID_PROBABILITY * 1.5f
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
            1 -> DroneEnemy(x, y, speedY * 1.5f)
            2 -> ArmoredEnemy(x, y, speedY * 0.5f)
            else -> EnemyShip(x, y, speedY, shotInterval = 4000L)
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

    private var level: Int = 1

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
        this.level = level
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
                val bossRect = RectF(localBoss.x - 75f, localBoss.y - 75f, localBoss.x + 75f, localBoss.y + 75f)
                if (projectileRect.intersect(bossRect)) {
                    localBoss.hp -= 10f
                    projectilesToRemove.add(projectile)
                    renderer.shipRendererInstance.addExplosionParticles(projectile.x, projectile.y)
                    Timber.d("Boss hit by regular projectile, HP decreased to ${localBoss.hp}")
                    if (localBoss.hp <= 0) {
                        renderer.shipRendererInstance.addExplosionParticles(localBoss.x, localBoss.y)
                        renderer.shipRendererInstance.addScoreTextParticle(localBoss.x, localBoss.y, "+500")
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
                val bossRect = RectF(localBoss.x - 75f, localBoss.y - 75f, localBoss.x + 75f, localBoss.y + 75f)
                for (projectile in homingProjectiles) {
                    if (projectile.target == localBoss && projectile.checkCollision(bossRect)) {
                        localBoss.hp -= 20f
                        homingProjectilesToRemove.add(projectile)
                        renderer.shipRendererInstance.addExplosionParticles(projectile.x, projectile.y)
                        Timber.d("Boss hit by homing missile, HP decreased to ${localBoss.hp}")
                        if (localBoss.hp <= 0) {
                            renderer.shipRendererInstance.addExplosionParticles(localBoss.x, localBoss.y)
                            renderer.shipRendererInstance.addScoreTextParticle(localBoss.x, localBoss.y, "+500")
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
                FlightModeManager.Environment.ASTEROID_FIELD -> (asteroidSpawnRateBase - (800f * (level.toFloat() / 50f).coerceIn(0f, 1f)) / 1.5).toLong()
                else -> (asteroidSpawnRateBase - (800f * (level.toFloat() / 50f).coerceIn(0f, 1f))).toLong()
            }
            val enemySpawnRate = when (environment) {
                FlightModeManager.Environment.NEBULA -> (enemySpawnRateBase / (1 + level * 0.05f) / 1.5).toLong()
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
        projectiles.forEach { projectile ->
            projectile.update()
            if (projectile !is HomingProjectile) {
                applyAimAssist(projectile, shipX, shipY)
            }
        }
        enemyShips.forEach { enemy ->
            enemy.y += enemy.speedY
            if (currentTime - enemy.lastShotTime >= enemy.shotInterval) {
                val dx = shipX - enemy.x
                val dy = shipY - enemy.y
                val angle = atan2(dy, dx)
                val speedX = cos(angle) * currentProjectileSpeed
                val speedY = sin(angle) * currentProjectileSpeed
                enemyProjectiles.add(Projectile(enemy.x, enemy.y, speedX, speedY, screenHeight, screenWidth))
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

    private fun applyAimAssist(projectile: Projectile, shipX: Float, shipY: Float) {
        if (projectile is HomingProjectile) return // Skip homing missiles

        // Validate existing target
        projectile.target?.let { target ->
            val isValidTarget = when (target) {
                is EnemyShip -> enemyShips.contains(target)
                is Asteroid -> asteroids.contains(target)
                is BossShip -> boss == target
                else -> false
            }
            if (!isValidTarget) {
                projectile.target = null // Clear invalid target
                Timber.d("Cleared invalid target from projectile at (${projectile.x}, ${projectile.y})")
            } else {
                val (targetX, targetY) = when (target) {
                    is EnemyShip -> Pair(target.x, target.y)
                    is Asteroid -> Pair(target.x, target.y)
                    is BossShip -> Pair(target.x, target.y)
                    else -> return
                }
                val dx = targetX - projectile.x
                val dy = targetY - projectile.y
                val distance = kotlin.math.hypot(dx, dy)
                if (distance > 0) {
                    val adjustX = (dx / distance) * AIM_ASSIST_STRENGTH * currentProjectileSpeed
                    val adjustY = (dy / distance) * AIM_ASSIST_STRENGTH * currentProjectileSpeed
                    projectile.speedX = (projectile.speedX + adjustX).coerceIn(-currentProjectileSpeed, currentProjectileSpeed)
                    projectile.speedY = (projectile.speedY + adjustY).coerceIn(-currentProjectileSpeed, currentProjectileSpeed)
                    val speedMagnitude = kotlin.math.hypot(projectile.speedX, projectile.speedY)
                    if (speedMagnitude > 0) {
                        projectile.speedX = (projectile.speedX / speedMagnitude) * currentProjectileSpeed
                        projectile.speedY = (projectile.speedY / speedMagnitude) * currentProjectileSpeed
                    }
                }
                return
            }
        }

        // Find nearest on-screen target if no valid target is set
        val targets = mutableListOf<Pair<Any, Pair<Float, Float>>>()
        enemyShips.forEach { if (!it.isOffScreen()) targets.add(it to (it.x to it.y)) }
        asteroids.forEach { if (!it.isOffScreen(screenHeight, screenWidth)) targets.add(it to (it.x to it.y)) }
        boss?.let { if (!it.isOffScreen()) targets.add(it to (it.x to it.y)) }

        var nearestTarget: Pair<Any, Pair<Float, Float>>? = null
        var minDistance = AIM_ASSIST_RANGE.toFloat()

        targets.forEach { (obj, pos) ->
            val (targetX, targetY) = pos
            val distance = kotlin.math.hypot(projectile.x - targetX, projectile.y - targetY)
            if (distance < minDistance) {
                minDistance = distance
                nearestTarget = obj to (targetX to targetY)
            }
        }

        nearestTarget?.let { (targetObj, pos) ->
            projectile.target = targetObj // Lock onto this target
            val (targetX, targetY) = pos
            val dx = targetX - projectile.x
            val dy = targetY - projectile.y
            val distance = kotlin.math.hypot(dx, dy)
            if (distance > 0) {
                val adjustX = (dx / distance) * AIM_ASSIST_STRENGTH * currentProjectileSpeed
                val adjustY = (dy / distance) * AIM_ASSIST_STRENGTH * currentProjectileSpeed
                projectile.speedX = (projectile.speedX + adjustX).coerceIn(-currentProjectileSpeed, currentProjectileSpeed)
                projectile.speedY = (projectile.speedY + adjustY).coerceIn(-currentProjectileSpeed, currentProjectileSpeed)
                val speedMagnitude = kotlin.math.hypot(projectile.speedX, projectile.speedY)
                if (speedMagnitude > 0) {
                    projectile.speedX = (projectile.speedX / speedMagnitude) * currentProjectileSpeed
                    projectile.speedY = (projectile.speedY / speedMagnitude) * currentProjectileSpeed
                }
            }
            Timber.d("Projectile locked onto target at ($targetX, $targetY), type=${targetObj.javaClass.simpleName}")
        }
    }

    private fun EnemyShip.isOffScreen(): Boolean = y > screenHeight + 50f || y < -50f || x < -50f || x > screenWidth + 50f
    private fun BossShip.isOffScreen(): Boolean = y > screenHeight + 75f || y < -75f || x < -75f || x > screenWidth + 75f

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
        enemyShips.removeAll { it.isOffScreen() }
        homingProjectiles.removeAll { projectile ->
            projectile.isOffScreen() || projectile.hasHitTarget() || !projectile.isTargetValid(this)
        }
    }

    fun setBoss(bossShip: BossShip?) {
        boss = bossShip
        bossDefeated = bossShip == null
    }
}