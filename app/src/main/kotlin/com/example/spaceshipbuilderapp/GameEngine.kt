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
    val renderer: Renderer
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
            }
        }

    val parts = mutableListOf<Part>()
    val placeholders = mutableListOf<Part>()
    var selectedPart: Part? = null
    var fuel = 0f
    var fuelCapacity = 100f
    var level = 1
    private var onLaunchListener: ((Boolean) -> Unit)? = null
    var onPowerUpCollectedListener: ((Float, Float) -> Unit)? = null

    var shipX: Float = 0f
    var shipY: Float = 0f
    var totalShipHeight: Float = 0f
    var maxPartHalfWidth: Float = 0f
    var mergedShipBitmap: Bitmap? = null

    val powerUps = mutableListOf<PowerUp>()
    val asteroids = mutableListOf<Asteroid>()
    private val powerUpSpawnRate = 1000L
    private val asteroidSpawnRate = 1500L
    private var lastPowerUpSpawnTime = System.currentTimeMillis()
    private var lastAsteroidSpawnTime = System.currentTimeMillis()

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f
    var statusBarHeight: Float = 0f

    var cockpitY: Float = 0f
    var fuelTankY: Float = 0f
    var engineY: Float = 0f

    private var mediaPlayer: MediaPlayer? = null

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

        fuel -= 0.1f
        Timber.d("Ship position: (x=$shipX, y=$shipY)")
        shipX = shipX.coerceIn(maxPartHalfWidth, this.screenWidth - maxPartHalfWidth)
        shipY = shipY.coerceIn(totalShipHeight / 2, this.screenHeight - totalShipHeight / 2)

        val currentTime = System.currentTimeMillis()
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
                    "power_up" -> fuel = (fuel + 10f).coerceAtMost(fuelCapacity)
                    "shield" -> {}
                    "speed" -> {}
                    "stealth" -> {}
                    "warp" -> {}
                    "star" -> {}
                }
                powerUpsToRemove.add(powerUp)
                Timber.d("Collected power-up, fuel increased to: $fuel")
                onPowerUpCollectedListener?.invoke(powerUp.x, powerUp.y)
                playPowerUpSound()
            }
        }
        for (asteroid in asteroids) {
            val asteroidRect = RectF(
                asteroid.x - 30f, asteroid.y - 30f,
                asteroid.x + 30f, asteroid.y + 30f
            )
            if (checkCollision(asteroidRect)) {
                fuel -= 5f
                asteroidsToRemove.add(asteroid)
                Timber.d("Hit asteroid, fuel decreased to: $fuel")
            }
        }
        powerUps.removeAll(powerUpsToRemove)
        asteroids.removeAll(asteroidsToRemove)

        powerUps.removeAll { it.isExpired(this.screenHeight) }
        asteroids.removeAll { it.isOffScreen(this.screenHeight) }
        Timber.d("After cleanup: ${powerUps.size} power-ups, ${asteroids.size} asteroids")

        if (fuel <= 0) {
            gameState = GameState.BUILD
            powerUps.clear()
            asteroids.clear()
        }
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

            // Clear parts after creating the merged bitmap
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
        Timber.d("moveShip called with direction=$direction (no action with dragging)")
    }

    fun stopShip() {
        Timber.d("stopShip called (no action with dragging)")
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
        Timber.d("Cockpit alignment: y=${cockpit.y}, target=$cockpitY, diff=${abs(cockpit.y - cockpitY)}")
        Timber.d("FuelTank alignment: y=${fuelTank.y}, target=$fuelTankY, diff=${abs(fuelTank.y - fuelTankY)}")
        Timber.d("Engine alignment: y=${engine.y}, target=$engineY, diff=${abs(engine.y - engineY)}")
        Timber.d("Build area check: maxAllowedY=$maxAllowedY, screenHeight=$screenHeight, statusBarHeight=$statusBarHeight, panelHeight=$PANEL_HEIGHT")
        sortedParts.forEach { part ->
            Timber.d("Part ${part.type} y=${part.y}, within build area=${part.y < maxAllowedY}")
        }

        return isValidOrder && isAlignedY && isWithinBuildArea
    }

    fun getSpaceworthinessFailureReason(): String {
        val sortedParts = parts.sortedBy { it.y }
        return when {
            parts.size != 3 -> "Need exactly 3 parts! Current parts: ${parts.size}"
            sortedParts[0].type != "cockpit" -> "Cockpit must be topmost! Top part: ${sortedParts[0].type}"
            sortedParts[1].type != "fuel_tank" -> "Fuel tank must be middle! Middle part: ${sortedParts[1].type}"
            sortedParts[2].type != "engine" -> "Engine must be bottom! Bottom part: ${sortedParts[2].type}"
            abs(sortedParts[0].y - cockpitY) > ALIGNMENT_THRESHOLD -> "Misaligned: Cockpit not at placeholder position! Cockpit y=${sortedParts[0].y}, placeholder y=$cockpitY"
            abs(sortedParts[1].y - fuelTankY) > ALIGNMENT_THRESHOLD -> "Misaligned: Fuel Tank not at placeholder position! Fuel Tank y=${sortedParts[1].y}, placeholder y=$fuelTankY"
            abs(sortedParts[2].y - engineY) > ALIGNMENT_THRESHOLD -> "Misaligned: Engine not at placeholder position! Engine y=${sortedParts[2].y}, placeholder y=$engineY"
            sortedParts.any { it.y >= screenHeight - PANEL_HEIGHT - statusBarHeight } -> {
                val maxAllowedY = screenHeight - PANEL_HEIGHT - statusBarHeight
                "Parts must be above the panel! Screen height=$screenHeight, panel height=$PANEL_HEIGHT, statusBarHeight=$statusBarHeight, maxAllowedY=$maxAllowedY"
            }
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
        val type = types.random()
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