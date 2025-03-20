package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import timber.log.Timber
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FlightView @Inject constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var renderer: Renderer

    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    private var statusBarHeight: Float = 0f
    private var isDragging = false
    private var updateRunnable: Runnable? = null
    private var userId: String? = null
    private var lastProjectileSpawnTime = 0L
    private val projectileSpawnInterval = 1000L // 1 second interval

    init {
        if (BuildConfig.DEBUG) Timber.d("FlightView initialized")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        gameEngine.screenWidth = screenWidth
        gameEngine.screenHeight = screenHeight
        if (BuildConfig.DEBUG) Timber.d("FlightView size changed: w=$w, h=$h")
    }

    fun setStatusBarHeight(height: Float) {
        statusBarHeight = height
        if (BuildConfig.DEBUG) Timber.d("FlightView status bar height set to: $height")
    }

    fun setUserId(userId: String) {
        this.userId = userId
    }

    fun setGameMode(mode: GameState) {
        if (mode == GameState.FLIGHT) {
            visibility = View.VISIBLE
            gameEngine.shipX = screenWidth / 2f
            gameEngine.shipY = screenHeight / 2f
            gameEngine.onPowerUpCollectedListener = { x, y ->
                renderer.particleSystem.addCollectionParticles(x, y)
            }
            startUpdateLoop()
            if (BuildConfig.DEBUG) Timber.d("FlightView activated, starting update loop")
        } else {
            visibility = View.GONE
            stopUpdateLoop()
            if (BuildConfig.DEBUG) Timber.d("FlightView deactivated")
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (gameState != GameState.FLIGHT || visibility != View.VISIBLE) {
            Timber.w("onDraw skipped: gameState=${gameState}, visibility=$visibility")
            return
        }
        super.onDraw(canvas)
        Timber.d("onDraw called, visibility=$visibility, gameState=${gameState}")
        renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight, gameEngine.level)
        renderer.drawShip(
            canvas,
            gameEngine,
            emptyList(),
            screenWidth,
            screenHeight,
            gameEngine.shipX,
            gameEngine.shipY,
            gameState,
            gameEngine.mergedShipBitmap,
            gameEngine.placeholders
        )
        renderer.drawPowerUps(canvas, gameEngine.powerUps, statusBarHeight)
        renderer.drawAsteroids(canvas, gameEngine.asteroids, statusBarHeight)
        renderer.drawProjectiles(canvas, gameEngine.projectiles, statusBarHeight)
        renderer.drawStats(canvas, gameEngine, statusBarHeight)
        Timber.d("Rendered frame in FlightView with ship at (x=${gameEngine.shipX}, y=${gameEngine.shipY})")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState != GameState.FLIGHT || visibility != View.VISIBLE) return false
        val shipRect = RectF(
            gameEngine.shipX - (gameEngine.mergedShipBitmap?.width ?: 0) / 2f,
            gameEngine.shipY - (gameEngine.mergedShipBitmap?.height ?: 0) / 2f,
            gameEngine.shipX + (gameEngine.mergedShipBitmap?.width ?: 0) / 2f,
            gameEngine.shipY + (gameEngine.mergedShipBitmap?.height ?: 0) / 2f
        )

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (shipRect.contains(event.rawX, event.rawY)) {
                    isDragging = true
                    if (BuildConfig.DEBUG) Timber.d("Started dragging ship")
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    gameEngine.shipX = event.rawX.coerceIn(0f + (gameEngine.mergedShipBitmap?.width ?: 0) / 2f, screenWidth - (gameEngine.mergedShipBitmap?.width ?: 0) / 2f)
                    gameEngine.shipY = event.rawY.coerceIn(0f + (gameEngine.mergedShipBitmap?.height ?: 0) / 2f, screenHeight - (gameEngine.mergedShipBitmap?.height ?: 0) / 2f)
                    // Spawn projectiles while dragging
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProjectileSpawnTime >= projectileSpawnInterval) {
                        gameEngine.spawnProjectile()
                        lastProjectileSpawnTime = currentTime
                        Timber.d("Spawned projectile at (x=${gameEngine.shipX}, y=${gameEngine.shipY - (gameEngine.mergedShipBitmap?.height ?: 0) / 2f})")
                    }
                    if (BuildConfig.DEBUG) Timber.d("Ship moved to (x=${gameEngine.shipX}, y=${gameEngine.shipY})")
                    invalidate()
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                if (BuildConfig.DEBUG) Timber.d("Stopped dragging ship")
                true
            }
            else -> false
        }
    }

    private fun startUpdateLoop() {
        updateRunnable?.let { removeCallbacks(it) }
        updateRunnable = object : Runnable {
            override fun run() {
                if (gameState == GameState.FLIGHT && visibility == View.VISIBLE) {
                    gameEngine.update(screenWidth, screenHeight, userId ?: "default")
                    invalidate()
                    if (BuildConfig.DEBUG) Timber.d("FlightView update loop: gameState=${gameState}, invalidated")
                    postDelayed(this, 16)
                } else {
                    if (BuildConfig.DEBUG) Timber.d("FlightView update loop skipped: gameState=${gameState}")
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

    private val gameState: GameState
        get() = gameEngine.gameState
}