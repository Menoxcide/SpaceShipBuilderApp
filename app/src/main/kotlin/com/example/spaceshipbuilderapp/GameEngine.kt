package com.example.spaceshipbuilderapp

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

enum class GameState { BUILD, FLIGHT }

class GameEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: Renderer
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SpaceshipBuilder", Context.MODE_PRIVATE)
    var gameState = GameState.BUILD
    val parts = mutableListOf<Part>()
    val placeholders = mutableListOf<Part>()
    var selectedPart: Part? = null
    var fuel = 0f
    var fuelCapacity = 100f
    var level = 1
    private var onLaunchListener: ((Boolean) -> Unit)? = null

    var shipX: Float = 0f
    var shipY: Float = 0f
    private var shipVelocityX: Float = 0f
    private var shipVelocityY: Float = 0f
    private val moveSpeed = 5f
    private val shipHalfWidth = renderer.cockpitBitmap.width * 0.5f / 2f
    private val shipHalfHeight = renderer.cockpitBitmap.height * 0.5f / 2f

    val powerUps = mutableListOf<PowerUp>()
    val asteroids = mutableListOf<Asteroid>()
    private val powerUpSpawnRate = 1000L
    private val asteroidSpawnRate = 1500L
    private var lastPowerUpSpawnTime = System.currentTimeMillis()
    private var lastAsteroidSpawnTime = System.currentTimeMillis()

    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f

    private var cockpitY: Float = 0f
    private var fuelTankY: Float = 0f
    private var engineY: Float = 0f

    companion object {
        const val ALIGNMENT_THRESHOLD = 50f // Relaxed from 20f to 50f
        const val PANEL_HEIGHT = 150f
    }

    init {
        loadPersistentData()
    }

    fun setScreenDimensions(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        Timber.d("GameEngine screen dimensions set: width=$screenWidth, height=$screenHeight")
    }

    fun initializePlaceholders(
        screenWidth: Float,
        screenHeight: Float,
        cockpitPlaceholderBitmap: Bitmap,
        fuelTankPlaceholderBitmap: Bitmap,
        enginePlaceholderBitmap: Bitmap
    ) {
        val cockpitHeight = renderer.cockpitBitmap.height.toFloat()
        val fuelTankHeight = renderer.fuelTankBitmap.height.toFloat()
        val engineHeight = renderer.engineBitmap.height.toFloat()

        val totalHeight = cockpitHeight + fuelTankHeight + engineHeight
        cockpitY = screenHeight / 2
        fuelTankY = (cockpitY + cockpitHeight) - 12
        engineY = (fuelTankY + fuelTankHeight) - 30

        placeholders.clear()
        placeholders.add(Part("cockpit", cockpitPlaceholderBitmap, screenWidth / 2, cockpitY, 0f))
        placeholders.add(Part("fuel_tank", fuelTankPlaceholderBitmap, screenWidth / 2, fuelTankY, 0f))
        placeholders.add(Part("engine", enginePlaceholderBitmap, screenWidth / 2, engineY, 0f))
        Timber.d("Cockpit Height: $cockpitHeight -> $cockpitY")
        Timber.d("Fuel Tank Height: $fuelTankHeight -> $fuelTankY")
        Timber.d("Engine Height: $engineHeight -> $engineY")
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
        shipX += shipVelocityX
        shipY += shipVelocityY
        Timber.d("Ship position updated: (x=$shipX, y=$shipY)")
        shipX = shipX.coerceIn(0f + shipHalfWidth, this.screenWidth - shipHalfWidth)
        shipY = shipY.coerceIn(0f + shipHalfHeight, this.screenHeight - shipHalfHeight)

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

        powerUps.forEach {
            it.update(this.screenHeight)
            Timber.d("Power-up position: (x=${it.x}, y=${it.y})")
        }
        asteroids.forEach {
            it.update(this.screenHeight)
            Timber.d("Asteroid position: (x=${it.x}, y=${it.y})")
        }

        val powerUpsToRemove = mutableListOf<PowerUp>()
        val asteroidsToRemove = mutableListOf<Asteroid>()
        for (powerUp in powerUps) {
            val powerUpRect = android.graphics.RectF(
                powerUp.x - 20f, powerUp.y - 20f,
                powerUp.x + 20f, powerUp.y + 20f
            )
            if (checkCollision(powerUpRect)) {
                fuel = (fuel + 10f).coerceAtMost(fuelCapacity)
                powerUpsToRemove.add(powerUp)
                Timber.d("Collected power-up, fuel increased to: $fuel")
            }
        }
        for (asteroid in asteroids) {
            val asteroidRect = android.graphics.RectF(
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
            onLaunchListener?.invoke(false)
            powerUps.clear()
            asteroids.clear()
            shipVelocityX = 0f
            shipVelocityY = 0f
        }
    }

    private fun checkCollision(rect: android.graphics.RectF): Boolean {
        val shipRect = android.graphics.RectF(
            shipX - shipHalfWidth, shipY - shipHalfHeight,
            shipX + shipHalfWidth, shipY + shipHalfHeight
        )
        return shipRect.intersect(rect)
    }

    fun launchShip(screenWidth: Float, screenHeight: Float): Boolean {
        if (isShipSpaceworthy(screenHeight)) {
            gameState = GameState.FLIGHT
            fuel = 50f
            shipX = screenWidth / 2
            shipY = screenHeight / 2
            this.screenWidth = screenWidth
            this.screenHeight = screenHeight
            onLaunchListener?.invoke(true)
            Timber.d("Ship launched successfully with fuel: $fuel")
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
            0 -> shipVelocityY = -moveSpeed // Up
            1 -> shipVelocityY = moveSpeed // Down
            2 -> shipVelocityX = -moveSpeed // Left
            3 -> shipVelocityX = moveSpeed // Right
        }
        Timber.d("Moving ship: velocityX=$shipVelocityX, velocityY=$shipVelocityY")
    }

    fun stopShip() {
        shipVelocityX = 0f
        shipVelocityY = 0f
        Timber.d("Stopped ship: velocityX=$shipVelocityX, velocityY=$shipVelocityY")
    }

    public fun isShipSpaceworthy(screenHeight: Float): Boolean {
        if (parts.size != 3) return false
        val sortedParts = parts.sortedBy { it.y }
        val cockpit = sortedParts[0]
        val fuelTank = sortedParts[1]
        val engine = sortedParts[2]
        val isValidOrder = cockpit.type == "cockpit" && fuelTank.type == "fuel_tank" && engine.type == "engine"
        val isAlignedY = abs(cockpit.y - cockpitY) < ALIGNMENT_THRESHOLD &&
                abs(fuelTank.y - fuelTankY) < ALIGNMENT_THRESHOLD &&
                abs(engine.y - engineY) < ALIGNMENT_THRESHOLD
        val isWithinBuildArea = sortedParts.all { it.y < screenHeight - PANEL_HEIGHT }
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
            abs(sortedParts[0].y - cockpitY) > ALIGNMENT_THRESHOLD -> "Misaligned: Cockpit not at placeholder position! Cockpit y=${sortedParts[0].y}, placeholder y=$cockpitY, threshold=$ALIGNMENT_THRESHOLD"
            abs(sortedParts[1].y - fuelTankY) > ALIGNMENT_THRESHOLD -> "Misaligned: Fuel Tank not at placeholder position! Fuel Tank y=${sortedParts[1].y}, placeholder y=$fuelTankY, threshold=$ALIGNMENT_THRESHOLD"
            abs(sortedParts[2].y - engineY) > ALIGNMENT_THRESHOLD -> "Misaligned: Engine not at placeholder position! Engine y=${sortedParts[2].y}, placeholder y=$engineY, threshold=$ALIGNMENT_THRESHOLD"
            sortedParts.any { it.y >= screenHeight - PANEL_HEIGHT } -> "Parts must be above the panel! Screen height=$screenHeight, panel height=$PANEL_HEIGHT"
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

    private fun loadPersistentData() {
        // Highscore logic moved to HighscoreManager
    }

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        onLaunchListener = listener
    }

    private fun spawnPowerUp(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = -20f
        val types = listOf("power_up", "shield", "speed", "stealth", "warp")
        val type = types.random()
        powerUps.add(PowerUp(x, y, type))
    }

    private fun spawnAsteroid(screenWidth: Float) {
        val x = Random.nextFloat() * screenWidth
        val y = -20f
        asteroids.add(Asteroid(x, y))
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