package com.example.spaceshipbuilderapp

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.max

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
    private val skillBonusPaint = Paint().apply {
        isAntiAlias = true
        textSize = 25f
        color = Color.CYAN
        textAlign = Paint.Align.LEFT
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    internal val aiMessagePaint = TextPaint().apply {
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
            Timber.d("UIRenderer drawStats: Paused state = $pausedState")
            val statsStartX = 20f
            var statsY = statusBarHeight + 40f

            // Draw base stats (paused or default)
            val stats: List<String>
            if (pausedState != null) {
                stats = listOf(
                    "Paused Game State:",
                    "HP: ${pausedState.hp.toInt()}/${gameEngine.maxHp.toInt()}",
                    "Fuel: ${pausedState.fuel.toInt()}/${gameEngine.fuelCapacity.toInt()}",
                    "Missiles: ${pausedState.missileCount}/${gameEngine.flightModeManager.shipManager.maxMissiles}", // Changed to current/max
                    "Position: (${pausedState.shipX.toInt()}, ${pausedState.shipY.toInt()})",
                    "Score: ${pausedState.currentScore}",
                    "Distance: ${pausedState.distanceTraveled.toInt()}",
                    "Level: ${pausedState.level}",
                    "Power-Up Duration: ${gameEngine.flightModeManager.powerUpManager.effectDuration / 1000}s",
                    "Power-Ups: " + listOfNotNull(
                        if (pausedState.shieldActive) "Shield" else null,
                        if (pausedState.speedBoostActive) "Speed" else null,
                        if (pausedState.stealthActive) "Stealth" else null,
                        if (pausedState.invincibilityActive) "Invincibility" else null
                    ).joinToString(", ").ifEmpty { "None" }
                )
            } else {
                stats = listOf(
                    "Ship Stats (Default):",
                    "HP: ${gameEngine.maxHp.toInt()}/${gameEngine.maxHp.toInt()}",
                    "Fuel: 50/${gameEngine.fuelCapacity.toInt()}",
                    "Missiles: ${gameEngine.flightModeManager.shipManager.maxMissiles}/${gameEngine.flightModeManager.shipManager.maxMissiles}", // Changed to current/max, defaults to max/max
                    "Position: (${(screenWidth / 2f).toInt()}, ${(screenHeight / 2f).toInt()})",
                    "Score: 0",
                    "Distance: 0",
                    "Level: 1",
                    "Power-Up Duration: ${gameEngine.flightModeManager.powerUpManager.effectDuration / 1000}s",
                    "Power-Ups: None"
                )
            }

            stats.forEach { stat ->
                canvas.drawText(stat, statsStartX, statsY, pausedStatsPaint)
                statsY += textHeight
            }

            // Draw skill bonuses below base stats
            statsY += 20f // Add some spacing between base stats and skill bonuses
            canvas.drawText("Skill Bonuses:", statsStartX, statsY, skillBonusPaint)
            statsY += 35f

            val skills = gameEngine.skillManager.skills
            val skillBonuses = mutableListOf<String>()

            val projectileDamageLevel = skills["projectile_damage"] ?: 0
            if (projectileDamageLevel > 0) {
                skillBonuses.add("Projectile Damage: +${projectileDamageLevel * 10}%")
            }

            val firingRateLevel = skills["firing_rate"] ?: 0
            if (firingRateLevel > 0) {
                skillBonuses.add("Firing Rate: +${firingRateLevel * 5}%")
            }

            val homingMissilesLevel = skills["homing_missiles"] ?: 0
            if (homingMissilesLevel > 0) {
                skillBonuses.add("Homing Missiles: +$homingMissilesLevel")
            }

            val maxHpLevel = skills["max_hp"] ?: 0
            if (maxHpLevel > 0) {
                skillBonuses.add("Max HP: +${maxHpLevel * 10}%")
            }

            val hpRegenerationLevel = skills["hp_regeneration"] ?: 0
            if (hpRegenerationLevel > 0) {
                skillBonuses.add("HP Regen: +$hpRegenerationLevel HP/s")
            }

            val shieldStrengthLevel = skills["shield_strength"] ?: 0
            if (shieldStrengthLevel > 0) {
                skillBonuses.add("Shield Strength: +${shieldStrengthLevel * 20}%")
            }

            val speedBoostLevel = skills["speed_boost"] ?: 0
            if (speedBoostLevel > 0) {
                skillBonuses.add("Speed: +${speedBoostLevel * 5}%")
            }

            val fuelEfficiencyLevel = skills["fuel_efficiency"] ?: 0
            if (fuelEfficiencyLevel > 0) {
                skillBonuses.add("Fuel Efficiency: -${fuelEfficiencyLevel * 5}%")
            }

            val powerUpDurationLevel = skills["power_up_duration"] ?: 0
            if (powerUpDurationLevel > 0) {
                skillBonuses.add("Power-Up Duration: +${powerUpDurationLevel * 10}%")
            }

            if (skillBonuses.isEmpty()) {
                canvas.drawText("None", statsStartX + 20f, statsY, skillBonusPaint)
            } else {
                skillBonuses.forEach { bonus ->
                    canvas.drawText(bonus, statsStartX + 20f, statsY, skillBonusPaint)
                    statsY += 30f
                }
            }
        }
    }

    fun drawAIMessages(canvas: Canvas, aiAssistant: AIAssistant, statusBarHeight: Float) {
        val messages = aiAssistant.getDisplayedMessages()
        if (messages.isEmpty()) return

        val padding = 20f
        val bottomOffset = aiAssistant.getBottomOffset()
        val maxWidth = (screenWidth * 0.9f).toInt() // 90% of screen width for wrapping

        var totalHeightSoFar = 0f // Track the cumulative height of messages to position them correctly

        messages.forEachIndexed { index, message ->
            // Create StaticLayout to wrap the text
            val staticLayout = StaticLayout.Builder.obtain(
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

            // Calculate the maximum width of the wrapped text (for centering)
            var maxLineWidth = 0f
            for (i in 0 until staticLayout.lineCount) {
                val lineWidth = staticLayout.getLineWidth(i)
                maxLineWidth = max(maxLineWidth, lineWidth)
            }

            val textHeight = staticLayout.height.toFloat()
            // Calculate the position for the text (no overlay, so no overlayWidth/overlayHeight)
            val textX = (screenWidth - maxLineWidth) / 2f
            val textY = screenHeight - bottomOffset - textHeight - totalHeightSoFar - (index * padding)

            // Draw the text directly without a background box
            canvas.save()
            canvas.translate(textX, textY)
            staticLayout.draw(canvas)
            canvas.restore()

            // Update the total height for the next message
            totalHeightSoFar += textHeight + padding

            if (BuildConfig.DEBUG) {
                Timber.d("Drawing AI message '$message' at (x=$textX, y=$textY) with width=$maxLineWidth, height=$textHeight, lines=${staticLayout.lineCount}")
            }
        }
    }
}