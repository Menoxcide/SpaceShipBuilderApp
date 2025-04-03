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

    // Convert to a map for Firebase storage
    fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "type" to type
        )
    }

    companion object {
        // Convert from a map (loaded from Firebase) to PowerUp
        fun fromMap(map: Map<String, Any>): PowerUp {
            return PowerUp(
                x = (map["x"] as? Double)?.toFloat() ?: 0f,
                y = (map["y"] as? Double)?.toFloat() ?: 0f,
                type = map["type"] as? String ?: "power_up"
            )
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

    // Convert to a map for Firebase storage
    open fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "size" to size,
            "rotation" to rotation,
            "angularVelocity" to angularVelocity,
            "isGiant" to false
        )
    }

    companion object {
        // Convert from a map (loaded from Firebase) to Asteroid
        fun fromMap(map: Map<String, Any>): Asteroid {
            return if (map["isGiant"] as? Boolean == true) {
                GiantAsteroid(
                    x = (map["x"] as? Double)?.toFloat() ?: 0f,
                    y = (map["y"] as? Double)?.toFloat() ?: 0f,
                    size = (map["size"] as? Double)?.toFloat() ?: 30f
                ).apply {
                    rotation = (map["rotation"] as? Double)?.toFloat() ?: 0f
                    angularVelocity = (map["angularVelocity"] as? Double)?.toFloat() ?: 0.15f
                }
            } else {
                Asteroid(
                    x = (map["x"] as? Double)?.toFloat() ?: 0f,
                    y = (map["y"] as? Double)?.toFloat() ?: 0f,
                    size = (map["size"] as? Double)?.toFloat() ?: 30f,
                    rotation = (map["rotation"] as? Double)?.toFloat() ?: 0f,
                    angularVelocity = (map["angularVelocity"] as? Double)?.toFloat() ?: Random.nextFloat() * 0.1f
                )
            }
        }
    }
}

class GiantAsteroid(x: Float, y: Float, size: Float) : Asteroid(x, y, size * FlightModeManager.GIANT_ASTEROID_SCALE, 0f, 0.15f) {
    fun explode(gameObjectManager: GameObjectManager) {
        Timber.d("Giant asteroid exploded at (x=$x, y=$y)")
        gameObjectManager.renderer.shipRendererInstance.addExplosionParticles(x, y)
        repeat(FlightModeManager.SMALL_ASTEROID_COUNT) {
            gameObjectManager.asteroids.add(Asteroid(x, y, size / 2f, Random.nextFloat() * 360f, Random.nextFloat() * 0.2f))
        }
        gameObjectManager.asteroids.remove(this)
    }

    override fun isOffScreen(screenHeight: Float, screenWidth: Float): Boolean = y > screenHeight + size || x < -size || x > screenWidth + size

    override fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "size" to size / FlightModeManager.GIANT_ASTEROID_SCALE, // Store the base size
            "rotation" to rotation,
            "angularVelocity" to angularVelocity,
            "isGiant" to true
        )
    }
}

data class Projectile(var x: Float, var y: Float, var speedX: Float, var speedY: Float, val screenHeight: Float, val screenWidth: Float) {
    fun update() {
        x += speedX
        y += speedY
    }

    fun isOffScreen(): Boolean = y < -FlightModeManager.PROJECTILE_SIZE || y > screenHeight + FlightModeManager.PROJECTILE_SIZE || x < -FlightModeManager.PROJECTILE_SIZE || x > screenWidth + FlightModeManager.PROJECTILE_SIZE

    // Convert to a map for Firebase storage
    fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "speedX" to speedX,
            "speedY" to speedY,
            "screenHeight" to screenHeight,
            "screenWidth" to screenWidth
        )
    }

    companion object {
        // Convert from a map (loaded from Firebase) to Projectile
        fun fromMap(map: Map<String, Any>): Projectile {
            return Projectile(
                x = (map["x"] as? Double)?.toFloat() ?: 0f,
                y = (map["y"] as? Double)?.toFloat() ?: 0f,
                speedX = (map["speedX"] as? Double)?.toFloat() ?: 0f,
                speedY = (map["speedY"] as? Double)?.toFloat() ?: 0f,
                screenHeight = (map["screenHeight"] as? Double)?.toFloat() ?: 0f,
                screenWidth = (map["screenWidth"] as? Double)?.toFloat() ?: 0f
            )
        }
    }
}

