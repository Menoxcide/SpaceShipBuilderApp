package com.example.spaceshipbuilderapp

import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.text.StaticLayout.Builder
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.sin

class UIRenderer @Inject constructor() {
    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    private var animationTime: Long = 0L

    private var unlockMessage: String? = null
    private var unlockMessageStartTime: Long = 0L
    private val unlockMessageDuration = 5000L

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
    private val graffitiPaint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
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
    private val pausedStatsPaint = Paint().apply {
        isAntiAlias = true
        textSize = 30f
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val aiMessagePaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.CYAN
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private val aiOverlayPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val aiPulsePaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(100, 0, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun setScreenDimensions(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    fun updateAnimationTime() {
        animationTime = System.currentTimeMillis()
    }

    fun showUnlockMessage(messages: List<String>) {
        if (messages.isNotEmpty()) {
            unlockMessage = "Unlocked: ${messages.joinToString(", ")}"
            unlockMessageStartTime = System.currentTimeMillis()
            Timber.d("Showing unlock message: $unlockMessage")
        }
    }

    fun drawStats(canvas: Canvas, gameEngine: GameEngine, statusBarHeight: Float, gameState: GameState) {
        val currentTime = System.currentTimeMillis()
        val textHeight = 40f
        val startY = statusBarHeight + 50f

        if (gameState == GameState.FLIGHT) {
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

            if (gameEngine.levelUpAnimationStartTime > 0L && currentTime - gameEngine.levelUpAnimationStartTime <= FlightModeManager.LEVEL_UP_ANIMATION_DURATION) {
                val levelTextDisplay = "Level ${gameEngine.level}"
                canvas.drawText(levelTextDisplay, screenWidth / 2f, screenHeight / 2f, levelUpPaint)
            } else {
                gameEngine.levelUpAnimationStartTime = 0L
            }

            if (unlockMessage != null && currentTime - unlockMessageStartTime <= unlockMessageDuration) {
                val unlockPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 50f
                    color = Color.YELLOW
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(4f, 2f, 2f, Color.BLACK)
                }
                canvas.drawText(unlockMessage!!, screenWidth / 2f, screenHeight / 2f + 100f, unlockPaint)
            } else {
                unlockMessage = null
            }
        } else if (gameState == GameState.BUILD) {
            val pausedState = gameEngine.gameStateManager.getPausedState()
            val pausedStatsStartX = 20f
            var pausedStatsY = statusBarHeight + 40f

            if (pausedState != null) {
                // Display paused game state
                val stats = listOf(
                    "Paused Game State:",
                    "HP: ${pausedState.hp.toInt()}/${gameEngine.maxHp.toInt()}",
                    "Fuel: ${pausedState.fuel.toInt()}/${gameEngine.fuelCapacity.toInt()}",
                    "Missiles: ${pausedState.missileCount}/${gameEngine.flightModeManager.shipManager.maxMissiles}",
                    "Position: (${pausedState.shipX.toInt()}, ${pausedState.shipY.toInt()})",
                    "Score: ${pausedState.currentScore}",
                    "Distance: ${pausedState.distanceTraveled.toInt()}",
                    "Level: ${pausedState.level}",
                    "Power-Ups: " + listOfNotNull(
                        if (pausedState.shieldActive) "Shield" else null,
                        if (pausedState.speedBoostActive) "Speed" else null,
                        if (pausedState.stealthActive) "Stealth" else null,
                        if (pausedState.invincibilityActive) "Invincibility" else null
                    ).joinToString(", ").ifEmpty { "None" }
                )

                stats.forEach { stat ->
                    canvas.drawText(stat, pausedStatsStartX, pausedStatsY, pausedStatsPaint)
                    pausedStatsY += textHeight
                }
            } else {
                // Display default ship values
                val defaultStats = listOf(
                    "Ship Stats (Default):",
                    "HP: ${gameEngine.maxHp.toInt()}/${gameEngine.maxHp.toInt()}",
                    "Fuel: 50/${gameEngine.fuelCapacity.toInt()}",
                    "Missiles: ${gameEngine.flightModeManager.shipManager.maxMissiles}/${gameEngine.flightModeManager.shipManager.maxMissiles}",
                    "Position: (${(screenWidth / 2f).toInt()}, ${(screenHeight / 2f).toInt()})",
                    "Score: 0",
                    "Distance: 0",
                    "Level: 1",
                    "Power-Ups: None"
                )

                defaultStats.forEach { stat ->
                    canvas.drawText(stat, pausedStatsStartX, pausedStatsY, pausedStatsPaint)
                    pausedStatsY += textHeight
                }
            }
        }
    }

    fun drawAIMessages(canvas: Canvas, aiAssistant: AIAssistant, statusBarHeight: Float) {
        val messages = aiAssistant.getDisplayedMessages()
        if (messages.isEmpty()) return

        val padding = 20f
        val bottomOffset = aiAssistant.getBottomOffset()
        val maxWidth = (screenWidth * 0.8f).toInt()

        messages.forEachIndexed { index, message ->
            val staticLayout = Builder.obtain(
                message,
                0,
                message.length,
                aiMessagePaint,
                maxWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0.0f, 1.0f)
                .setIncludePad(false)
                .build()

            val textWidth = staticLayout.getLineWidth(0).toFloat()
            val lineCount = staticLayout.lineCount
            val textHeight = staticLayout.height.toFloat()
            val overlayWidth = min(textWidth + 2 * padding, maxWidth.toFloat())
            val overlayHeight = textHeight + 2 * padding
            val overlayX = (screenWidth - overlayWidth) / 2f
            val overlayY = screenHeight - bottomOffset - overlayHeight - (index * (overlayHeight + padding))

            canvas.drawRect(
                overlayX,
                overlayY,
                overlayX + overlayWidth,
                overlayY + overlayHeight,
                aiOverlayPaint
            )

            val pulse = (sin(animationTime / 500f) + 1) / 2
            aiPulsePaint.alpha = (pulse * 100).toInt() + 50
            canvas.drawRect(
                overlayX,
                overlayY,
                overlayX + overlayWidth,
                overlayY + overlayHeight,
                aiPulsePaint
            )

            canvas.save()
            canvas.translate(overlayX + padding, overlayY + padding)
            staticLayout.draw(canvas)
            canvas.restore()

            if (BuildConfig.DEBUG) {
                Timber.d("Drawing AI message '$message' at (x=$overlayX, y=$overlayY) with width=$overlayWidth, height=$overlayHeight, lines=$lineCount")
            }
        }
    }
}