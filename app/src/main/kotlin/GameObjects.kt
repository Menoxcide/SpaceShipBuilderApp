package com.example.spaceshipbuilderapp

import android.graphics.RectF
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class PowerUp(var x: Float, var y: Float, val type: String) {
    fun update(screenHeight: Float) {
        y += 5f
    }

    fun isExpired(screenHeight: Float): Boolean = y > screenHeight

    fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "type" to type
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): PowerUp {
            return PowerUp(
                x = (map["x"] as? Double)?.toFloat() ?: 0f,
                y = (map["y"] as? Double)?.toFloat() ?: 0f,
                type = map["type"] as? String ?: "power_up"
            )
        }
    }
}

open class Asteroid(
    var x: Float,
    var y: Float,
    var size: Float,
    var rotation: Float = 120f,
    var angularVelocity: Float = Random.nextFloat() * 6f - 3f, // Randomly select from -3 to 3 degrees per frame
    val spriteId: Int = Random.nextInt(9) // Randomly select from 0 to 8 (9 asteroid sprites)
) {
    open fun update(screenWidth: Float, screenHeight: Float, level: Int) {
        val radius = screenWidth / 8f
        val angle = (System.currentTimeMillis() % 10000) / 10000f * 2 * Math.PI.toFloat()
        // Adjust speed: start at 2 units per frame at level 1, increase to 10 units per frame by level 50
        val speed = 2f + (8f * (level.toFloat() / 50f).coerceIn(0f, 1f)) // Linearly interpolate from 2 to 10
        x += cos(angle) * speed
        y += sin(angle) * speed + speed
        rotation += angularVelocity
    }

    open fun isOffScreen(screenHeight: Float, screenWidth: Float): Boolean = y > screenHeight + size || x < -size || x > screenWidth + size

    open fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "size" to size,
            "rotation" to rotation,
            "angularVelocity" to angularVelocity,
            "isGiant" to false,
            "spriteId" to spriteId // Serialize the spriteId
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Asteroid {
            return if (map["isGiant"] as? Boolean == true) {
                GiantAsteroid(
                    x = (map["x"] as? Double)?.toFloat() ?: 0f,
                    y = (map["y"] as? Double)?.toFloat() ?: 0f,
                    size = (map["size"] as? Double)?.toFloat() ?: 30f,
                    spriteId = (map["spriteId"] as? Long)?.toInt() ?: Random.nextInt(9)
                ).apply {
                    rotation = (map["rotation"] as? Double)?.toFloat() ?: 0f
                    angularVelocity = (map["angularVelocity"] as? Double)?.toFloat() ?: (Random.nextFloat() * 4f - 2f)
                }
            } else {
                Asteroid(
                    x = (map["x"] as? Double)?.toFloat() ?: 0f,
                    y = (map["y"] as? Double)?.toFloat() ?: 0f,
                    size = (map["size"] as? Double)?.toFloat() ?: 30f,
                    rotation = (map["rotation"] as? Double)?.toFloat() ?: 0f,
                    angularVelocity = (map["angularVelocity"] as? Double)?.toFloat() ?: (Random.nextFloat() * 6f - 3f),
                    spriteId = (map["spriteId"] as? Long)?.toInt() ?: Random.nextInt(9)
                )
            }
        }
    }
}

class GiantAsteroid(
    x: Float,
    y: Float,
    size: Float,
    spriteId: Int = Random.nextInt(9) // Randomly select from 0 to 8
) : Asteroid(x, y, size * FlightModeManager.GIANT_ASTEROID_SCALE, 0f, Random.nextFloat() * 4f - 2f, spriteId) { // Randomly select from -2 to 2 degrees per frame
    fun explode(gameObjectManager: GameObjectManager) {
        Timber.d("Giant asteroid exploded at (x=$x, y=$y)")
        gameObjectManager.renderer.shipRendererInstance.addExplosionParticles(x, y)
        repeat(FlightModeManager.SMALL_ASTEROID_COUNT) {
            gameObjectManager.asteroids.add(Asteroid(x, y, size / 2f, Random.nextFloat() * 360f, Random.nextFloat() * 6f - 3f))
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
            "isGiant" to true,
            "spriteId" to spriteId // Serialize the spriteId
        )
    }
}

