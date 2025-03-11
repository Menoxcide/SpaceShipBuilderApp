package com.example.spaceshipbuilderapp

import android.view.MotionEvent
import com.example.spaceshipbuilderapp.BuildConfig
import com.example.spaceshipbuilderapp.GameEngine.Part
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.hypot

class InputHandler @Inject constructor(private val gameEngine: GameEngine) {
    companion object {
        const val SNAP_RANGE = 200f
        const val OVERLAP_THRESHOLD = 50f
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameEngine.gameState != GameState.BUILD) return false // Disable in FLIGHT mode
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> true // Handled by MainActivity
            MotionEvent.ACTION_MOVE -> {
                gameEngine.selectedPart?.let { part ->
                    part.x = event.rawX
                    part.y = event.rawY
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val part = gameEngine.selectedPart ?: return false
                getTargetPosition(part.type)?.let { (targetX, targetY) ->
                    val distance = hypot(part.x - targetX, part.y - targetY)
                    if (distance < SNAP_RANGE && !checkOverlap(targetX, targetY, part)) {
                        part.x = targetX
                        part.y = targetY
                        part.scale = gameEngine.placeholders.find { it.type == part.type }?.scale ?: 1f
                        gameEngine.parts.removeAll { it.type == part.type }
                        gameEngine.parts.add(part)
                        if (BuildConfig.DEBUG) Timber.d("Snapped ${part.type} at (x=$targetX, y=$targetY) with scale=${part.scale}")
                        true
                    } else {
                        if (BuildConfig.DEBUG) Timber.d("Invalid placement - Out of range or overlap detected")
                        gameEngine.parts.remove(part)
                        false
                    }
                } ?: run {
                    gameEngine.parts.remove(part)
                    false
                }.also {
                    gameEngine.selectedPart = null
                }
            }
            else -> false
        }
    }

    private fun getTargetPosition(partType: String): Pair<Float, Float>? {
        return gameEngine.placeholders.find { it.type == partType }?.let { Pair(it.x, it.y) }
    }

    private fun checkOverlap(x: Float, y: Float, part: Part): Boolean {
        return gameEngine.parts.any { it != part && hypot(it.x - x, it.y - y) < OVERLAP_THRESHOLD }
    }
}