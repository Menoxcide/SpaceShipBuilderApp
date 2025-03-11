package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.example.spaceshipbuilderapp.BuildConfig
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.HashSet
import kotlin.math.sin
import kotlin.random.Random
import javax.inject.Inject

class Renderer @Inject constructor(private val context: Context) {
    private val particleSystem by lazy { ParticleSystem(context) }
    private var animationFrame = 0f
    private val successParticles = CopyOnWriteArrayList<SuccessParticle>()
    private val stars = mutableListOf<Star>()
    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f

    private val powerUpBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.power_up) ?: throw IllegalStateException("Power-up bitmap not found")
    private val shieldBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.shield_icon) ?: throw IllegalStateException("Shield bitmap not found")
    private val speedBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.speed_icon) ?: throw IllegalStateException("Speed bitmap not found")
    private val stealthBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.stealth_icon) ?: throw IllegalStateException("Stealth bitmap not found")
    private val warpBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.warp_icon) ?: throw IllegalStateException("Warp bitmap not found")
    private val asteroidBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.asteroid) ?: throw IllegalStateException("Asteroid bitmap not found")

    data class Star(
        var x: Float,
        var y: Float,
        val size: Float,
        val brightness: Float
    )

    data class SuccessParticle(
        var x: Float,
        var y: Float,
        var speedX: Float,
        var speedY: Float,
        var life: Float,
        var size: Float
    ) {
        fun update() {
            x += speedX
            y += speedY
            life -= 0.03f
        }

        fun isDead() = life <= 0f
    }

    private val backgroundPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 2400f, "#1A0B2E".toColorInt(), "#4B0082".toColorInt(), Shader.TileMode.CLAMP)
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
        color = "#FFFFFF".toColorInt()
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
    }
    private val successParticlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.YELLOW
    }
    private val powerUpPaint = Paint().apply {
        isAntiAlias = true
        color = Color.GREEN
    }
    private val asteroidPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }

    val cockpitBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.cockpit) ?: throw IllegalStateException("Cockpit bitmap not found")
    val fuelTankBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.fuel_tank) ?: throw IllegalStateException("Fuel tank bitmap not found")
    val engineBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.engine) ?: throw IllegalStateException("Engine bitmap not found")

    public val cockpitPlaceholderBitmap = createPlaceholderBitmap(cockpitBitmap)
    public val fuelTankPlaceholderBitmap = createPlaceholderBitmap(fuelTankBitmap)
    public val enginePlaceholderBitmap = createPlaceholderBitmap(engineBitmap)

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
        canvas.drawRect(0f, -statusBarHeight, screenWidth, screenHeight, backgroundPaint)
        if (stars.isEmpty()) {
            repeat(20) {
                stars.add(
                    Star(
                        x = Random.nextFloat() * screenWidth,
                        y = Random.nextFloat() * (screenHeight + statusBarHeight) - statusBarHeight,
                        size = Random.nextFloat() * 2f + 1f,
                        brightness = Random.nextFloat()
                    )
                )
            }
        }
        stars.forEach { star ->
            star.y += 3f
            if (star.y > screenHeight + statusBarHeight) {
                star.y = -statusBarHeight
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

    fun drawSuccessAnimation(canvas: Canvas, parts: List<GameEngine.Part>) {
        val sortedParts = parts.sortedBy { it.y }
        if (sortedParts.isEmpty()) return

        val topY = sortedParts.first().y - (sortedParts.first().bitmap.height * sortedParts.first().scale / 2f)
        val bottomY = sortedParts.last().y + (sortedParts.last().bitmap.height * sortedParts.last().scale / 2f)
        val centerX = sortedParts.first().x

        if (animationFrame % 10 == 0f) {
            repeat(2) {
                val startY = Random.nextFloat() * (bottomY - topY) + topY
                val side = if (Random.nextBoolean()) -1 else 1
                val startX = centerX + (side * (sortedParts.first().bitmap.width * sortedParts.first().scale / 2f + 20f))
                successParticles.add(
                    SuccessParticle(
                        x = startX,
                        y = startY,
                        speedX = side * (Random.nextFloat() * 3f + 2f),
                        speedY = Random.nextFloat() * 2f - 1f,
                        life = 1f,
                        size = Random.nextFloat() * 5f + 3f
                    )
                )
            }
        }

        val particlesToRemove = HashSet<SuccessParticle>()
        successParticles.forEach { particle ->
            particle.update()
            successParticlePaint.alpha = (particle.life * 255).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size, successParticlePaint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        successParticles.removeAll(particlesToRemove)
    }

    fun drawShip(canvas: Canvas, shipParts: List<GameEngine.Part>, screenWidth: Float, screenHeight: Float, shipX: Float, shipY: Float) {
        if (shipParts.isEmpty()) return
        val totalHeight = shipParts.sumOf { (it.bitmap.height * it.scale).toDouble() }.toFloat()
        var currentY = shipY - totalHeight / 2f
        val centerX = shipX
        shipParts.sortedBy { it.y }.forEach { part ->
            val xOffset = (part.bitmap.width * part.scale / 2f)
            val yOffset = (part.bitmap.height * part.scale / 2f)
            canvas.withSave {
                withTranslation(centerX, currentY + yOffset) {
                    withRotation(part.rotation, xOffset, yOffset) {
                        scale(part.scale, part.scale, xOffset, yOffset)
                        drawBitmap(part.bitmap, -xOffset, -yOffset, null)
                    }
                }
            }
            if (part.type == "engine") {
                particleSystem.addPropulsionParticles(centerX, currentY + (part.bitmap.height * part.scale))
            }
            currentY += (part.bitmap.height * part.scale)
        }
        particleSystem.drawExhaustParticles(canvas)
    }

    fun drawPowerUps(canvas: Canvas, powerUps: List<GameEngine.PowerUp>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${powerUps.size} power-ups")
        powerUps.forEach { powerUp ->
            val adjustedY = powerUp.y + statusBarHeight
            if (BuildConfig.DEBUG) Timber.d("Drawing power-up at (x=${powerUp.x}, y=$adjustedY)")
            // Temporarily draw a green circle instead of bitmap to test visibility
            canvas.drawCircle(powerUp.x, adjustedY, 20f, powerUpPaint)
        }
    }

    fun drawAsteroids(canvas: Canvas, asteroids: List<GameEngine.Asteroid>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${asteroids.size} asteroids")
        asteroids.forEach { asteroid ->
            val adjustedY = asteroid.y + statusBarHeight
            if (BuildConfig.DEBUG) Timber.d("Drawing asteroid at (x=${asteroid.x}, y=$adjustedY)")
            // Temporarily draw a red circle instead of bitmap to test visibility
            canvas.drawCircle(asteroid.x, adjustedY, 20f, asteroidPaint)
        }
    }

    fun drawStats(canvas: Canvas, gameEngine: GameEngine, statusBarHeight: Float) {
        val textHeight = 50f
        val startY = 80f + statusBarHeight
        canvas.drawText("Level: ${gameEngine.level}", 40f, startY, graffitiPaint)
        canvas.drawText("Fuel: ${gameEngine.fuel.toInt()}/${gameEngine.fuelCapacity}", 40f, startY + textHeight * 1, graffitiPaint)
        canvas.drawText("Parts: ${gameEngine.parts.size}/3", 40f, startY + textHeight * 2, graffitiPaint)
    }

    fun clearParticles() {
        particleSystem.clearParticles()
        successParticles.clear()
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
        if (!asteroidBitmap.isRecycled) asteroidBitmap.recycle()
        particleSystem.onDestroy()
    }
}