@Serializable
open class Projectile(
    open var x: Float,
    open var y: Float,
    open var speedX: Float,
    open var speedY: Float,
    open var screenHeight: Float,
    open var screenWidth: Float,
    @Serializable(with = WeaponTypeSerializer::class)
    open var weaponType: WeaponType = WeaponType.Default // Add weaponType property with serializer
) {
    open fun update() {
        x += speedX
        y += speedY
    }

    open fun isOffScreen(): Boolean = y < -20f || y > screenHeight + 20f || x < -20f || x > screenWidth + 20f

    open fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "speedX" to speedX,
            "speedY" to speedY,
            "screenHeight" to screenHeight,
            "screenWidth" to screenWidth,
            "type" to when (this) {
                is PlasmaProjectile -> "PlasmaProjectile"
                is MissileProjectile -> "MissileProjectile"
                is LaserProjectile -> "LaserProjectile"
                is HomingProjectile -> "HomingProjectile"
                else -> "Projectile"
            },
            "weaponType" to weaponType // Serialization handled by WeaponTypeSerializer
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Projectile {
            val x = (map["x"] as? Double)?.toFloat() ?: 0f
            val y = (map["y"] as? Double)?.toFloat() ?: 0f
            val speedX = (map["speedX"] as? Double)?.toFloat() ?: 0f
            val speedY = (map["speedY"] as? Double)?.toFloat() ?: 0f
            val screenHeight = (map["screenHeight"] as? Double)?.toFloat() ?: 0f
            val screenWidth = (map["screenWidth"] as? Double)?.toFloat() ?: 0f
            val type = map["type"] as? String ?: "Projectile"
            val weaponTypeStr = map["weaponType"] as? String ?: "Default"
            val weaponType = when (weaponTypeStr) {
                "Default" -> WeaponType.Default
                "Plasma" -> WeaponType.Plasma
                "Missile" -> WeaponType.Missile
                "HomingMissile" -> WeaponType.HomingMissile
                "Laser" -> WeaponType.Laser
                else -> WeaponType.Default
            }

            return when (type) {
                "PlasmaProjectile" -> PlasmaProjectile(x, y, speedX, speedY, screenHeight, screenWidth, weaponType)
                "MissileProjectile" -> MissileProjectile(x, y, speedX, speedY, screenHeight, screenWidth, weaponType)
                "LaserProjectile" -> LaserProjectile(x, y, speedX, speedY, screenHeight, screenWidth, weaponType)
                "HomingProjectile" -> HomingProjectile.fromMap(map, null) // Will be handled by HomingProjectile's fromMap
                else -> Projectile(x, y, speedX, speedY, screenHeight, screenWidth, weaponType)
            }
        }
    }
}

class PlasmaProjectile(
    x: Float,
    y: Float,
    speedX: Float,
    speedY: Float,
    screenHeight: Float,
    screenWidth: Float,
    weaponType: WeaponType = WeaponType.Plasma
) : Projectile(x, y, speedX, speedY, screenHeight, screenWidth, weaponType)

class MissileProjectile(
    x: Float,
    y: Float,
    speedX: Float,
    speedY: Float,
    screenHeight: Float,
    screenWidth: Float,
    weaponType: WeaponType = WeaponType.Missile
) : Projectile(x, y, speedX, speedY, screenHeight, screenWidth, weaponType)

class LaserProjectile(
    x: Float,
    y: Float,
    speedX: Float,
    speedY: Float,
    screenHeight: Float,
    screenWidth: Float,
    weaponType: WeaponType = WeaponType.Laser
) : Projectile(x, y, speedX, speedY, screenHeight, screenWidth, weaponType)

open class EnemyShip(
    open var x: Float,
    open var y: Float,
    open var speedY: Float,
    open var health: Float = 10f,
    open var damage: Float = 20f,
    open var lastShotTime: Long = 0L,
    open val shotInterval: Long = 4000L
) {
    open fun toMap(): Map<String, Any> {
        return mapOf(
            "type" to "EnemyShip",
            "x" to x,
            "y" to y,
            "speedY" to speedY,
            "health" to health,
            "damage" to damage,
            "lastShotTime" to lastShotTime,
            "shotInterval" to shotInterval
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): EnemyShip {
            val type = map["type"] as? String ?: "EnemyShip"
            val x = (map["x"] as? Double)?.toFloat() ?: 0f
            val y = (map["y"] as? Double)?.toFloat() ?: 0f
            val speedY = (map["speedY"] as? Double)?.toFloat() ?: 5f
            val health = (map["health"] as? Double)?.toFloat() ?: 10f
            val damage = (map["damage"] as? Double)?.toFloat() ?: 20f
            val lastShotTime = map["lastShotTime"] as? Long ?: 0L
            val shotInterval = map["shotInterval"] as? Long ?: 4000L

            return when (type) {
                "DroneEnemy" -> DroneEnemy(x, y, speedY, health, damage, lastShotTime, shotInterval)
                "ArmoredEnemy" -> ArmoredEnemy(x, y, speedY, health, damage, lastShotTime, shotInterval)
                else -> EnemyShip(x, y, speedY, health, damage, lastShotTime, shotInterval)
            }
        }
    }
}

// New DroneEnemy subclass
data class DroneEnemy(
    override var x: Float,
    override var y: Float,
    override var speedY: Float,
    override var health: Float = 10f,
    override var damage: Float = 10f,
    override var lastShotTime: Long = 0L,
    override val shotInterval: Long = 3000L // Shoots more frequently
) : EnemyShip(x, y, speedY, health, damage, lastShotTime, shotInterval) {
    override fun toMap(): Map<String, Any> {
        return mapOf(
            "type" to "DroneEnemy",
            "x" to x,
            "y" to y,
            "speedY" to speedY,
            "health" to health,
            "damage" to damage,
            "lastShotTime" to lastShotTime,
            "shotInterval" to shotInterval
        )
    }
}

