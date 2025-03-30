package com.example.spaceshipbuilderapp

import android.graphics.Bitmap
import android.graphics.Canvas
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

class ShipManager @Inject constructor(
    private val skillManager: SkillManager
) {
    var shipX: Float = 0f
    var shipY: Float = 0f
    var totalShipHeight: Float = 0f
    var maxPartHalfWidth: Float = 0f
    var mergedShipBitmap: Bitmap? = null

    var fuel = 0f
    var fuelCapacity = 100f
    var hp = 100f
    var maxHp = 100f
        get() {
            val baseHp = when (selectedShipSet) {
                0 -> 100f
                1 -> 150f
                2 -> 200f
                else -> 100f
            }
            return baseHp * (1 + (skillManager.skills["max_hp"] ?: 0) * 0.10f)
        }

    var missileCount = 3
        get() {
            return field.coerceAtMost(maxMissiles)
        }
        set(value) {
            field = value.coerceIn(0, maxMissiles)
        }
    var maxMissiles = 3
        get() {
            val baseMissiles = when (selectedShipSet) {
                0 -> 3
                1 -> 4
                2 -> 5
                else -> 3
            }
            return baseMissiles + (skillManager.skills["homing_missiles"] ?: 0)
        }

    var shipColor: String = "default"
    var selectedShipSet: Int = 0
        set(value) {
            if (value in unlockedShipSets) {
                field = value
                applyShipSetCharacteristics()
                Timber.d("Selected ship set: $value")
            } else {
                Timber.w("Cannot select ship set $value, not unlocked yet")
            }
        }
    private val unlockedShipSets = mutableSetOf(0)

    var baseFuelConsumption = 0.05f
    var currentFuelConsumption = baseFuelConsumption
    var baseSpeed = 5f
    var currentSpeed = baseSpeed
    var baseProjectileSpeed = 10f
    var currentProjectileSpeed = baseProjectileSpeed

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    var reviveCount: Int = 0
        set(value) {
            field = value.coerceIn(0, 3)
        }

    var destroyAllCharges: Int = 0
        set(value) {
            field = value.coerceIn(0, 3)
        }

    fun updateUnlockedShipSets(highestLevel: Int, starsCollected: Int) {
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
            updateUnlockedShipSets(highestLevel, starsCollected)
        } else {
            Timber.w("Invalid ship set $set to unlock")
        }
    }

    fun getUnlockedShipSets(): Set<Int> = unlockedShipSets.toSet()

    fun applyShipColorEffects() {
        currentFuelConsumption = baseFuelConsumption * (1 - (skillManager.skills["fuel_efficiency"] ?: 0) * 0.05f)
        currentSpeed = baseSpeed * (1 + (skillManager.skills["speed_boost"] ?: 0) * 0.05f)
        Timber.d("Applied ship color effects: speed=$currentSpeed, fuelConsumption=$currentFuelConsumption")
    }

    fun applyShipSetCharacteristics() {
        baseSpeed = 5f * (1 + (skillManager.skills["speed_boost"] ?: 0) * 0.05f)
        currentSpeed = baseSpeed
        baseProjectileSpeed = 10f * (1 + (skillManager.skills["projectile_damage"] ?: 0) * 0.10f)
        currentProjectileSpeed = baseProjectileSpeed
        missileCount = maxMissiles
        hp = maxHp
        fuel = fuelCapacity

        when (selectedShipSet) {
            1 -> {
                baseSpeed *= 1.1f
                currentSpeed = baseSpeed
                baseProjectileSpeed *= 1.1f
                currentProjectileSpeed = baseProjectileSpeed
            }
            2 -> {
                baseSpeed *= 1.2f
                currentSpeed = baseSpeed
                baseProjectileSpeed *= 1.2f
                currentProjectileSpeed = baseProjectileSpeed
            }
        }
        Timber.d("Applied ship set $selectedShipSet characteristics: speed=$currentSpeed, projectileSpeed=$currentProjectileSpeed, maxMissiles=$maxMissiles, maxHp=$maxHp")
    }

    fun launchShip(screenWidth: Float, screenHeight: Float, sortedParts: List<Part>) {
        fuel = 50f
        hp = maxHp
        if (screenWidth > 0f && screenHeight > 0f) {
            shipX = screenWidth / 2f
            shipY = screenHeight / 2f
            this.screenWidth = screenWidth
            this.screenHeight = screenHeight
        } else {
            Timber.w("Cannot set ship position: invalid screen dimensions (width=$screenWidth, height=$screenHeight)")
        }

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

        applyShipColorEffects()
        applyShipSetCharacteristics()
        Timber.d("Ship launched: totalShipHeight=$totalShipHeight, maxPartHalfWidth=$maxPartHalfWidth")
    }

    fun moveShip(direction: Int) {
        when (direction) {
            1 -> shipY -= currentSpeed
            2 -> shipX += currentSpeed
            3 -> shipY += currentSpeed
            4 -> shipX -= currentSpeed
        }
        shipX = shipX.coerceIn(maxPartHalfWidth, screenWidth - maxPartHalfWidth)
        shipY = shipY.coerceIn(totalShipHeight / 2, screenHeight - totalShipHeight / 2)
        Timber.d("Moved ship with speed $currentSpeed to (x=$shipX, y=$shipY)")
    }

    fun stopShip() {
        Timber.d("stopShip called (no velocity to stop with dragging)")
    }

    fun reset() {
        if (screenWidth > 0f && screenHeight > 0f) {
            shipX = screenWidth / 2f
            shipY = screenHeight / 2f
        }
        missileCount = maxMissiles
        lastMissileRechargeTime = System.currentTimeMillis()
        Timber.d("ShipManager reset: missileCount=$missileCount")
    }

    fun onDestroy() {
        mergedShipBitmap?.recycle()
        mergedShipBitmap = null
        Timber.d("ShipManager onDestroy called")
    }

    var lastMissileRechargeTime = System.currentTimeMillis()
    val missileRechargeTime = 10000L

    var level = 1
    var highestLevel = 1
    var starsCollected = 0
}