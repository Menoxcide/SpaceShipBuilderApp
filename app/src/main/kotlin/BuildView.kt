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
    @Inject lateinit var gameStateManager: GameStateManager
    @Inject lateinit var buildModeManager: BuildModeManager

    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    private var statusBarHeight: Float = 0f
    private var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private var lastParticleTriggerTime = 0L
    private var isLaunchButtonVisible = false
    private var isSpaceworthy = false
    private var shouldRedraw = false // Flag to control redraws
    private var isInitialized = false // New flag to delay drawing until ready

    init {
        if (BuildConfig.DEBUG) Timber.d("BuildView initialized")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        gameEngine.setScreenDimensions(screenWidth, screenHeight, statusBarHeight)
        if (BuildConfig.DEBUG) Timber.d("BuildView size changed: w=$w, h=$h")
    }

    fun setStatusBarHeight(height: Float) {
        statusBarHeight = height
        gameEngine.setScreenDimensions(screenWidth, screenHeight, statusBarHeight)
        if (BuildConfig.DEBUG) Timber.d("BuildView status bar height set to: $height")
    }

    fun setSelectedPart(part: Part?) {
        buildModeManager.selectedPart = part
        invalidate()
        if (BuildConfig.DEBUG) Timber.d("Selected part set to ${part?.type} at (x=${part?.x}, y=${part?.y})")
    }

    fun launchShip(): Boolean {
        val success = gameEngine.launchShip(screenWidth, screenHeight)
        if (success) {
            isVisible = false
            if (BuildConfig.DEBUG) Timber.d("BuildView hidden, launching to Flight mode")
        }
        invalidate()
        return success
    }

    // New method to signal initialization completion
    fun setInitialized() {
        isInitialized = true
        invalidate()
        Timber.d("BuildView initialized, triggering redraw")
    }

    override fun onDraw(canvas: Canvas) {
        if (!isInitialized || gameStateManager.gameState != GameState.BUILD || !isVisible) {
            Timber.w("onDraw skipped: isInitialized=$isInitialized, gameState=${gameStateManager.gameState}, visibility=$isVisible")
            shouldRedraw = false
            return
        }
        super.onDraw(canvas)
        Timber.d("onDraw called, visibility=$isVisible, gameState=${gameStateManager.gameState}")

        // Draw background (including stars) first, passing the current environment
        renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight, gameEngine.level, gameEngine.currentEnvironment)
        renderer.drawPlaceholders(canvas, buildModeManager.placeholders)
        renderer.drawParts(canvas, buildModeManager.parts)
        buildModeManager.selectedPart?.let { renderer.drawParts(canvas, listOf(it)) }
        renderer.drawShip(
            canvas,
            gameEngine,
            buildModeManager.parts,
            screenWidth,
            screenHeight,
            screenWidth / 2f,
            screenHeight / 2f,
            gameStateManager.gameState,
            gameEngine.mergedShipBitmap,
            buildModeManager.placeholders
        )

        val currentSpaceworthy = buildModeManager.parts.size == 3 && gameEngine.isShipSpaceworthy(screenHeight)
        if (currentSpaceworthy && !isSpaceworthy) {
            val shipCenterX = gameEngine.screenWidth / 2f
            val shipCenterY = (buildModeManager.cockpitY + buildModeManager.engineY) / 2f
            renderer.shipRendererInstance.addCollectionParticles(shipCenterX, shipCenterY)
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
                val shipCenterY = (buildModeManager.cockpitY + buildModeManager.engineY) / 2f
                renderer.shipRendererInstance.addCollectionParticles(shipCenterX, shipCenterY)
                lastParticleTriggerTime = currentTime
                Timber.d("Periodic celebratory particles triggered at (x=$shipCenterX, y=$shipCenterY)")
            }
        }

        val isLaunchReady = currentSpaceworthy && gameStateManager.gameState == GameState.BUILD
        if (isLaunchReady != isLaunchButtonVisible) {
            (context as? MainActivity)?.setLaunchButtonVisibility(isLaunchReady)
            isLaunchButtonVisible = isLaunchReady
            Timber.d("Forced launch button visibility update to $isLaunchReady in BuildView")
        }

        // Log the paused state before rendering stats
        val pausedState = gameStateManager.getPausedState()
        Timber.d("BuildView onDraw: Paused state = $pausedState")
        renderer.drawStats(canvas, gameEngine, statusBarHeight, gameStateManager.gameState)

        Timber.d("Rendered frame in BuildView with parts count: ${buildModeManager.parts.size}, parts: ${buildModeManager.parts.map { "${it.type} at y=${it.y}" }}")
        shouldRedraw = false // Reset the redraw flag after drawing
    }

    override fun invalidate() {
        if (!shouldRedraw) {
            shouldRedraw = true
            super.invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameStateManager.gameState != GameState.BUILD || !isVisible) return false

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
}