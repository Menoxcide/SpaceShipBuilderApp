package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import android.graphics.drawable.BitmapDrawable
import kotlin.random.Random
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Color
import kotlin.math.sin
import android.graphics.Typeface

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
        color = Color.argb(200, 0, 255, 255) // Cyan with slight transparency
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

    init {
        setWillNotDraw(false)
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
        val destroyAllButton = rootView.findViewById<Button>(R.id.destroyAllButton)
        destroyAllButton.isEnabled = charges > 0
        destroyAllButton.visibility = if (gameState == GameState.FLIGHT && charges > 0) View.VISIBLE else View.GONE

        val drawable = resources.getDrawable(R.drawable.destroy_all, null)
        val bitmap = (drawable as BitmapDrawable).bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val scaledDrawable = BitmapDrawable(resources, scaledBitmap)
        destroyAllButton.setCompoundDrawablesWithIntrinsicBounds(scaledDrawable, null, null, null)

        Timber.d("Updated destroyAllButton: visibility=${destroyAllButton.visibility}, enabled=${destroyAllButton.isEnabled}, charges=$charges")
    }

    fun updatePauseButton(gameState: GameState) {
        val pauseButton = rootView.findViewById<Button>(R.id.pauseButton)
        val shouldBeVisible = gameState == GameState.FLIGHT
        pauseButton.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
        Timber.d("Updated pauseButton: shouldBeVisible=$shouldBeVisible, visibility=${pauseButton.visibility}, gameState=$gameState")
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
                Timber.d("Post-layout visibility check: pauseButton visibility=${rootView.findViewById<Button>(R.id.pauseButton).visibility}, destroyAllButton visibility=${rootView.findViewById<Button>(R.id.destroyAllButton).visibility}")
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

        // Update warning animation phase
        warningPhase = (warningPhase + 0.05f) % (2 * Math.PI.toFloat())

        // Apply screen shake if active
        val currentTime = System.currentTimeMillis()
        if (currentTime - gameEngine.glowStartTime <= shakeDuration) {
            shakeTime = gameEngine.glowStartTime
            val timeSinceShake = (currentTime - shakeTime).toFloat() / shakeDuration
            val shakeOffsetX = Random.nextFloat() * shakeMagnitude * (1f - timeSinceShake)
            val shakeOffsetY = Random.nextFloat() * shakeMagnitude * (1f - timeSinceShake)
            canvas.translate(shakeOffsetX, shakeOffsetY)
        }

        // Check for level-up flash
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

        // Draw warning vignette if HP or fuel is low
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

        // Draw holographic HUD
        val hudMargin = 20f
        val barWidth = 150f
        val barHeight = 20f
        val textOffset = 40f
        val barSpacing = 10f

        // HP Bar
        val hpBarTop = statusBarHeight + hudMargin
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

        // Fuel Bar
        val fuelBarTop = hpBarTop + barHeight + barSpacing + textOffset
        canvas.drawText("Fuel: ${gameEngine.fuel.toInt()}/${gameEngine.fuelCapacity.toInt()}", hpBarLeft, fuelBarTop - 10f, hudPaint)
        hudBarPaint.color = Color.BLUE
        val fuelBarFilledWidth = barWidth * fuelFraction
        canvas.drawRect(hpBarLeft, fuelBarTop, hpBarLeft + fuelBarFilledWidth, fuelBarTop + barHeight, hudBarPaint)
        canvas.drawRect(hpBarLeft, fuelBarTop, hpBarLeft + barWidth, fuelBarTop + barHeight, hudBorderPaint)

        // Missiles
        val missileTextTop = fuelBarTop + barHeight + barSpacing + textOffset
        canvas.drawText("Missiles: ${gameEngine.missileCount}/${gameEngine.maxMissiles}", hpBarLeft, missileTextTop, hudPaint)

        // Level and Score on the right side
        val rightTextX = screenWidth - hudMargin - 150f
        canvas.drawText("Level: ${gameEngine.level}", rightTextX, hpBarTop, hudPaint)
        canvas.drawText("Score: ${flightModeManager.currentScore}", rightTextX, hpBarTop + textOffset, hudPaint)

        renderer.drawStats(canvas, gameEngine, statusBarHeight, gameStateManager.gameState)
        renderer.drawAIMessages(canvas, gameEngine.aiAssistant, statusBarHeight)
        Timber.d("Rendered frame in FlightView with ship at (x=${gameEngine.shipX}, y=${gameEngine.shipY})")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameStateManager.gameState != GameState.FLIGHT || visibility != View.VISIBLE) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.rawX
                val touchY = event.rawY

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
                    gameEngine.shipX = event.rawX.coerceIn(
                        gameEngine.maxPartHalfWidth,
                        screenWidth - gameEngine.maxPartHalfWidth
                    )
                    gameEngine.shipY = event.rawY.coerceIn(
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
        if (BuildConfig.DEBUG) Timber.d("FlightView detached from window")
    }
}