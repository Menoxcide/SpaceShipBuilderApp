package com.example.spaceshipbuilderapp

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

enum class GameState { BUILD, FLIGHT }

class GameEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    val renderer: Renderer,
    private val highscoreManager: HighscoreManager
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SpaceshipBuilder", Context.MODE_PRIVATE)
    var gameState: GameState = GameState.BUILD
        set(value) {
            if (field == value) return
            field = value
            if (value == GameState.FLIGHT) {
                onLaunchListener?.invoke(true)
            } else {
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
                currentScore = 0
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
    private var onLaunchListener: ((Boolean) -> Unit)? = null
    var onPowerUpCollectedListener: ((Float, Float) -> Unit)? = null
    var currentScore = 0

    var shipX: Float = 0f
    var shipY: Float = 0f
    var totalShipHeight: Float = 0f
    var maxPartHalfWidth: Float = 0f
    var mergedShipBitmap: Bitmap? = null

    val powerUps = mutableListOf<PowerUp>()
    val asteroids = mutableListOf<Asteroid>()
    private val powerUpSpawnRate = 1500L
    private val asteroidSpawnRate = 1500L
    private var lastPowerUpSpawnTime = System.currentTimeMillis()
    private var lastAsteroidSpawnTime = System.currentTimeMillis()
    private var lastScoreUpdateTime = System.currentTimeMillis()

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f
    var statusBarHeight: Float = 0f

    var cockpitY: Float = 0f
    var fuelTankY: Float = 0f
    var engineY: Float = 0f

    private var mediaPlayer: MediaPlayer? = null

    private var shieldActive = false
    private var shieldEndTime = 0L
    private var speedBoostActive = false
    private var speedBoostEndTime = 0L
    private var stealthActive = false
    private var stealthEndTime = 0L
    private val effectDuration = 10000L
    private var baseFuelConsumption = 0.05f
    private var currentFuelConsumption = baseFuelConsumption
    private var baseSpeed = 5f
    private var currentSpeed = baseSpeed

    companion object {
        const val ALIGNMENT_THRESHOLD = 75f
        const val PANEL_HEIGHT = 150f
    }

    init {
        loadPersistentData()
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

    fun update(screenWidth: Float, screenHeight: Float) {
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

        fuel -= currentFuelConsumption
        shipX = shipX.coerceIn(maxPartHalfWidth, this.screenWidth - maxPartHalfWidth)
        shipY = shipY.coerceIn(totalShipHeight / 2, this.screenHeight - totalShipHeight / 2)

        if (currentTime - lastScoreUpdateTime >= 1000) {
            currentScore += 10
            lastScoreUpdateTime = currentTime
        }

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

        powerUps.forEach { it.update(this.screenHeight) }
        asteroids.forEach { it.update(this.screenHeight) }

        val powerUpsToRemove = mutableListOf<PowerUp>()
        val asteroidsToRemove = mutableListOf<Asteroid>()
        for (powerUp in powerUps) {
            val powerUpRect = RectF(
                powerUp.x - 20f, powerUp.y - 20f,
                powerUp.x + 20f, powerUp.y + 20f
            )
            if (checkCollision(powerUpRect)) {
                when (powerUp.type) {
                    "power_up" -> fuel = (fuel + 20f).coerceAtMost(fuelCapacity)
                    "shield" -> {
                        shieldActive = true
                        shieldEndTime = currentTime + effectDuration
                        currentFuelConsumption = baseFuelConsumption / 2f
                        Timber.d("Shield activated, fuel consumption reduced to $currentFuelConsumption")
                    }
                    "speed" -> {
                        speedBoostActive = true
                        speedBoostEndTime = currentTime + effectDuration
                        currentSpeed = baseSpeed * 2f
                        Timber.d("Speed boost activated, speed increased to $currentSpeed")
                    }
                    "stealth" -> {
                        stealthActive = true
                        stealthEndTime = currentTime + effectDuration
                        Timber.d("Stealth activated")
                    }
                    "warp" -> {
                        shipX = Random.nextFloat() * (this.screenWidth - 2 * maxPartHalfWidth) + maxPartHalfWidth
                        shipY = Random.nextFloat() * (this.screenHeight - totalShipHeight) + totalShipHeight / 2
                        Timber.d("Warp activated, ship teleported to (x=$shipX, y=$shipY)")
                    }
                    "star" -> {
                        currentScore += 50
                        Timber.d("Star collected, score increased to $currentScore")
                    }
                }
                powerUpsToRemove.add(powerUp)
                Timber.d("Collected ${powerUp.type} power-up")
                onPowerUpCollectedListener?.invoke(powerUp.x, powerUp.y)
                playPowerUpSound()
            }
        }
        for (asteroid in asteroids) {
            val asteroidRect = RectF(
                asteroid.x - 30f, asteroid.y - 30f,
                asteroid.x + 30f, asteroid.y + 30f
            )
            if (checkCollision(asteroidRect) && !stealthActive) {
                hp -= 10f
                asteroidsToRemove.add(asteroid)
                renderer.particleSystem.addCollisionParticles(shipX, shipY) // Center of ship
                renderer.particleSystem.addDamageTextParticle(shipX, shipY, 10) // Floating -10 text
                playCollisionSound()
                Timber.d("Hit asteroid, HP decreased to $hp")
            }
        }
        powerUps.removeAll(powerUpsToRemove)
        asteroids.removeAll(asteroidsToRemove)

        powerUps.removeAll { it.isExpired(this.screenHeight) }
        asteroids.removeAll { it.isOffScreen(this.screenHeight) }
        Timber.d("After cleanup: ${powerUps.size} power-ups, ${asteroids.size} asteroids, HP: $hp, Fuel: $fuel")

        if (fuel <= 0 || hp <= 0) {
            highscoreManager.addScore(currentScore)
            gameState = GameState.BUILD
            powerUps.clear()
            asteroids.clear()
            resetPowerUpEffects()
            hp = maxHp
            fuel = 0f
        }
    }

    private fun resetPowerUpEffects() {
        shieldActive = false
        speedBoostActive = false
        stealthActive = false
        currentFuelConsumption = baseFuelConsumption
        currentSpeed = baseSpeed
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

    fun rotatePart(partType: String) {
        parts.find { it.type == partType }?.let {
            it.rotation = (it.rotation + 90f) % 360f
            Timber.d("Rotated $partType to ${it.rotation} degrees")
        } ?: Timber.w("Part $partType not found")
    }

    fun moveShip(direction: Int) {
        when (direction) {
            1 -> shipY -= currentSpeed // Up
            2 -> shipX += currentSpeed // Right
            3 -> shipY += currentSpeed // Down
            4 -> shipX -= currentSpeed // Left
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

    private fun loadPersistentData() {}

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        onLaunchListener = listener
    }

    fun notifyLaunchListener() {
        onLaunchListener?.invoke(gameState == GameState.FLIGHT)
        Timber.d("Notified launch listener, isLaunching=${gameState == GameState.FLIGHT}")
    }

    private fun spawnPowerUp(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        val types = listOf("power_up", "shield", "speed", "stealth", "warp", "star")
        val type = if (Random.nextFloat() < 0.5f) "power_up" else types.random()
        powerUps.add(PowerUp(x, y, type))
    }

    private fun spawnAsteroid(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = 0f
        asteroids.add(Asteroid(x, y))
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
            mediaPlayer = MediaPlayer.create(context, R.raw.x)
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

    data class Asteroid(var x: Float, var y: Float) {
        fun update(screenHeight: Float) {
            y += 7f
        }

        fun isOffScreen(screenHeight: Float): Boolean {
            val offScreen = y > screenHeight
            if (offScreen) Timber.d("Asteroid off-screen at y=$y, screenHeight=$screenHeight")
            return offScreen
        }
    }
}