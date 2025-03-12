package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sin
import kotlin.random.Random

class Renderer @Inject constructor(
    private val context: Context,
    val particleSystem: ParticleSystem
) {
    private val stars = mutableListOf<Star>()
    private var animationFrame = 0f
    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f

    private val powerUpBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.power_up)
        ?: throw IllegalStateException("Power-up bitmap not found")
    private val shieldBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.shield_icon)
        ?: throw IllegalStateException("Shield bitmap not found")
    private val speedBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.speed_icon)
        ?: throw IllegalStateException("Speed bitmap not found")
    private val stealthBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.stealth_icon)
        ?: throw IllegalStateException("Stealth bitmap not found")
    private val warpBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.warp_icon)
        ?: throw IllegalStateException("Warp bitmap not found")
    private val starBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.star)
        ?: throw IllegalStateException("Star bitmap not found")
    private val asteroidBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.asteroid)
        ?: throw IllegalStateException("Asteroid bitmap not found")
    private val invincibilityBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.invincibility_icon) // Assume you have this resource
        ?: throw IllegalStateException("Invincibility bitmap not found")

    data class Star(
        var x: Float,
        var y: Float,
        val size: Float,
        val brightness: Float
    )

    private val backgroundPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 2400f, Color.parseColor("#1A0B2E"), Color.parseColor("#4B0082"), Shader.TileMode.CLAMP)
    }
    private val starPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
    }
    private val hologramPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 0)
    }
    private val graffitiPaint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
    }
    private val powerUpPaint = Paint().apply {
        isAntiAlias = true
    }
    private val asteroidPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }
    private val hpBarPaint = Paint().apply {
        isAntiAlias = true
        color = Color.GREEN
    }
    private val fuelBarPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLUE
    }
    private val barBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val invincibilityAuraPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    // Theme support
    var currentTheme = "dark"

    fun setTheme(theme: String) {
        currentTheme = theme
        updatePaints()
    }

    private fun updatePaints() {
        when (currentTheme) {
            "dark" -> {
                backgroundPaint.shader = LinearGradient(0f, 0f, 0f, 2400f, Color.parseColor("#1A0B2E"), Color.parseColor("#4B0082"), Shader.TileMode.CLAMP)
                graffitiPaint.color = Color.parseColor("#FFFFFF")
            }
            "neon" -> {
                backgroundPaint.shader = LinearGradient(0f, 0f, 0f, 2400f, Color.parseColor("#00FF00"), Color.parseColor("#FF00FF"), Shader.TileMode.CLAMP)
                graffitiPaint.color = Color.parseColor("#000000")
            }
        }
    }

    val cockpitBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.cockpit)
        ?: throw IllegalStateException("Cockpit bitmap not found")
    val fuelTankBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.fuel_tank)
        ?: throw IllegalStateException("Fuel tank bitmap not found")
    val engineBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.engine)
        ?: throw IllegalStateException("Engine bitmap not found")

    val cockpitPlaceholderBitmap = createPlaceholderBitmap(cockpitBitmap)
    val fuelTankPlaceholderBitmap = createPlaceholderBitmap(fuelTankBitmap)
    val enginePlaceholderBitmap = createPlaceholderBitmap(engineBitmap)

    private fun createPlaceholderBitmap(original: Bitmap): Bitmap {
        return createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                hologramPaint.alpha = 128
                drawBitmap(original, 0f, 0f, hologramPaint)
            }
        }
    }

    fun updateAnimationFrame() {
        animationFrame = (animationFrame + 1) % 360
    }

    fun drawBackground(canvas: Canvas, screenWidth: Float, screenHeight: Float, statusBarHeight: Float) {
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, backgroundPaint)
        if (stars.isEmpty()) {
            repeat(20) {
                stars.add(
                    Star(
                        x = Random.nextFloat() * screenWidth,
                        y = Random.nextFloat() * screenHeight,
                        size = Random.nextFloat() * 2f + 1f,
                        brightness = Random.nextFloat()
                    )
                )
            }
        }
        stars.forEach { star ->
            star.y += 3f
            if (star.y > screenHeight) {
                star.y = 0f
                star.x = Random.nextFloat() * screenWidth
            }
            val brightness = (sin(animationFrame * 0.05f + star.brightness * Math.PI.toFloat()) * 0.5f + 0.5f).coerceIn(0f, 1f)
            starPaint.alpha = (brightness * 255).toInt()
            canvas.drawCircle(star.x, star.y, star.size, starPaint)
        }
    }

    fun drawParts(canvas: Canvas, parts: List<GameEngine.Part>) {
        parts.forEach { part ->
            val x = part.x - (part.bitmap.width * part.scale / 2f)
            val y = part.y - (part.bitmap.height * part.scale / 2f)
            canvas.withSave {
                withTranslation(part.x, part.y) {
                    withRotation(part.rotation, part.bitmap.width * part.scale / 2f, part.bitmap.height * part.scale / 2f) {
                        scale(part.scale, part.scale, part.bitmap.width / 2f, part.bitmap.height / 2f)
                        drawBitmap(part.bitmap, -part.bitmap.width / 2f, -part.bitmap.height / 2f, null)
                    }
                }
            }
        }
    }

    fun drawPlaceholders(canvas: Canvas, placeholders: List<GameEngine.Part>) {
        placeholders.forEach { part ->
            val x = part.x - (part.bitmap.width * part.scale / 2f)
            val y = part.y - (part.bitmap.height * part.scale / 2f)
            canvas.withSave {
                scale(part.scale, part.scale, part.x, part.y)
                drawBitmap(part.bitmap, x, y, hologramPaint)
            }
        }
    }

    fun drawShip(
        canvas: Canvas,
        gameEngine: GameEngine,
        shipParts: List<GameEngine.Part>,
        screenWidth: Float,
        screenHeight: Float,
        shipX: Float,
        shipY: Float,
        gameState: GameState,
        mergedShipBitmap: Bitmap?,
        placeholders: List<GameEngine.Part>
    ) {
        if (gameState == GameState.FLIGHT && mergedShipBitmap != null) {
            val x = shipX - mergedShipBitmap.width / 2f
            val y = shipY - mergedShipBitmap.height / 2f
            canvas.drawBitmap(mergedShipBitmap, x, y, null)
            if (gameEngine.invincibilityActive) {
                canvas.drawCircle(
                    shipX,
                    shipY,
                    mergedShipBitmap.width / 2f + 10f,
                    invincibilityAuraPaint
                )
            }
            particleSystem.addPropulsionParticles(shipX, y + mergedShipBitmap.height / 2f)
            particleSystem.drawExhaustParticles(canvas)
            particleSystem.drawCollectionParticles(canvas)
            particleSystem.drawCollisionParticles(canvas)
            particleSystem.drawDamageTextParticles(canvas)
            particleSystem.drawAuraParticles(canvas) // Draw aura effect

            val barWidth = mergedShipBitmap.width * 0.8f
            val barHeight = 10f
            val barX = shipX - barWidth / 2f
            val hpBarY = y - barHeight - 15f
            val fuelBarY = hpBarY + barHeight + 5f
            val hpFraction = gameEngine.hp / gameEngine.maxHp
            val fuelFraction = gameEngine.fuel / gameEngine.fuelCapacity
            val hpBarFilledWidth = barWidth * hpFraction
            val fuelBarFilledWidth = barWidth * fuelFraction

            canvas.drawRect(barX, hpBarY, barX + barWidth, hpBarY + barHeight, barBorderPaint)
            canvas.drawRect(barX, hpBarY, barX + hpBarFilledWidth, hpBarY + barHeight, hpBarPaint)
            canvas.drawRect(barX, fuelBarY, barX + barWidth, fuelBarY + barHeight, barBorderPaint)
            canvas.drawRect(barX, fuelBarY, barX + fuelBarFilledWidth, fuelBarY + barHeight, fuelBarPaint)
        } else if (gameState == GameState.BUILD && shipParts.isNotEmpty()) {
            val placeholderPositions = placeholders.associate { it.type to it.y }
            shipParts.sortedBy { it.y }.forEach { part ->
                val targetY = placeholderPositions[part.type] ?: part.y
                val xOffset = (part.bitmap.width * part.scale / 2f)
                val yOffset = (part.bitmap.height * part.scale / 2f)
                canvas.withSave {
                    withTranslation(shipX, targetY) {
                        withRotation(part.rotation, xOffset, yOffset) {
                            scale(part.scale, part.scale, xOffset, yOffset)
                            drawBitmap(part.bitmap, -xOffset, -yOffset, null)
                        }
                    }
                }
                if (part.type == "engine") {
                    particleSystem.addPropulsionParticles(shipX, targetY + (part.bitmap.height * part.scale))
                }
            }
            particleSystem.drawCollectionParticles(canvas)
            Timber.d("Drawing ship in BUILD mode with ${shipParts.size} parts")
        }
    }

    fun drawPowerUps(canvas: Canvas, powerUps: List<GameEngine.PowerUp>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${powerUps.size} power-ups")
        powerUps.forEach { powerUp ->
            val y = powerUp.y
            val bitmap = when (powerUp.type) {
                "shield" -> shieldBitmap
                "speed" -> speedBitmap
                "power_up" -> powerUpBitmap
                "stealth" -> stealthBitmap
                "warp" -> warpBitmap
                "star" -> starBitmap
                "invincibility" -> invincibilityBitmap // Use invincibility bitmap
                else -> powerUpBitmap
            }
            val x = powerUp.x - bitmap.width / 2f
            canvas.drawBitmap(bitmap, x, y - bitmap.height / 2f, powerUpPaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing power-up at (x=${powerUp.x}, y=$y)")
        }
    }

    fun drawAsteroids(canvas: Canvas, asteroids: List<GameEngine.Asteroid>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${asteroids.size} asteroids")
        asteroids.forEach { asteroid ->
            val y = asteroid.y
            val x = asteroid.x - asteroid.size // Use asteroid size for positioning (center-based)
            // Scale asteroid bitmap based on size (big asteroids are larger)
            val scaledBitmap = Bitmap.createScaledBitmap(
                asteroidBitmap,
                (asteroid.size * 2).toInt(), // Double the size for width and height
                (asteroid.size * 2).toInt(),
                true
            )
            canvas.drawBitmap(scaledBitmap, x, y - asteroid.size, asteroidPaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing asteroid at (x=${asteroid.x}, y=$y) with size=${asteroid.size}")
        }
    }

    fun drawStats(canvas: Canvas, gameEngine: GameEngine, statusBarHeight: Float) {
        val textHeight = 50f
        val startY = 80f + statusBarHeight
        if (gameEngine.gameState == GameState.FLIGHT) {
            canvas.drawText("Level: ${gameEngine.level}", 40f, startY, graffitiPaint) // Show level
            canvas.drawText("Score: ${gameEngine.currentScore}", 40f, startY + textHeight, graffitiPaint)
        } else {
            canvas.drawText("Level: ${gameEngine.level}", 40f, startY, graffitiPaint)
            canvas.drawText("Fuel: ${gameEngine.fuel.toInt()}/${gameEngine.fuelCapacity}", 40f, startY + textHeight, graffitiPaint)
            canvas.drawText("Parts: ${gameEngine.parts.size}/3", 40f, startY + textHeight * 2, graffitiPaint)
        }
    }

    fun clearParticles() {
        particleSystem.clearParticles()
    }

    fun onDestroy() {
        if (!cockpitPlaceholderBitmap.isRecycled) cockpitPlaceholderBitmap.recycle()
        if (!fuelTankPlaceholderBitmap.isRecycled) fuelTankPlaceholderBitmap.recycle()
        if (!enginePlaceholderBitmap.isRecycled) enginePlaceholderBitmap.recycle()
        if (!powerUpBitmap.isRecycled) powerUpBitmap.recycle()
        if (!shieldBitmap.isRecycled) shieldBitmap.recycle()
        if (!speedBitmap.isRecycled) speedBitmap.recycle()
        if (!stealthBitmap.isRecycled) stealthBitmap.recycle()
        if (!warpBitmap.isRecycled) warpBitmap.recycle()
        if (!starBitmap.isRecycled) starBitmap.recycle()
        if (!asteroidBitmap.isRecycled) asteroidBitmap.recycle()
        if (!invincibilityBitmap.isRecycled) invincibilityBitmap.recycle() // Recycle invincibility bitmap
        particleSystem.onDestroy()
    }
}