data class EnemyShip(var x: Float, var y: Float, var speedY: Float, var lastShotTime: Long = 0L, val shotInterval: Long) {
    // Convert to a map for Firebase storage
    fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "speedY" to speedY,
            "lastShotTime" to lastShotTime,
            "shotInterval" to shotInterval
        )
    }

    companion object {
        // Convert from a map (loaded from Firebase) to EnemyShip
        fun fromMap(map: Map<String, Any>): EnemyShip {
            return EnemyShip(
                x = (map["x"] as? Double)?.toFloat() ?: 0f,
                y = (map["y"] as? Double)?.toFloat() ?: 0f,
                speedY = (map["speedY"] as? Double)?.toFloat() ?: 5f,
                lastShotTime = map["lastShotTime"] as? Long ?: 0L,
                shotInterval = map["shotInterval"] as? Long ?: 4000L
            )
        }
    }
}

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

    // Convert to a map for Firebase storage
    fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "hp" to hp,
            "maxHp" to maxHp,
            "shotInterval" to shotInterval,
            "movementInterval" to movementInterval,
            "speedX" to speedX,
            "speedY" to speedY,
            "lastShotTime" to lastShotTime,
            "lastMovementChange" to lastMovementChange,
            "tier" to tier
        )
    }

    companion object {
        // Convert from a map (loaded from Firebase) to BossShip
        fun fromMap(map: Map<String, Any>): BossShip {
            return BossShip(
                x = (map["x"] as? Double)?.toFloat() ?: 0f,
                y = (map["y"] as? Double)?.toFloat() ?: 0f,
                hp = (map["hp"] as? Double)?.toFloat() ?: 100f,
                maxHp = (map["maxHp"] as? Double)?.toFloat() ?: 100f,
                shotInterval = map["shotInterval"] as? Long ?: FlightModeManager.BOSS_SHOT_INTERVAL,
                movementInterval = map["movementInterval"] as? Long ?: FlightModeManager.BOSS_MOVEMENT_INTERVAL,
                speedX = (map["speedX"] as? Double)?.toFloat() ?: 0f,
                speedY = (map["speedY"] as? Double)?.toFloat() ?: 0f,
                lastShotTime = map["lastShotTime"] as? Long ?: 0L,
                lastMovementChange = map["lastMovementChange"] as? Long ?: 0L,
                tier = (map["tier"] as? Long)?.toInt() ?: 1
            )
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
            gameObjectManager.renderer.shipRendererInstance.addMissileExhaustParticles(x, y + 10f)
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

    // Convert to a map for Firebase storage
    fun toMap(): Map<String, Any> {
        val targetData = when (target) {
            is EnemyShip -> mapOf(
                "type" to "EnemyShip",
                "x" to target.x,
                "y" to target.y
            )
            is BossShip -> mapOf(
                "type" to "BossShip",
                "x" to target.x,
                "y" to target.y
            )
            else -> mapOf("type" to "Unknown")
        }
        return mapOf(
            "x" to x,
            "y" to y,
            "target" to targetData,
            "screenHeight" to screenHeight,
            "screenWidth" to screenWidth,
            "hasHit" to hasHit
        )
    }

    companion object {
        // Convert from a map (loaded from Firebase) to HomingProjectile
        // Note: The target will be resolved by GameObjectManager during restoration
        fun fromMap(map: Map<String, Any>, gameObjectManager: GameObjectManager): HomingProjectile {
            val x = (map["x"] as? Double)?.toFloat() ?: 0f
            val y = (map["y"] as? Double)?.toFloat() ?: 0f
            val screenHeight = (map["screenHeight"] as? Double)?.toFloat() ?: 0f
            val screenWidth = (map["screenWidth"] as? Double)?.toFloat() ?: 0f
            val hasHit = map["hasHit"] as? Boolean ?: false
            val targetData = map["target"] as? Map<String, Any> ?: emptyMap()
            val targetType = targetData["type"] as? String
            val targetX = (targetData["x"] as? Double)?.toFloat() ?: 0f
            val targetY = (targetData["y"] as? Double)?.toFloat() ?: 0f

            // Find the closest matching target based on position
            val target = when (targetType) {
                "EnemyShip" -> gameObjectManager.enemyShips.minByOrNull { enemy ->
                    val dx = enemy.x - targetX
                    val dy = enemy.y - targetY
                    dx * dx + dy * dy
                }
                "BossShip" -> gameObjectManager.getBoss()
                else -> null
            } ?: gameObjectManager.enemyShips.firstOrNull() // Fallback to first enemy ship if target not found

            return HomingProjectile(x, y, target ?: Any(), screenHeight, screenWidth).apply {
                this.hasHit = hasHit
            }
        }
    }
}