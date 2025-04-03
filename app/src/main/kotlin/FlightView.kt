package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
    private val projectileSpawnInterval = 1000L // 1 second interval

    private var pendingGameMode: GameState? = null

    init {
        setWillNotDraw(false) // Ensure onDraw is called
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

        // Scale the destroy_all drawable to 64x64dp
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
        super.onDraw(canvas) // Draw child views (buttons)
        if (gameStateManager.gameState != GameState.FLIGHT || visibility != View.VISIBLE) {
            Timber.w("onDraw skipped: gameState=${gameStateManager.gameState}, visibility=$visibility")
            return
        }
        Timber.d("onDraw called, visibility=$visibility, gameState=${gameStateManager.gameState}")

        renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight, gameEngine.level)
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