package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.spaceshipbuilderapp.BuildConfig
import com.example.spaceshipbuilderapp.GameEngine.Part
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FlightView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    @Inject lateinit var renderer: Renderer
    @Inject lateinit var gameEngine: GameEngine
    private var screenWidth = 0f
    private var screenHeight = 0f
    private var statusBarHeight = 0f
    private val scope = CoroutineScope(Dispatchers.Main)
    private var touchX = 0f
    private var touchY = 0f
    private var isTouching = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        Timber.d("FlightView initialized, visibility=$visibility")

        // Force a layout pass to ensure onSizeChanged is called
        post {
            if (screenWidth == 0f || screenHeight == 0f) {
                screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
                screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
                Timber.w("onSizeChanged not called, manually setting dimensions: width=$screenWidth, height=$screenHeight")
                gameEngine.setScreenDimensions(screenWidth, screenHeight)
                invalidate()
            }
        }

        scope.launch {
            Timber.d("FlightView coroutine started")
            while (true) {
                if (gameEngine.gameState == GameState.FLIGHT) {
                    gameEngine.update(screenWidth, screenHeight)
                    renderer.updateAnimationFrame()
                    invalidate()
                    Timber.d("FlightView update loop: gameState=${gameEngine.gameState}, invalidated")
                } else {
                    Timber.d("FlightView update loop skipped: gameState=${gameEngine.gameState}")
                }
                kotlinx.coroutines.delay(16) // ~60 FPS
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Timber.d("FlightView attached to window, visibility=$visibility")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        Timber.d("FlightView size changed: width=$screenWidth, height=$screenHeight, statusBarHeight=$statusBarHeight")
        gameEngine.setScreenDimensions(screenWidth, screenHeight)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gameEngine.gameState == GameState.FLIGHT && visibility == View.VISIBLE) {
            Timber.d("onDraw called, visibility=$visibility, gameState=${gameEngine.gameState}")
            renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight)
            renderer.drawShip(canvas, gameEngine.parts, screenWidth, screenHeight, gameEngine.shipX, gameEngine.shipY)
            Timber.d("Drawing ${gameEngine.powerUps.size} power-ups")
            renderer.drawPowerUps(canvas, gameEngine.powerUps, statusBarHeight)
            Timber.d("Drawing ${gameEngine.asteroids.size} asteroids")
            renderer.drawAsteroids(canvas, gameEngine.asteroids, statusBarHeight)
            renderer.drawStats(canvas, gameEngine, statusBarHeight)
            Timber.d("Rendered frame in FlightView with ship at (x=${gameEngine.shipX}, y=${gameEngine.shipY})")
        } else {
            Timber.w("onDraw skipped: gameState=${gameEngine.gameState}, visibility=$visibility")
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (BuildConfig.DEBUG) Timber.d("Touch event received: action=${event.actionMasked}")
        val handled = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                isTouching = true
                if (BuildConfig.DEBUG) Timber.d("Touch down at (x=${touchX}, y=${touchY})")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouching && gameEngine.gameState == GameState.FLIGHT) {
                    val dx = event.x - touchX
                    val dy = event.y - touchY
                    touchX = event.x
                    touchY = event.y
                    if (BuildConfig.DEBUG) Timber.d("Touch move: dx=$dx, dy=$dy")
                    val threshold = 10f
                    if (Math.abs(dx) > threshold || Math.abs(dy) > threshold) {
                        if (Math.abs(dx) > Math.abs(dy)) {
                            if (dx > threshold) {
                                gameEngine.moveShip(3) // Right
                                if (BuildConfig.DEBUG) Timber.d("Moving right: dx=$dx")
                            } else if (dx < -threshold) {
                                gameEngine.moveShip(2) // Left
                                if (BuildConfig.DEBUG) Timber.d("Moving left: dx=$dx")
                            }
                        } else {
                            if (dy > threshold) {
                                gameEngine.moveShip(1) // Down
                                if (BuildConfig.DEBUG) Timber.d("Moving down: dy=$dy")
                            } else if (dy < -threshold) {
                                gameEngine.moveShip(0) // Up
                                if (BuildConfig.DEBUG) Timber.d("Moving up: dy=$dy")
                            }
                        }
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                isTouching = false
                gameEngine.stopShip()
                if (BuildConfig.DEBUG) Timber.d("Touch up, stopping ship")
                true
            }
            else -> false
        }
        invalidate()
        return handled
    }

    fun setStatusBarHeight(height: Int) {
        statusBarHeight = height.toFloat()
        Timber.d("Status bar height set to: $statusBarHeight")
        invalidate()
    }

    fun setGameMode(mode: GameState) {
        if (mode == GameState.FLIGHT) {
            visibility = View.VISIBLE
            requestFocus()
            postInvalidate()
            Timber.d("FlightView set to FLIGHT mode, visibility=$visibility")
        } else {
            visibility = View.GONE
            Timber.d("FlightView set to $mode, visibility=$visibility")
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        Timber.d("FlightView detached from window")
    }
}