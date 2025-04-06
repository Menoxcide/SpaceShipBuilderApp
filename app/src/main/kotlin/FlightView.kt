package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

@AndroidEntryPoint
class FlightView @Inject constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var renderer: Renderer
    @Inject lateinit var gameStateManager: GameStateManager
    @Inject lateinit var buildModeManager: BuildModeManager
    @Inject lateinit var flightModeManager: FlightModeManager

    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    private var statusBarHeight: Float = 0f
    private var isDragging = false
    private var updateRunnable: Runnable? = null
    private var userId: String? = null
    private var lastProjectileSpawnTime = 0L
    private val projectileSpawnInterval = 1000L

    private var pendingGameMode: GameState? = null
    private var shakeTime: Long = 0L
    private var shakeDuration: Long = 300L
    private var shakeMagnitude: Float = 10f
    private var currentShakeOffsetX: Float = 0f
    private var currentShakeOffsetY: Float = 0f

    private val warningPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private var warningPhase: Float = 0f
    private val warningRadius: Float
        get() = Math.max(screenWidth, screenHeight) * 1.5f

    private val flashPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private var flashStartTime: Long = 0L
    private val flashDuration: Long = 300L

    private val hudPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(200, 0, 255, 255)
        textSize = 30f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(5f, 2f, 2f, Color.argb(255, 0, 200, 200))
    }
    private val hudBarPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val hudBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(200, 0, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val powerUpCooldownPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(128, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val powerUpCooldownBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val missileIndicatorPaint = Paint().apply { isAntiAlias = true }

    private val robotPaint = Paint().apply {
        isAntiAlias = true
    }
    private var robotBitmap: Bitmap? = null
    private var robotPhase: Float = 0f

    private val dialPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(200, 0, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        setShadowLayer(5f, 2f, 2f, Color.argb(255, 0, 200, 200))
    }
    private val dialFillPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(100, 0, 255, 255)
        style = Paint.Style.FILL
    }
    private val dialNeedlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val dialTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var pauseButtonBitmap: Bitmap? = null
    private var destroyAllButtonBitmap: Bitmap? = null
    private var pauseButtonRect: RectF? = null
    private var destroyAllButtonRect: RectF? = null
    private var pauseButtonVisible: Boolean = false
    private var destroyAllButtonVisible: Boolean = false
    private var destroyAllButtonEnabled: Boolean = false

    private val powerUpBitmaps = mapOf(
        "shield" to BitmapFactory.decodeResource(context.resources, R.drawable.shield_icon),
        "speed" to BitmapFactory.decodeResource(context.resources, R.drawable.speed_icon),
        "stealth" to BitmapFactory.decodeResource(context.resources, R.drawable.stealth_icon),
        "invincibility" to BitmapFactory.decodeResource(context.resources, R.drawable.invincibility_icon)
    )
    private val missileSprite: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.homing_missile).let {
        val scaleHeight = 32f
        val aspectRatio = it.width.toFloat() / it.height.toFloat()
        Bitmap.createScaledBitmap(it, (scaleHeight * aspectRatio).toInt(), scaleHeight.toInt(), true)
    }

    init {
        setWillNotDraw(false)
     robotBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.robot_companion)?.let {
            Bitmap.createScaledBitmap(it, 256, 256, true)
        }
        pauseButtonBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.pause_icon)?.let {
            Bitmap.createScaledBitmap(it, 64, 64, true)
        }
        destroyAllButtonBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.destroy_all)?.let {
            Bitmap.createScaledBitmap(it, 64, 64, true)
        }
        if (pauseButtonBitmap == null || destroyAllButtonBitmap == null) {
            Timber.w("Failed to load button bitmaps")
        }
        if (BuildConfig.DEBUG) Timber.d("FlightView initialized")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        gameEngine.screenWidth = screenWidth
        gameEngine.screenHeight = screenHeight
        Timber.d("FlightView size changed: w=$w, h=$h")

        pendingGameMode?.let { mode ->
            setGameModeInternal(mode)
            pendingGameMode = null
        }
    }

    fun setStatusBarHeight(height: Float) {
        statusBarHeight = height
        gameEngine.statusBarHeight = statusBarHeight
        Timber.d("FlightView status bar height set to: $height")
    }

    fun setUserId(userId: String) {
        this.userId = userId
    }

    fun updateDestroyAllButton(charges: Int, gameState: GameState) {
        destroyAllButtonVisible = gameState == GameState.FLIGHT && charges > 0
        destroyAllButtonEnabled = charges > 0
        Timber.d("Updated destroyAllButton: visible=$destroyAllButtonVisible, enabled=$destroyAllButtonEnabled, charges=$charges")
    }

    fun updatePauseButton(gameState: GameState) {
        pauseButtonVisible = gameState == GameState.FLIGHT
        Timber.d("Updated pauseButton: visible=$pauseButtonVisible, gameState=$gameState")
    }

    fun triggerPause() {
        if (pauseButtonVisible) {
            gameStateManager.setGameState(GameState.BUILD, screenWidth, screenHeight, flightModeManager::resetFlightData, gameEngine::savePersistentData, userId ?: "default", gameEngine)
            Timber.d("Pause triggered programmatically, transitioning to BUILD state")
        }
    }

    fun triggerDestroyAll(): Boolean {
        if (destroyAllButtonVisible && destroyAllButtonEnabled) {
            val success = flightModeManager.destroyAll()
            if (success) {
                updateDestroyAllButton(gameEngine.destroyAllCharges - 1, gameStateManager.gameState)
            }
            Timber.d("Destroy All triggered programmatically, success=$success")
            return success
        }
        return false
    }

    fun setGameMode(mode: GameState) {
        if (screenWidth == 0f || screenHeight == 0f) {
            pendingGameMode = mode
            Timber.d("Deferring setGameMode to $mode until dimensions are available")
            return
        }
        setGameModeInternal(mode)
    }

    private fun setGameModeInternal(mode: GameState) {
        Timber.d("setGameModeInternal called with mode=$mode")
        if (mode == GameState.FLIGHT) {
            visibility = View.VISIBLE
            gameEngine.shipX = screenWidth / 2f
            gameEngine.shipY = screenHeight / 2f
            updatePauseButton(mode)
            updateDestroyAllButton(gameEngine.destroyAllCharges, mode)
            post {
                updatePauseButton(mode)
                updateDestroyAllButton(gameEngine.destroyAllCharges, mode)
                Timber.d("Post-layout visibility check: pauseButtonVisible=$pauseButtonVisible, destroyAllButtonVisible=$destroyAllButtonVisible")
            }
            startUpdateLoop()
            Timber.d("FlightView activated, starting update loop")
        } else {
            visibility = View.GONE
            updatePauseButton(mode)
            updateDestroyAllButton(gameEngine.destroyAllCharges, mode)
            stopUpdateLoop()
            Timber.d("FlightView deactivated")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gameStateManager.gameState != GameState.FLIGHT || visibility != View.VISIBLE) {
            Timber.w("onDraw skipped: gameState=${gameStateManager.gameState}, visibility=$visibility")
            return
        }
        Timber.d("onDraw called, visibility=$visibility, gameState=${gameStateManager.gameState}")

        warningPhase = (warningPhase + 0.05f) % (2 * Math.PI.toFloat())
        robotPhase = (robotPhase + 0.05f) % (2 * Math.PI.toFloat())

        val currentTime = System.currentTimeMillis()
        currentShakeOffsetX = 0f
        currentShakeOffsetY = 0f
        if (currentTime - gameEngine.glowStartTime <= shakeDuration) {
            shakeTime = gameEngine.glowStartTime
            val timeSinceShake = (currentTime - shakeTime).toFloat() / shakeDuration
            currentShakeOffsetX = Random.nextFloat() * shakeMagnitude * (1f - timeSinceShake)
            currentShakeOffsetY = Random.nextFloat() * shakeMagnitude * (1f - timeSinceShake)
            canvas.translate(currentShakeOffsetX, currentShakeOffsetY)
        }

        if (currentTime - flightModeManager.levelUpAnimationStartTime <= flashDuration) {
            flashStartTime = flightModeManager.levelUpAnimationStartTime
            val timeSinceFlash = (currentTime - flashStartTime).toFloat() / flashDuration
            val flashAlpha = (255 * (1f - timeSinceFlash)).toInt().coerceIn(0, 255)
            flashPaint.color = Color.argb(flashAlpha, 255, 255, 255)
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, flashPaint)
        }

        renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight, gameEngine.level, gameEngine.currentEnvironment)
        renderer.drawShip(
            canvas,
            gameEngine,
            emptyList<Part>(),
            screenWidth,
            screenHeight,
            gameEngine.shipX,
            gameEngine.shipY,
            gameStateManager.gameState,
            gameEngine.mergedShipBitmap,
            buildModeManager.placeholders
        )

        renderer.drawPowerUps(canvas, gameEngine.powerUps, statusBarHeight)
        renderer.drawAsteroids(canvas, gameEngine.asteroids, statusBarHeight)
        renderer.drawProjectiles(canvas, gameEngine.projectiles, statusBarHeight)
        renderer.drawEnemyShips(canvas, gameEngine.enemyShips, statusBarHeight)
        renderer.drawBoss(canvas, gameEngine.getBoss(), statusBarHeight)
        renderer.drawEnemyProjectiles(canvas, gameEngine.enemyProjectiles, statusBarHeight)
        renderer.drawHomingProjectiles(canvas, gameEngine.homingProjectiles, statusBarHeight)

        val hpFraction = gameEngine.hp / gameEngine.maxHp
        val fuelFraction = gameEngine.fuel / gameEngine.fuelCapacity
        if (hpFraction < 0.3f || fuelFraction < 0.3f) {
            val warningColor = if (hpFraction < 0.3f) Color.RED else Color.YELLOW
            val warningAlpha = (sin(warningPhase.toDouble()) * 127f + 128f).toInt()
            warningPaint.shader = RadialGradient(
                screenWidth / 2f, screenHeight / 2f,
                warningRadius,
                Color.TRANSPARENT,
                Color.argb(warningAlpha, Color.red(warningColor), Color.green(warningColor), Color.blue(warningColor)),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, warningPaint)
        }

        // HUD rendering
        val hudMargin = 50f
        val barWidth = 150f
        val barHeight = 20f
        val textOffset = 40f
        val barSpacing = 10f
        val buttonSize = 64f
        val buttonSpacing = 10f
        val powerUpSize = 64f
        val powerUpSpacing = 10f
        val missileSize = missileSprite.width.toFloat()
        val missileSpacing = 5f

        val hpBarTop = statusBarHeight + hudMargin + 20f
        val hpBarLeft = hudMargin
        canvas.drawText("HP: ${gameEngine.hp.toInt()}/${gameEngine.maxHp.toInt()}", hpBarLeft, hpBarTop - 10f, hudPaint)
        hudBarPaint.color = when {
            hpFraction > 0.5f -> Color.GREEN
            hpFraction > 0.3f -> Color.YELLOW
            else -> Color.RED
        }
        val hpBarFilledWidth = barWidth * hpFraction
        canvas.drawRect(hpBarLeft, hpBarTop, hpBarLeft + hpBarFilledWidth, hpBarTop + barHeight, hudBarPaint)
        canvas.drawRect(hpBarLeft, hpBarTop, hpBarLeft + barWidth, hpBarTop + barHeight, hudBorderPaint)

        val fuelBarTop = hpBarTop + barHeight + barSpacing + textOffset
        canvas.drawText("Fuel: ${gameEngine.fuel.toInt()}/${gameEngine.fuelCapacity.toInt()}", hpBarLeft, fuelBarTop - 10f, hudPaint)
        hudBarPaint.color = Color.BLUE
        val fuelBarFilledWidth = barWidth * fuelFraction
        canvas.drawRect(hpBarLeft, fuelBarTop, hpBarLeft + fuelBarFilledWidth, fuelBarTop + barHeight, hudBarPaint)
        canvas.drawRect(hpBarLeft, fuelBarTop, hpBarLeft + barWidth, fuelBarTop + barHeight, hudBorderPaint)

        // Missile indicator
        val missileTop = fuelBarTop + barHeight + barSpacing + textOffset
        val totalMissiles = gameEngine.maxMissiles
        val totalMissileWidth = totalMissiles * missileSize + (totalMissiles - 1) * missileSpacing
        val missileXStart = hpBarLeft // Align with HP/Fuel text
        for (i in 0 until totalMissiles) {
            missileIndicatorPaint.alpha = if (i < gameEngine.missileCount) 255 else 50
            val missileX = missileXStart + i * (missileSize + missileSpacing)
            canvas.drawBitmap(missileSprite, missileX, missileTop, missileIndicatorPaint)
        }

        // Power-up indicators
        val powerUpTop = missileTop + missileSprite.height + barSpacing
        val activePowerUps = mutableListOf<Pair<String, Long>>()
        if (gameEngine.shieldActive) activePowerUps.add("shield" to flightModeManager.powerUpManager.getShieldRemainingDuration(currentTime))
        if (gameEngine.speedBoostActive) activePowerUps.add("speed" to flightModeManager.powerUpManager.getSpeedBoostRemainingDuration(currentTime))
        if (gameEngine.stealthActive) activePowerUps.add("stealth" to flightModeManager.powerUpManager.getStealthRemainingDuration(currentTime))
        if (gameEngine.invincibilityActive) activePowerUps.add("invincibility" to flightModeManager.powerUpManager.getInvincibilityRemainingDuration(currentTime))

        var powerUpX = hpBarLeft // Align with HP/Fuel/Missile left margin

        activePowerUps.forEach { (type, remainingDuration) ->
            val bitmap = powerUpBitmaps[type]
            if (bitmap != null && !bitmap.isRecycled) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, powerUpSize.toInt(), powerUpSize.toInt(), true)
                canvas.drawBitmap(scaledBitmap, powerUpX, powerUpTop, null)

                val fractionRemaining = (remainingDuration.toFloat() / flightModeManager.powerUpManager.effectDuration).coerceIn(0f, 1f)
                if (fractionRemaining > 0f) {
                    val centerX = powerUpX + powerUpSize / 2f
                    val centerY = powerUpTop + powerUpSize / 2f
                    val radius = powerUpSize / 2f
                    val sweepAngle = 360f * fractionRemaining
                    val cooldownPath = Path().apply {
                        addArc(
                            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
                            -90f,
                            -sweepAngle
                        )
                    }
                    canvas.drawPath(cooldownPath, powerUpCooldownPaint)
                    canvas.drawCircle(centerX, centerY, radius, powerUpCooldownBorderPaint)
                }
                powerUpX += powerUpSize + powerUpSpacing
            }
        }

        val rightTextX = screenWidth - hudMargin - 150f
        val rightHudTop = statusBarHeight + hudMargin + 20f
        canvas.drawText("Level: ${gameEngine.level}", rightTextX, rightHudTop, hudPaint)
        canvas.drawText("Score: ${flightModeManager.currentScore}", rightTextX, rightHudTop + textOffset, hudPaint)

        val buttonYStart = rightHudTop + 2 * textOffset
        val buttonX = rightTextX + 100f

        if (pauseButtonVisible) {
            pauseButtonBitmap?.let { bitmap ->
                val left = buttonX
                val top = buttonYStart
                val right = left + buttonSize
                val bottom = top + buttonSize
                pauseButtonRect = RectF(left, top, right, bottom)
                canvas.drawBitmap(bitmap, left, top, null)
                if (BuildConfig.DEBUG) {
                    val debugPaint = Paint().apply {
                        color = Color.argb(128, 255, 0, 0)
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                    }
                    canvas.drawRect(pauseButtonRect!!, debugPaint)
                }
            }
        } else {
            pauseButtonRect = null
        }

        if (destroyAllButtonVisible) {
            destroyAllButtonBitmap?.let { bitmap ->
                val left = buttonX
                val top = buttonYStart + buttonSize + buttonSpacing
                val right = left + buttonSize
                val bottom = top + buttonSize
                destroyAllButtonRect = RectF(left, top, right, bottom)
                if (destroyAllButtonEnabled) {
                    canvas.drawBitmap(bitmap, left, top, null)
                } else {
                    robotPaint.alpha = 128
                    canvas.drawBitmap(bitmap, left, top, robotPaint)
                    robotPaint.alpha = 255
                }
                if (BuildConfig.DEBUG) {
                    val debugPaint = Paint().apply {
                        color = Color.argb(128, 0, 255, 0)
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                    }
                    canvas.drawRect(destroyAllButtonRect!!, debugPaint)
                }
            }
        } else {
            destroyAllButtonRect = null
        }

        val dialRadius = 50f
        val dialX = screenWidth / 2f
        val dialY = statusBarHeight + hudMargin + dialRadius + 20f
        val distance = flightModeManager.getSessionDistanceTraveled()
        val speed = flightModeManager.shipManager.currentSpeed
        val maxDistancePerLevel = flightModeManager.getDistancePerLevel() * gameEngine.level
        val distanceFraction = (distance / maxDistancePerLevel).coerceIn(0f, 1f)
        val speedFraction = (speed / 10f).coerceIn(0f, 1f)

        canvas.drawCircle(dialX, dialY, dialRadius, dialFillPaint)
        canvas.drawCircle(dialX, dialY, dialRadius, dialPaint)

        val distanceSweepAngle = 360f * distanceFraction
        val distancePath = Path().apply {
            addArc(
                RectF(dialX - dialRadius, dialY - dialRadius, dialX + dialRadius, dialY + dialRadius),
                -90f,
                distanceSweepAngle
            )
        }
        dialFillPaint.color = Color.argb(150, 0, 255, 0)
        canvas.drawPath(distancePath, dialFillPaint)

        val speedAngle = -90f + (360f * speedFraction)
        val needleLength = dialRadius * 0.8f
        val needleEndX = dialX + needleLength * cos(Math.toRadians(speedAngle.toDouble())).toFloat()
        val needleEndY = dialY + needleLength * sin(Math.toRadians(speedAngle.toDouble())).toFloat()
        canvas.drawLine(dialX, dialY, needleEndX, needleEndY, dialNeedlePaint)

        canvas.drawText("Distance: ${distance.toInt()}", dialX, dialY + dialRadius + 30f, dialTextPaint)
        canvas.drawText("Speed: ${speed.toInt()}", dialX, dialY + dialRadius + 50f, dialTextPaint)

        val messages = gameEngine.aiAssistant.getDisplayedMessages()
        if (messages.isNotEmpty()) {
            val padding = 20f
            val bottomOffset = gameEngine.aiAssistant.getBottomOffset()
            val maxWidth = (screenWidth * 0.9f).toInt()

            var totalHeightSoFar = 0f

            messages.forEachIndexed { index, message ->
                val staticLayout = StaticLayout.Builder.obtain(
                    message,
                    0,
                    message.length,
                    renderer.uiRenderer.aiMessagePaint,
                    maxWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0.0f, 1.0f)
                    .setIncludePad(false)
                    .build()

                var maxLineWidth = 0f
                for (i in 0 until staticLayout.lineCount) {
                    val lineWidth = staticLayout.getLineWidth(i)
                    maxLineWidth = max(maxLineWidth, lineWidth)
                }

                val textHeight = staticLayout.height.toFloat()
                val textX = (screenWidth - maxLineWidth) / 2f
                val textY = screenHeight - bottomOffset - textHeight - totalHeightSoFar - (index * padding)

                if (index == 0) {
                    robotBitmap?.let { bitmap ->
                        if (!bitmap.isRecycled) {
                            val robotX = (textX - bitmap.width - 10f).coerceAtLeast(0f)
                            val robotY = textY + (textHeight - bitmap.height) / 2f
                            val hoverOffset = sin(robotPhase.toDouble()) * 5f
                            canvas.drawBitmap(bitmap, robotX, robotY + hoverOffset.toFloat(), robotPaint)
                        } else {
                            Timber.w("Robot bitmap is recycled")
                        }
                    }
                }

                canvas.save()
                canvas.translate(textX, textY)
                staticLayout.draw(canvas)
                canvas.restore()

                totalHeightSoFar += textHeight + padding

                if (BuildConfig.DEBUG) {
                    Timber.d("Drawing AI message '$message' at (x=$textX, y=$textY) with width=$maxLineWidth, height=$textHeight, lines=${staticLayout.lineCount}")
                }
            }
        }

        Timber.d("Rendered frame in FlightView with ship at (x=${gameEngine.shipX}, y=${gameEngine.shipY})")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameStateManager.gameState != GameState.FLIGHT || visibility != View.VISIBLE) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val location = IntArray(2)
                getLocationOnScreen(location)
                val viewLeft = location[0].toFloat()
                val viewTop = location[1].toFloat()

                val touchX = event.rawX - viewLeft - currentShakeOffsetX
                val touchY = event.rawY - viewTop - currentShakeOffsetY - statusBarHeight

                if (BuildConfig.DEBUG) {
                    Timber.d("Touch at (rawX=${event.rawX}, rawY=${event.rawY}), view position (left=$viewLeft, top=$viewTop), adjusted (touchX=$touchX, touchY=$touchY), statusBarHeight=$statusBarHeight")
                    pauseButtonRect?.let {
                        Timber.d("Pause button bounds: left=${it.left}, top=${it.top}, right=${it.right}, bottom=${it.bottom}")
                    }
                    destroyAllButtonRect?.let {
                        Timber.d("Destroy All button bounds: left=${it.left}, top=${it.top}, right=${it.right}, bottom=${it.bottom}")
                    }
                }

                if (pauseButtonVisible && pauseButtonRect?.contains(touchX, touchY) == true) {
                    gameStateManager.setGameState(GameState.BUILD, screenWidth, screenHeight, flightModeManager::resetFlightData, gameEngine::savePersistentData, userId ?: "default", gameEngine)
                    Timber.d("Pause button clicked, transitioning to BUILD state")
                    return true
                }

                if (destroyAllButtonVisible && destroyAllButtonEnabled && destroyAllButtonRect?.contains(touchX, touchY) == true) {
                    val success = flightModeManager.destroyAll()
                    if (success) {
                        updateDestroyAllButton(gameEngine.destroyAllCharges - 1, gameStateManager.gameState)
                    }
                    Timber.d("Destroy All button clicked, success=$success")
                    return true
                }

                for (enemy in gameEngine.enemyShips) {
                    val enemyRect = RectF(
                        enemy.x - 75f,
                        enemy.y - 75f,
                        enemy.x + 75f,
                        enemy.y + 75f
                    )
                    if (enemyRect.contains(touchX, touchY)) {
                        gameEngine.launchHomingMissile(enemy)
                        Timber.d("Clicked enemy ship at (x=${enemy.x}, y=${enemy.y}), launching homing missile")
                        return true
                    }
                }

                val boss = gameEngine.getBoss()
                if (boss != null) {
                    val bossRect = RectF(
                        boss.x - 100f,
                        boss.y - 100f,
                        boss.x + 100f,
                        boss.y + 100f
                    )
                    if (bossRect.contains(touchX, touchY)) {
                        gameEngine.launchHomingMissile(boss)
                        Timber.d("Clicked boss at (x=${boss.x}, y=${boss.y}), launching homing missile")
                        return true
                    }
                }

                val shipRect = RectF(
                    gameEngine.shipX - gameEngine.maxPartHalfWidth,
                    gameEngine.shipY - gameEngine.totalShipHeight / 2,
                    gameEngine.shipX + gameEngine.maxPartHalfWidth,
                    gameEngine.shipY + gameEngine.totalShipHeight / 2
                )
                if (shipRect.contains(touchX, touchY)) {
                    isDragging = true
                    if (BuildConfig.DEBUG) Timber.d("Started dragging ship from (x=$touchX, y=$touchY)")
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    val viewLeft = location[0].toFloat()
                    val viewTop = location[1].toFloat()

                    val touchX = event.rawX - viewLeft - currentShakeOffsetX
                    val touchY = event.rawY - viewTop - currentShakeOffsetY - statusBarHeight

                    gameEngine.shipX = touchX.coerceIn(
                        gameEngine.maxPartHalfWidth,
                        screenWidth - gameEngine.maxPartHalfWidth
                    )
                    gameEngine.shipY = touchY.coerceIn(
                        gameEngine.totalShipHeight / 2,
                        screenHeight - gameEngine.totalShipHeight / 2
                    )
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProjectileSpawnTime >= projectileSpawnInterval) {
                        gameEngine.spawnProjectile()
                        lastProjectileSpawnTime = currentTime
                        Timber.d("Spawned projectile at (x=${gameEngine.shipX}, y=${gameEngine.shipY - (gameEngine.mergedShipBitmap?.height ?: 0) / 2f})")
                    }
                    if (BuildConfig.DEBUG) Timber.d("Ship moved to (x=${gameEngine.shipX}, y=${gameEngine.shipY})")
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                if (BuildConfig.DEBUG) Timber.d("Stopped dragging ship")
                return true
            }
            else -> return false
        }
    }

    private fun startUpdateLoop() {
        updateRunnable?.let { removeCallbacks(it) }
        updateRunnable = object : Runnable {
            override fun run() {
                if (gameStateManager.gameState == GameState.FLIGHT && visibility == View.VISIBLE) {
                    gameEngine.update(screenWidth, screenHeight, userId ?: "default")
                    updatePauseButton(gameStateManager.gameState)
                    updateDestroyAllButton(gameEngine.destroyAllCharges, gameStateManager.gameState)
                    invalidate()
                    if (BuildConfig.DEBUG) Timber.d("FlightView update loop: gameState=${gameStateManager.gameState}, invalidated")
                    postDelayed(this, 16)
                } else {
                    if (BuildConfig.DEBUG) Timber.d("FlightView update loop skipped: gameState=${gameStateManager.gameState}")
                }
            }
        }
        post(updateRunnable)
    }

    private fun stopUpdateLoop() {
        updateRunnable?.let { removeCallbacks(it) }
        updateRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopUpdateLoop()
        robotBitmap?.recycle()
        robotBitmap = null
        pauseButtonBitmap?.recycle()
        pauseButtonBitmap = null
        destroyAllButtonBitmap?.recycle()
        destroyAllButtonBitmap = null
        missileSprite.recycle()
        if (BuildConfig.DEBUG) Timber.d("FlightView detached from window")
    }
}