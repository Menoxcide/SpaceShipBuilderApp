package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.example.spaceshipbuilderapp.GameEngine.Part
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BuildView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    @Inject lateinit var renderer: Renderer
    @Inject lateinit var gameEngine: GameEngine
    private val inputHandler by lazy { InputHandler(gameEngine) }
    private var screenWidth = 0f
    private var screenHeight = 0f
    private var statusBarHeight = 0f
    private val scope = CoroutineScope(Dispatchers.Main)
    private var wasCorrectOrder = false // Track previous state for toast

    val cockpitBitmap: Bitmap get() = renderer.cockpitBitmap
    val fuelTankBitmap: Bitmap get() = renderer.fuelTankBitmap
    val engineBitmap: Bitmap get() = renderer.engineBitmap

    init {
        gameEngine.setLaunchListener { isLaunching ->
            (context as? MainActivity)?.setSelectionPanelVisibility(!isLaunching)
            if (!isLaunching) renderer.clearParticles()
            (context as? MainActivity)?.setLaunchButtonVisibility(gameEngine.isShipInCorrectOrder() && gameEngine.parts.size == 3 && !isLaunching)
        }
        scope.launch {
            while (true) {
                gameEngine.update(screenWidth, screenHeight) // Pass screenWidth to satisfy method signature
                renderer.updateAnimationFrame()
                // Check for successful build and show toast
                val isCorrectOrder = gameEngine.isShipInCorrectOrder()
                if (isCorrectOrder && gameEngine.parts.size == 3 && !wasCorrectOrder) {
                    Toast.makeText(context, "Success!", Toast.LENGTH_SHORT).show()
                    (context as? MainActivity)?.setLaunchButtonVisibility(true)
                }
                wasCorrectOrder = isCorrectOrder && gameEngine.parts.size == 3
                (context as? MainActivity)?.setLaunchButtonVisibility(isCorrectOrder && gameEngine.parts.size == 3 && gameEngine.gameState == GameState.BUILD)
                postInvalidate()
                kotlinx.coroutines.delay(16) // ~60 FPS
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        gameEngine.initializePlaceholders(
            screenWidth,
            screenHeight,
            renderer.cockpitPlaceholderBitmap,
            renderer.fuelTankPlaceholderBitmap,
            renderer.enginePlaceholderBitmap
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight)

        renderer.drawPlaceholders(canvas, gameEngine.placeholders)
        renderer.drawParts(canvas, gameEngine.parts)
        gameEngine.selectedPart?.let { renderer.drawParts(canvas, listOf(it)) }
        if (gameEngine.isShipInCorrectOrder() && gameEngine.parts.size == 3) {
            renderer.drawSuccessAnimation(canvas, gameEngine.parts)
        }
        renderer.drawStats(canvas, gameEngine, statusBarHeight)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = inputHandler.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val hasHistory = event.historySize > 0
            val dx = if (hasHistory) event.getHistoricalX(0) - event.getX() else 0f
            val dy = if (hasHistory) event.getHistoricalY(0) - event.getY() else 0f
            if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                performClick()
            }
        }
        invalidate()
        return handled
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setSelectedPart(part: Part?) {
        gameEngine.selectedPart = part
        invalidate()
    }

    fun setStatusBarHeight(height: Int) {
        statusBarHeight = height.toFloat()
        invalidate()
    }

    fun launchShip(): Boolean {
        return gameEngine.launchShip(screenWidth, screenHeight).also { invalidate() }
    }

    fun rotatePart(partType: String) {
        gameEngine.rotatePart(partType)
        invalidate()
    }

    fun showLeaderboard() {
        (context as? MainActivity)?.showLeaderboard()
        invalidate()
    }

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        gameEngine.setLaunchListener(listener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}