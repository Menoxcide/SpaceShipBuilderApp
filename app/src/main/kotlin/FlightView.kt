package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button
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

    private var destroyAllButton: Button? = null

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

    fun setDestroyAllButton(button: Button) {
        destroyAllButton = button
        updateDestroyAllButton()
        destroyAllButton?.setOnClickListener {
            if (gameEngine.destroyAll()) {
                updateDestroyAllButton()
            }
            Timber.d("Destroy All button clicked")
        }
    }

    private fun updateDestroyAllButton() {
        destroyAllButton?.let { button ->
            button.text = "Destroy All (${gameEngine.destroyAllCharges}/3)"
            button.isEnabled = gameEngine.destroyAllCharges > 0
            button.visibility = if (gameEngine.isDestroyAllUnlocked) View.VISIBLE else View.GONE
        }
    }

    fun setGameMode(mode: GameState) {
        if (mode == GameState.FLIGHT) {
            visibility = View.VISIBLE
            gameEngine.shipX = screenWidth / 2f
            gameEngine.shipY = screenHeight / 2f
            gameEngine.onPowerUpCollectedListener = { x, y ->
                renderer.particleSystem.addCollectionParticles(x, y)
            }
            updateDestroyAllButton()
            startUpdateLoop()
            if (BuildConfig.DEBUG) Timber.d("FlightView activated, starting update loop")
        } else {
            visibility = View.GONE
            destroyAllButton?.visibility = View.GONE
            stopUpdateLoop()
            if (BuildConfig.DEBUG) Timber.d("FlightView deactivated")
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (gameState != GameState.FLIGHT || visibility != View.VISIBLE) {
            Timber.w("onDraw skipped: gameState=$gameState, visibility=$visibility")
            return
        }
        super.onDraw(canvas)
        Timber.d("onDraw called, visibility=$visibility, gameState=$gameState")
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
        renderer.drawEnemyShips(canvas, gameEngine.enemyShips, statusBarHeight)
        renderer.drawBoss(canvas, gameEngine.getBoss(), statusBarHeight)
        renderer.drawEnemyProjectiles(canvas, gameEngine.enemyProjectiles, statusBarHeight)
        renderer.drawHomingProjectiles(canvas, gameEngine.homingProjectiles, statusBarHeight)
        renderer.drawStats(canvas, gameEngine, statusBarHeight, gameState)
        Timber.d("Rendered frame in FlightView with ship at (x=${gameEngine.shipX}, y=${gameEngine.shipY})")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState != GameState.FLIGHT || visibility != View.VISIBLE) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.rawX
                val touchY = event.rawY

                // Check for enemy ship click
                for (enemy in gameEngine.enemyShips) {
                    val enemyRect = RectF(
                        enemy.x - 75f,
                        enemy.y - 75f,
                        enemy.x + 75f,
                        enemy.y + 75f
                    )
                    if (enemyRect.contains(touchX, touchY)) {
                        gameEngine.launchHomingMissile(enemy)
                        Timber.d("Clicked enemy ship at (x=${enemy.x}, y=${enemy.y}) with tolerance, launching homing missile")
                        return true
                    }
                }

                // Check for boss click
                gameEngine.getBoss()?.let { boss ->
                    val bossRect = RectF(
                        boss.x - 100f,
                        boss.y - 100f,
                        boss.x + 100f,
                        boss.y + 100f
                    )
                    if (bossRect.contains(touchX, touchY)) {
                        gameEngine.launchHomingMissile(boss)
                        Timber.d("Clicked boss at (x=${boss.x}, y=${boss.y}) with tolerance, launching homing missile")
                        return true
                    }
                }

                // Check for ship drag
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
                if (gameState == GameState.FLIGHT && visibility == View.VISIBLE) {
                    gameEngine.update(screenWidth, screenHeight, userId ?: "default")
                    invalidate()
                    if (BuildConfig.DEBUG) Timber.d("FlightView update loop: gameState=$gameState, invalidated")
                    postDelayed(this, 16)
                } else {
                    if (BuildConfig.DEBUG) Timber.d("FlightView update loop skipped: gameState=$gameState")
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