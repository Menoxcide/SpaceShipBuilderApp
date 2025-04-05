package com.example.spaceshipbuilderapp

import android.graphics.*
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sin

class ShipRenderer @Inject constructor(
    private val bitmapManager: BitmapManager,
    private val particleSystem: ParticleSystem
) {
    private val shipTintPaint = Paint().apply { isAntiAlias = true }
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        setShadowLayer(8f, 0f, 0f, Color.RED)
    }
    private val powerUpPaint = Paint().apply { isAntiAlias = true }
    private val missileIndicatorPaint = Paint().apply { isAntiAlias = true; color = Color.YELLOW }
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

    fun drawShip(
        canvas: Canvas,
        gameEngine: GameEngine,
        shipParts: List<Part>,
        screenWidth: Float,
        screenHeight: Float,
        shipX: Float,
        shipY: Float,
        gameState: GameState,
        mergedShipBitmap: Bitmap?,
        placeholders: List<Part>
    ) {
        val shipSet = bitmapManager.getShipSet(gameEngine.selectedShipSet)

        when (gameEngine.shipColor) {
            "red" -> shipTintPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(1.5f, 0.5f, 0.5f, 1f) })
            "blue" -> shipTintPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.5f, 0.5f, 1.5f, 1f) })
            "green" -> shipTintPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.5f, 1.5f, 0.5f, 1f) })
            else -> shipTintPaint.colorFilter = null
        }

        if (gameState == GameState.FLIGHT && mergedShipBitmap != null && !mergedShipBitmap.isRecycled) {
            val x = shipX - mergedShipBitmap.width / 2f
            val y = shipY - mergedShipBitmap.height / 2f

            canvas.drawBitmap(mergedShipBitmap, x, y, shipTintPaint)

            val currentTime = System.currentTimeMillis()
            if (currentTime - gameEngine.glowStartTime <= gameEngine.glowDuration) {
                val elapsed = (currentTime - gameEngine.glowStartTime).toFloat() / gameEngine.glowDuration
                val pulse = (sin(elapsed * 2 * Math.PI.toFloat() * 3) + 1) / 2
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
                gameEngine.shieldActive -> bitmapManager.getBitmap(R.drawable.shield_icon, "Shield")
                gameEngine.speedBoostActive -> bitmapManager.getBitmap(R.drawable.speed_icon, "Speed")
                gameEngine.stealthActive -> bitmapManager.getBitmap(R.drawable.stealth_icon, "Stealth")
                gameEngine.invincibilityActive -> bitmapManager.getBitmap(R.drawable.invincibility_icon, "Invincibility")
                else -> null
            }
            powerUpSprite?.let {
                val spriteX = centerX - it.width / 2f
                val spriteY = centerY - it.height / 2f
                canvas.drawBitmap(it, spriteX, spriteY, powerUpPaint)
            }

            val totalMissiles = gameEngine.missileCount
            val indicatorSize = 10f
            val indicatorSpacing = 5f
            val totalWidth = (indicatorSize + indicatorSpacing) * totalMissiles - indicatorSpacing
            val indicatorXStart = shipX - totalWidth / 2f
            val indicatorY = y - 20f
            for (i in 0 until totalMissiles) {
                missileIndicatorPaint.alpha = if (i < gameEngine.missileCount) 255 else 50
                canvas.drawCircle(
                    indicatorXStart + i * (indicatorSize + indicatorSpacing) + indicatorSize / 2f,
                    indicatorY,
                    indicatorSize / 2f,
                    missileIndicatorPaint
                )
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
            particleSystem.drawMissileExhaustParticles(canvas)
            particleSystem.drawSparkleParticles(canvas)

            val barWidth = 8f
            val offset = 2f
            val fuelTankHeight = shipSet.fuelTank.height.toFloat()
            val fuelTankTop = y + shipSet.cockpit.height
            val fuelTankBottom = fuelTankTop + fuelTankHeight
            val hpBarX = x - barWidth - offset
            val fuelBarX = x + mergedShipBitmap.width + offset
            val hpFraction = gameEngine.hp / gameEngine.maxHp
            val fuelFraction = gameEngine.fuel / gameEngine.fuelCapacity
            val hpFilledHeight = fuelTankHeight * hpFraction
            val fuelFilledHeight = fuelTankHeight * fuelFraction

            canvas.drawRect(hpBarX, fuelTankTop, hpBarX + barWidth, fuelTankBottom, barBorderPaint)
            canvas.drawRect(fuelBarX, fuelTankTop, fuelBarX + barWidth, fuelTankBottom, barBorderPaint)
            canvas.drawRect(hpBarX, fuelTankBottom - hpFilledHeight, hpBarX + barWidth, fuelTankBottom, hpBarPaint)
            canvas.drawRect(fuelBarX, fuelTankBottom - fuelFilledHeight, fuelBarX + barWidth, fuelTankBottom, fuelBarPaint)
        } else if (gameState == GameState.BUILD && shipParts.isNotEmpty()) {
            val placeholderPositions = placeholders.associate { it.type to it.y }
            shipParts.sortedBy { it.y }.forEach { part ->
                val targetY = placeholderPositions[part.type] ?: part.y
                val xOffset = (part.bitmap.width * part.scale / 2f)
                val yOffset = (part.bitmap.height * part.scale / 2f)
                canvas.save()
                canvas.translate(shipX, targetY)
                canvas.rotate(part.rotation, xOffset, yOffset)
                canvas.scale(part.scale, part.scale, xOffset, yOffset)
                canvas.drawBitmap(part.bitmap, -xOffset, -yOffset, shipTintPaint)
                canvas.restore()
                if (part.type == "engine") {
                    particleSystem.addPropulsionParticles(shipX, targetY + (part.bitmap.height * part.scale))
                }
            }
            particleSystem.drawCollectionParticles(canvas)
            Timber.d("Drawing ship in BUILD mode with ${shipParts.size} parts")
        } else {
            Timber.w("Cannot draw ship: invalid state or bitmap (gameState=$gameState, mergedShipBitmap=$mergedShipBitmap)")
        }
    }

    fun addCollectionParticles(x: Float, y: Float) {
        particleSystem.addCollectionParticles(x, y)
    }

    fun addCollisionParticles(x: Float, y: Float) {
        particleSystem.addCollisionParticles(x, y)
    }

    fun addDamageTextParticle(x: Float, y: Float, damage: Int) {
        particleSystem.addDamageTextParticle(x, y, damage)
    }

    fun addPowerUpTextParticle(x: Float, y: Float, text: String, powerUpType: String) {
        particleSystem.addPowerUpTextParticle(x, y, text, powerUpType)
    }

    fun addPowerUpSpriteParticles(x: Float, y: Float, powerUpType: String) {
        particleSystem.addPowerUpSpriteParticles(x, y, powerUpType)
    }

    fun addExplosionParticles(x: Float, y: Float) {
        particleSystem.addExplosionParticles(x, y)
    }

    fun addScoreTextParticle(x: Float, y: Float, text: String) {
        particleSystem.addScoreTextParticle(x, y, text)
    }

    fun addMissileExhaustParticles(x: Float, y: Float) {
        particleSystem.addMissileExhaustParticles(x, y)
    }

    fun clearParticles() {
        particleSystem.clearParticles()
    }

    fun onDestroy() {
        particleSystem.onDestroy()
        Timber.d("ShipRenderer onDestroy called")
    }
}