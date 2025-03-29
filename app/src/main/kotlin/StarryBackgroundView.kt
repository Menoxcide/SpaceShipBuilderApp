package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class StarryBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    @Inject lateinit var renderer: Renderer
    @Inject lateinit var gameEngine: GameEngine

    private var screenWidth: Float = 0f
    private var screenHeight: Float = 0f
    private var statusBarHeight: Float = 0f

    init {
        if (BuildConfig.DEBUG) Timber.d("StarryBackgroundView initialized")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        statusBarHeight = resources.getDimensionPixelSize(
            resources.getIdentifier("status_bar_height", "dimen", "android")
        ).toFloat()
        Timber.d("StarryBackgroundView size changed: w=$w, h=$h")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.drawBackground(canvas, screenWidth, screenHeight, statusBarHeight, gameEngine.level)
    }
}