// New ArmoredEnemy subclass
data class ArmoredEnemy(
    override var x: Float,
    override var y: Float,
    override var speedY: Float,
    override var health: Float = 50f,
    override var damage: Float = 30f,
    override var lastShotTime: Long = 0L,
    override val shotInterval: Long = 5000L // Shoots less frequently
) : EnemyShip(x, y, speedY, health, damage, lastShotTime, shotInterval) {
    override fun toMap(): Map<String, Any> {
        return mapOf(
            "type" to "ArmoredEnemy",
            "x" to x,
            "y" to y,
            "speedY" to speedY,
            "health" to health,
            "damage" to damage,
            "lastShotTime" to lastShotTime,
            "shotInterval" to shotInterval
        )
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
                    gameObjectManager.screenWidth,
                    WeaponType.Default // Default weapon type for enemy projectiles
                )
            )
            lastShotTime = currentTime
            gameObjectManager.audioManager.playBossShootSound()
            Timber.d("Boss shot projectile at (x=$x, y=$y)")
        }
    }

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

@Serializable
data class HomingProjectile(
    @Contextual
    val target: Any?,
    var hasHit: Boolean = false
) : Projectile(
    x = 0f, // These will be set via fromMap or constructor
    y = 0f,
    speedX = 0f,
    speedY = 0f,
    screenHeight = 0f,
    screenWidth = 0f,
    weaponType = WeaponType.HomingMissile // Default to HomingMissile
) {
    private val speed = 15f

    override fun update() {
        // HomingProjectile updates are handled in the update method below
    }

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

    override fun isOffScreen(): Boolean = y < -FlightModeManager.PROJECTILE_SIZE || y > screenHeight + FlightModeManager.PROJECTILE_SIZE || x < -FlightModeManager.PROJECTILE_SIZE || x > screenWidth + FlightModeManager.PROJECTILE_SIZE

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

    override fun toMap(): Map<String, Any> {
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
            "hasHit" to hasHit,
            "weaponType" to weaponType // Serialization handled by WeaponTypeSerializer
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>, gameObjectManager: GameObjectManager?): HomingProjectile {
            val x = (map["x"] as? Double)?.toFloat() ?: 0f
            val y = (map["y"] as? Double)?.toFloat() ?: 0f
            val screenHeight = (map["screenHeight"] as? Double)?.toFloat() ?: 0f
            val screenWidth = (map["screenWidth"] as? Double)?.toFloat() ?: 0f
            val hasHit = map["hasHit"] as? Boolean ?: false
            val targetData = map["target"] as? Map<String, Any> ?: emptyMap()
            val targetType = targetData["type"] as? String
            val targetX = (targetData["x"] as? Double)?.toFloat() ?: 0f
            val targetY = (targetData["y"] as? Double)?.toFloat() ?: 0f
            val weaponTypeStr = map["weaponType"] as? String ?: "HomingMissile"
            val weaponType = when (weaponTypeStr) {
                "Default" -> WeaponType.Default
                "Plasma" -> WeaponType.Plasma
                "Missile" -> WeaponType.Missile
                "HomingMissile" -> WeaponType.HomingMissile
                "Laser" -> WeaponType.Laser
                else -> WeaponType.HomingMissile // Default to HomingMissile for HomingProjectile
            }

            val target = if (gameObjectManager != null) {
                when (targetType) {
                    "EnemyShip" -> gameObjectManager.enemyShips.minByOrNull { enemy ->
                        val dx = enemy.x - targetX
                        val dy = enemy.y - targetY
                        dx * dx + dy * dy
                    }
                    "BossShip" -> gameObjectManager.getBoss()
                    else -> null
                } ?: gameObjectManager.enemyShips.firstOrNull() // Fallback to first enemy ship if target not found
            } else {
                null // Fallback if gameObjectManager is null
            }

            return HomingProjectile(target, hasHit).apply {
                this.x = x
                this.y = y
                this.screenHeight = screenHeight
                this.screenWidth = screenWidth
                this.weaponType = weaponType
            }
        }
    }
}

// Custom serializer for WeaponType
object WeaponTypeSerializer : KSerializer<WeaponType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("WeaponType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WeaponType) {
        val stringValue = when (value) {
            is WeaponType.Default -> "Default"
            is WeaponType.Plasma -> "Plasma"
            is WeaponType.Missile -> "Missile"
            is WeaponType.HomingMissile -> "HomingMissile"
            is WeaponType.Laser -> "Laser"
        }
        encoder.encodeString(stringValue)
    }

    override fun deserialize(decoder: Decoder): WeaponType {
        val stringValue = decoder.decodeString()
        return when (stringValue) {
            "Default" -> WeaponType.Default
            "Plasma" -> WeaponType.Plasma
            "Missile" -> WeaponType.Missile
            "HomingMissile" -> WeaponType.HomingMissile
            "Laser" -> WeaponType.Laser
            else -> WeaponType.Default // Fallback to Default if unknown
        }
    }
}