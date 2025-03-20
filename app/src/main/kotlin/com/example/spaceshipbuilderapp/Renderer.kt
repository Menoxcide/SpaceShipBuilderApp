package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
    private val invincibilityBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.invincibility_icon)
        ?: throw IllegalStateException("Invincibility bitmap not found")
    private val enemyShipBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.enemy_ship)
        ?: throw IllegalStateException("Enemy ship bitmap not found")

    // Ship sets (each set has cockpit, fuel tank, and engine)
    private val shipSets: List<ShipSet> = listOf(
        ShipSet(
            cockpit = BitmapFactory.decodeResource(context.resources, R.drawable.cockpit)
                ?: throw IllegalStateException("Cockpit bitmap not found"),
            fuelTank = BitmapFactory.decodeResource(context.resources, R.drawable.fuel_tank)
                ?: throw IllegalStateException("Fuel tank bitmap not found"),
            engine = BitmapFactory.decodeResource(context.resources, R.drawable.engine)
                ?: throw IllegalStateException("Engine bitmap not found")
        ),
        ShipSet(
            cockpit = BitmapFactory.decodeResource(context.resources, R.drawable.cockpit_1)
                ?: throw IllegalStateException("Cockpit_1 bitmap not found"),
            fuelTank = BitmapFactory.decodeResource(context.resources, R.drawable.fuel_tank_1)
                ?: throw IllegalStateException("Fuel_tank_1 bitmap not found"),
            engine = BitmapFactory.decodeResource(context.resources, R.drawable.engine_1)
                ?: throw IllegalStateException("Engine_1 bitmap not found")
        ),
        ShipSet(
            cockpit = BitmapFactory.decodeResource(context.resources, R.drawable.cockpit_2)
                ?: throw IllegalStateException("Cockpit_2 bitmap not found"),
            fuelTank = BitmapFactory.decodeResource(context.resources, R.drawable.fuel_tank_2)
                ?: throw IllegalStateException("Fuel_tank_2 bitmap not found"),
            engine = BitmapFactory.decodeResource(context.resources, R.drawable.engine_2)
                ?: throw IllegalStateException("Engine_2 bitmap not found")
        )
    )

    // Current ship set bitmaps (updated based on selected ship set)
    private var selectedShipSet: Int = 0 // Default to the first ship set

    val cockpitBitmap: Bitmap get() = shipSets[selectedShipSet].cockpit
    val fuelTankBitmap: Bitmap get() = shipSets[selectedShipSet].fuelTank
    val engineBitmap: Bitmap get() = shipSets[selectedShipSet].engine

    val cockpitPlaceholderBitmap: Bitmap get() = createPlaceholderBitmap(cockpitBitmap)
    val fuelTankPlaceholderBitmap: Bitmap get() = createPlaceholderBitmap(fuelTankBitmap)
    val enginePlaceholderBitmap: Bitmap get() = createPlaceholderBitmap(engineBitmap)

    data class ShipSet(val cockpit: Bitmap, val fuelTank: Bitmap, val engine: Bitmap)

    data class Star(
        var x: Float,
        var y: Float,
        val size: Float,
        val brightness: Float,
        var phase: Float // For random left-right oscillation
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
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        style = Paint.Style.FILL
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    private val fuelBarPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLUE
        style = Paint.Style.FILL
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    private val barBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    private val distancePaint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.YELLOW
        textAlign = Paint.Align.LEFT
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val scorePaint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.CYAN
        textAlign = Paint.Align.LEFT
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val levelUpPaint = Paint().apply {
        isAntiAlias = true
        textSize = 100f
        color = Color.GREEN
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val longestDistancePaint = Paint().apply {
        isAntiAlias = true
        textSize = 100f
        color = Color.YELLOW
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val highestScorePaint = Paint().apply {
        isAntiAlias = true
        textSize = 100f
        color = Color.CYAN
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val highestLevelPaint = Paint().apply {
        isAntiAlias = true
        textSize = 100f
        color = Color.GREEN
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val starsCollectedPaint = Paint().apply {
        isAntiAlias = true
        textSize = 100f
        color = Color.MAGENTA
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val shipTintPaint = Paint().apply {
        isAntiAlias = true
    }
    private val projectilePaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
    }
    private val enemyProjectilePaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }
    private val scoreTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = 30f
        color = Color.GREEN
        textAlign = Paint.Align.CENTER
    }
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        setShadowLayer(8f, 0f, 0f, Color.RED)
    }

    private fun createPlaceholderBitmap(original: Bitmap): Bitmap {
        return createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                hologramPaint.alpha = 128
                drawBitmap(original, 0f, 0f, hologramPaint)
            }
        }
    }

    fun setShipSet(shipSet: Int) {
        selectedShipSet = shipSet
        Timber.d("Renderer ship set updated to: $selectedShipSet")
    }

    fun updateAnimationFrame() {
        animationFrame = (animationFrame + 1) % 360
    }

    fun drawBackground(canvas: Canvas, screenWidth: Float, screenHeight: Float, statusBarHeight: Float, level: Int = 1) {
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight

        val gradientIndex = (level - 1) / 10 % 5
        val gradients = listOf(
            Pair(Color.parseColor("#1A0B2E"), Color.parseColor("#4B0082")),
            Pair(Color.parseColor("#0A2E4B"), Color.parseColor("#008282")),
            Pair(Color.parseColor("#2E0A4B"), Color.parseColor("#82004B")),
            Pair(Color.parseColor("#4B2E0A"), Color.parseColor("#828200")),
            Pair(Color.parseColor("#0A4B2E"), Color.parseColor("#00824B"))
        )
        val (startColor, endColor) = gradients[gradientIndex]
        backgroundPaint.shader = LinearGradient(0f, 0f, 0f, screenHeight, startColor, endColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, backgroundPaint)

        if (stars.isEmpty()) {
            repeat(20) {
                stars.add(
                    Star(
                        x = Random.nextFloat() * screenWidth,
                        y = Random.nextFloat() * screenHeight,
                        size = Random.nextFloat() * 2f + 1f,
                        brightness = Random.nextFloat(),
                        phase = Random.nextFloat() * 2 * Math.PI.toFloat() // Random phase for oscillation
                    )
                )
            }
        }
        stars.forEach { star ->
            star.y += 1f // Move stars downward slowly
            // Add slow left-right oscillation using sine wave with random phase
            star.x += sin((System.currentTimeMillis() / 1000f + star.phase).toDouble()).toFloat() * 0.5f
            if (star.y > screenHeight) {
                star.y = 0f
                star.x = Random.nextFloat() * screenWidth
                star.phase = Random.nextFloat() * 2 * Math.PI.toFloat() // Reset phase for variety
            }
            if (star.x < 0f) star.x = screenWidth
            if (star.x > screenWidth) star.x = 0f
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
        selectedShipSet = gameEngine.selectedShipSet // Update the selected ship set

        when (gameEngine.shipColor) {
            "red" -> shipTintPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(1.5f, 0.5f, 0.5f, 1f) })
            "blue" -> shipTintPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.5f, 0.5f, 1.5f, 1f) })
            "green" -> shipTintPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.5f, 1.5f, 0.5f, 1f) })
            else -> shipTintPaint.colorFilter = null
        }

        if (gameState == GameState.FLIGHT && mergedShipBitmap != null) {
            val x = shipX - mergedShipBitmap.width / 2f
            val y = shipY - mergedShipBitmap.height / 2f

            // Draw the ship
            canvas.drawBitmap(mergedShipBitmap, x, y, shipTintPaint)

            // Draw pulsating red outline if glow is active
            val currentTime = System.currentTimeMillis()
            if (currentTime - gameEngine.glowStartTime <= gameEngine.glowDuration) {
                val elapsed = (currentTime - gameEngine.glowStartTime).toFloat() / gameEngine.glowDuration
                val pulse = (sin(elapsed * 2 * Math.PI.toFloat() * 3) + 1) / 2 // Pulsate 3 times
                glowPaint.alpha = (pulse * 255).toInt()
                val glowRect = RectF(
                    x - 2f, y - 2f,
                    x + mergedShipBitmap.width + 2f, y + mergedShipBitmap.height + 2f
                )
                canvas.drawRect(glowRect, glowPaint)
            }

            val centerX = shipX
            val centerY = shipY
            val powerUpSprite = when {
                gameEngine.shieldActive -> shieldBitmap
                gameEngine.speedBoostActive -> speedBitmap
                gameEngine.stealthActive -> stealthBitmap
                gameEngine.invincibilityActive -> invincibilityBitmap
                else -> null
            }
            powerUpSprite?.let {
                val spriteX = centerX - it.width / 2f
                val spriteY = centerY - it.height / 2f
                canvas.drawBitmap(it, spriteX, spriteY, powerUpPaint)
            }

            particleSystem.addPropulsionParticles(shipX, y + mergedShipBitmap.height, gameEngine.speedBoostActive)
            particleSystem.drawExhaustParticles(canvas)
            particleSystem.drawCollectionParticles(canvas)
            particleSystem.drawCollisionParticles(canvas)
            particleSystem.drawDamageTextParticles(canvas)
            particleSystem.drawPowerUpTextParticles(canvas)
            particleSystem.drawPowerUpSpriteParticles(canvas)
            particleSystem.drawExplosionParticles(canvas)
            particleSystem.drawScoreTextParticles(canvas)

            // Draw sleek HP and Fuel bars
            val barWidth = 8f
            val offset = 2f
            val fuelTankHeight = fuelTankBitmap.height.toFloat()
            val fuelTankTop = y + cockpitBitmap.height
            val fuelTankBottom = fuelTankTop + fuelTankHeight
            val hpBarX = x - barWidth - offset
            val fuelBarX = x + mergedShipBitmap.width + offset
            val hpFraction = gameEngine.hp / gameEngine.maxHp
            val fuelFraction = gameEngine.fuel / gameEngine.fuelCapacity
            val hpFilledHeight = fuelTankHeight * hpFraction
            val fuelFilledHeight = fuelTankHeight * fuelFraction

            // Draw borders
            canvas.drawRect(hpBarX, fuelTankTop, hpBarX + barWidth, fuelTankBottom, barBorderPaint)
            canvas.drawRect(fuelBarX, fuelTankTop, fuelBarX + barWidth, fuelTankBottom, barBorderPaint)

            // Draw filled parts (from bottom up)
            canvas.drawRect(hpBarX, fuelTankBottom - hpFilledHeight, hpBarX + barWidth, fuelTankBottom, hpBarPaint)
            canvas.drawRect(fuelBarX, fuelTankBottom - fuelFilledHeight, fuelBarX + barWidth, fuelTankBottom, fuelBarPaint)
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
                            drawBitmap(part.bitmap, -xOffset, -yOffset, shipTintPaint)
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
                "invincibility" -> invincibilityBitmap
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
            val x = asteroid.x - asteroid.size
            val scaledBitmap = Bitmap.createScaledBitmap(
                asteroidBitmap,
                (asteroid.size * 2).toInt(),
                (asteroid.size * 2).toInt(),
                true
            )
            canvas.withSave {
                translate(x + asteroid.size, y)
                rotate(asteroid.rotation * (180f / Math.PI.toFloat()))
                drawBitmap(scaledBitmap, -asteroid.size, -asteroid.size, asteroidPaint)
            }
            if (BuildConfig.DEBUG) Timber.d("Drawing asteroid at (x=${asteroid.x}, y=$y) with size=${asteroid.size}, rotation=${asteroid.rotation}")
        }
    }

    fun drawProjectiles(canvas: Canvas, projectiles: List<GameEngine.Projectile>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${projectiles.size} projectiles")
        projectiles.forEach { projectile ->
            canvas.drawCircle(projectile.x, projectile.y, GameEngine.PROJECTILE_SIZE, projectilePaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing projectile at (x=${projectile.x}, y=${projectile.y})")
        }
    }

    fun drawEnemyShips(canvas: Canvas, enemyShips: List<GameEngine.EnemyShip>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${enemyShips.size} enemy ships")
        enemyShips.forEach { enemy ->
            val scaledBitmap = Bitmap.createScaledBitmap(enemyShipBitmap, 100, 100, true)
            canvas.drawBitmap(scaledBitmap, enemy.x - 50f, enemy.y - 50f, asteroidPaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing enemy ship at (x=${enemy.x}, y=${enemy.y})")
        }
    }

    fun drawEnemyProjectiles(canvas: Canvas, enemyProjectiles: List<GameEngine.Projectile>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${enemyProjectiles.size} enemy projectiles")
        enemyProjectiles.forEach { projectile ->
            canvas.drawCircle(projectile.x, projectile.y, GameEngine.PROJECTILE_SIZE, enemyProjectilePaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing enemy projectile at (x=${projectile.x}, y=${projectile.y})")
        }
    }

    fun drawStats(canvas: Canvas, gameEngine: GameEngine, statusBarHeight: Float) {
        val currentTime = System.currentTimeMillis()
        val textHeight = 100f // Height of each text line, matching levelUpPaint
        val startY = statusBarHeight + 150f // Move down further to avoid notification bar

        if (gameEngine.gameState == GameState.FLIGHT) {
            // Calculate text bounds for better centering
            val distanceText = "Distance: ${gameEngine.distanceTraveled.toInt()} units"
            val scoreText = "Score: ${gameEngine.currentScore}"
            val levelText = "Level: ${gameEngine.level}"

            val distanceBounds = Rect()
            val scoreBounds = Rect()
            val levelBounds = Rect()

            distancePaint.getTextBounds(distanceText, 0, distanceText.length, distanceBounds)
            scorePaint.getTextBounds(scoreText, 0, scoreText.length, scoreBounds)
            graffitiPaint.getTextBounds(levelText, 0, levelText.length, levelBounds)

            val totalWidth = maxOf(distanceBounds.width(), scoreBounds.width(), levelBounds.width())
            val startX = (screenWidth - totalWidth) / 2f

            canvas.drawText(distanceText, startX, startY, distancePaint)
            canvas.drawText(scoreText, startX, startY + textHeight, scorePaint)
            canvas.drawText(levelText, startX, startY + 2 * textHeight, graffitiPaint)

            if (gameEngine.levelUpAnimationStartTime > 0L && currentTime - gameEngine.levelUpAnimationStartTime <= GameEngine.LEVEL_UP_ANIMATION_DURATION) {
                val levelTextDisplay = "Level ${gameEngine.level}"
                canvas.drawText(levelTextDisplay, screenWidth / 2f, screenHeight / 2f, levelUpPaint)
            } else {
                gameEngine.levelUpAnimationStartTime = 0L
            }
        } else if (gameEngine.gameState == GameState.BUILD) {
            // Display longestDistanceTraveled, highestScore, highestLevel, and starsCollected in build mode
            val longestDistanceText = "Longest Distance: ${gameEngine.longestDistanceTraveled.toInt()}"
            val highestScoreText = "Highest Score: ${gameEngine.highestScore}"
            val highestLevelText = "Highest Level: ${gameEngine.highestLevel}"
            val starsCollectedText = "Stars Collected: ${gameEngine.starsCollected}"

            canvas.drawText(longestDistanceText, screenWidth / 2f, startY, longestDistancePaint)
            canvas.drawText(highestScoreText, screenWidth / 2f, startY + textHeight, highestScorePaint)
            canvas.drawText(highestLevelText, screenWidth / 2f, startY + 2 * textHeight, highestLevelPaint)
            canvas.drawText(starsCollectedText, screenWidth / 2f, startY + 3 * textHeight, starsCollectedPaint)
        }
    }

    fun addScoreTextParticle(x: Float, y: Float, text: String) {
        particleSystem.addScoreTextParticle(x, y, text)
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
        if (!invincibilityBitmap.isRecycled) invincibilityBitmap.recycle()
        if (!enemyShipBitmap.isRecycled) enemyShipBitmap.recycle()
        particleSystem.onDestroy()
    }
}