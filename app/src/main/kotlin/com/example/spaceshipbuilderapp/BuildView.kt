package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import timber.log.Timber
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BuildView @Inject constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var renderer: Renderer
    @Inject lateinit var inputHandler: InputHandler

    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    private var statusBarHeight: Float = 0f
    private var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private var lastParticleTriggerTime = 0L
    private var isLaunchButtonVisible = false
    private var isSpaceworthy = false

    init {
        if (BuildConfig.DEBUG) Timber.d("BuildView initialized")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        gameEngine.setScreenDimensions(screenWidth, screenHeight, statusBarHeight)
        gameEngine.initializePlaceholders(
            screenWidth,
            screenHeight,
            renderer.cockpitPlaceholderBitmap,
            renderer.fuelTankPlaceholderBitmap,
            renderer.enginePlaceholderBitmap,
            statusBarHeight
        )
        if (BuildConfig.DEBUG) Timber.d("BuildView size changed: w=$w, h=$h")
    }

    fun setStatusBarHeight(height: Float) {
        statusBarHeight = height
        gameEngine.setScreenDimensions(screenWidth, screenHeight, statusBarHeight)
        gameEngine.initializePlaceholders(
            screenWidth,
            screenHeight,
            renderer.cockpitPlaceholderBitmap,
            renderer.fuelTankPlaceholderBitmap,
            renderer.enginePlaceholderBitmap,
            statusBarHeight
        )
        if (BuildConfig.DEBUG) Timber.d("BuildView status bar height set to: $height")
    }

    fun setSelectedPart(part: GameEngine.Part) {
        gameEngine.selectedPart = part
        invalidate()
        if (BuildConfig.DEBUG) Timber.d("Selected part set to ${part.type} at (x=${part.x}, y=${part.y})")
    }

    fun launchShip(): Boolean {
        val success = gameEngine.launchShip(screenWidth, screenHeight)
        if (success) {
            gameEngine.gameState = GameState.FLIGHT
            isVisible = false
            if (BuildConfig.DEBUG) Timber.d("BuildView hidden, launching to Flight mode")
        }
        invalidate()
        return success
    }

    override fun onDraw(canvas: Canvas) {
        if (gameState != GameState.BUILD || !isVisible) {
            Timber.w("onDraw skipped: gameState=${gameState}, visibility=$isVisible")
            return
        }
        super.onDraw(canvas)
        Timber.d("onDraw called, visibility=$isVisible, gameState=${gameState}")

        renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight, gameEngine.level)
        renderer.drawPlaceholders(canvas, gameEngine.placeholders)
        renderer.drawParts(canvas, gameEngine.parts)
        gameEngine.selectedPart?.let { renderer.drawParts(canvas, listOf(it)) }
        renderer.drawStats(canvas, gameEngine, statusBarHeight)
        renderer.drawShip(
            canvas,
            gameEngine,
            gameEngine.parts,
            screenWidth,
            screenHeight,
            screenWidth / 2f,
            screenHeight / 2f,
            gameState,
            gameEngine.mergedShipBitmap,
            gameEngine.placeholders
        )

        val currentSpaceworthy = gameEngine.parts.size == 3 && gameEngine.isShipSpaceworthy(screenHeight)
        if (currentSpaceworthy && !isSpaceworthy) {
            val shipCenterX = gameEngine.screenWidth / 2f
            val shipCenterY = (gameEngine.cockpitY + gameEngine.engineY) / 2f
            renderer.particleSystem.addCollectionParticles(shipCenterX, shipCenterY)
            Timber.d("Ship became spaceworthy, immediately triggering celebratory particles at (x=$shipCenterX, y=$shipCenterY)")
            lastParticleTriggerTime = System.currentTimeMillis()
            isSpaceworthy = true
        } else if (!currentSpaceworthy && isSpaceworthy) {
            isSpaceworthy = false
            Timber.d("Ship no longer spaceworthy")
        }

        if (currentSpaceworthy) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastParticleTriggerTime >= 1000) {
                val shipCenterX = gameEngine.screenWidth / 2f
                val shipCenterY = (gameEngine.cockpitY + gameEngine.engineY) / 2f
                renderer.particleSystem.addCollectionParticles(shipCenterX, shipCenterY)
                lastParticleTriggerTime = currentTime
                Timber.d("Periodic celebratory particles triggered at (x=$shipCenterX, y=$shipCenterY)")
            }
        }

        val isLaunchReady = currentSpaceworthy && gameState == GameState.BUILD
        if (isLaunchReady != isLaunchButtonVisible) {
            (context as? MainActivity)?.setLaunchButtonVisibility(isLaunchReady)
            isLaunchButtonVisible = isLaunchReady
            Timber.d("Forced launch button visibility update to $isLaunchReady in BuildView")
        }

        Timber.d("Rendered frame in BuildView with parts count: ${gameEngine.parts.size}, parts: ${gameEngine.parts.map { "${it.type} at y=${it.y}" }}")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState != GameState.BUILD || !isVisible) return false

        val handled = inputHandler.onTouchEvent(event)
        if (handled) {
            invalidate()
            if (BuildConfig.DEBUG) Timber.d("Touch event handled by InputHandler, invalidating view")
        }
        return handled
    }

    override fun performClick(): Boolean {
        if (BuildConfig.DEBUG) Timber.d("BuildView performClick called")
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (BuildConfig.DEBUG) Timber.d("BuildView detached from window")
    }

    private val gameState: GameState
        get() = gameEngine.gameState
}