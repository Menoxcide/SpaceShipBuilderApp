package com.example.spaceshipbuilderapp

import android.graphics.RectF
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class PowerUp(var x: Float, var y: Float, val type: String) {
    fun update(screenHeight: Float) {
        y += 5f
    }

    fun isExpired(screenHeight: Float): Boolean = y > screenHeight
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

class GiantAsteroid(x: Float, y: Float, size: Float) : Asteroid(x, y, size * FlightModeManager.GIANT_ASTEROID_SCALE, 0f, 0.15f) {
    fun explode(gameObjectManager: GameObjectManager) {
        Timber.d("Giant asteroid exploded at (x=$x, y=$y)")
        gameObjectManager.renderer.particleSystem.addExplosionParticles(x, y)
        repeat(FlightModeManager.SMALL_ASTEROID_COUNT) {
            gameObjectManager.asteroids.add(Asteroid(x, y, size / 2f, Random.nextFloat() * 360f, Random.nextFloat() * 0.2f))
        }
        gameObjectManager.asteroids.remove(this)
    }

    override fun isOffScreen(screenHeight: Float, screenWidth: Float): Boolean = y > screenHeight + size || x < -size || x > screenWidth + size
}

data class Projectile(var x: Float, var y: Float, var speedX: Float, var speedY: Float, val screenHeight: Float, val screenWidth: Float) {
    fun update() {
        x += speedX
        y += speedY
    }

    fun isOffScreen(): Boolean = y < -FlightModeManager.PROJECTILE_SIZE || y > screenHeight + FlightModeManager.PROJECTILE_SIZE || x < -FlightModeManager.PROJECTILE_SIZE || x > screenWidth + FlightModeManager.PROJECTILE_SIZE
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
    fun update(gameObjectManager: GameObjectManager, currentTime: Long) {
        x += speedX
        y += speedY
        x = x.coerceIn(75f, gameObjectManager.screenWidth - 75f)
        y = y.coerceIn(gameObjectManager.screenHeight * 0.05f, gameObjectManager.screenHeight * 0.3f)

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
            gameObjectManager.enemyProjectiles.add(
                Projectile(
                    x,
                    y + 75f,
                    0f,
                    gameObjectManager.currentProjectileSpeed,
                    gameObjectManager.screenHeight,
                    gameObjectManager.screenWidth
                )
            )
            lastShotTime = currentTime
            gameObjectManager.audioManager.playBossShootSound()
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

    fun update(gameObjectManager: GameObjectManager, currentTime: Long) {
        if (!isTargetValid(gameObjectManager)) return

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
            gameObjectManager.renderer.particleSystem.addMissileExhaustParticles(x, y + 10f)
        }
    }

    fun isOffScreen(): Boolean = y < -FlightModeManager.PROJECTILE_SIZE || y > screenHeight + FlightModeManager.PROJECTILE_SIZE || x < -FlightModeManager.PROJECTILE_SIZE || x > screenWidth + FlightModeManager.PROJECTILE_SIZE

    fun hasHitTarget(): Boolean = hasHit

    fun checkCollision(targetRect: RectF): Boolean {
        val projectileRect = RectF(
            x - FlightModeManager.PROJECTILE_SIZE,
            y - FlightModeManager.PROJECTILE_SIZE,
            x + FlightModeManager.PROJECTILE_SIZE,
            y + FlightModeManager.PROJECTILE_SIZE
        )
        if (projectileRect.intersect(targetRect)) {
            hasHit = true
            return true
        }
        return false
    }

    fun isTargetValid(gameObjectManager: GameObjectManager): Boolean {
        return when (target) {
            is EnemyShip -> gameObjectManager.enemyShips.contains(target)
            is BossShip -> gameObjectManager.getBoss() == target
            else -> false
        }
    }
}