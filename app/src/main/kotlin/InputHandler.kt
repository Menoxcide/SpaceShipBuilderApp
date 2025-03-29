package com.example.spaceshipbuilderapp

import android.view.MotionEvent
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.hypot

class InputHandler @Inject constructor(
    private val gameEngine: GameEngine,
    private val renderer: Renderer,
    private val gameStateManager: GameStateManager,
    private val buildModeManager: BuildModeManager // Inject BuildModeManager
) {
    companion object {
        const val SNAP_RANGE = 200f
        const val OVERLAP_THRESHOLD = 50f
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameStateManager.gameState != GameState.BUILD) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                true
            }
            MotionEvent.ACTION_MOVE -> {
                buildModeManager.selectedPart?.let { part ->
                    part.x = event.rawX
                    part.y = event.rawY
                    if (BuildConfig.DEBUG) Timber.d("Dragging ${part.type} to (x=${part.x}, y=${part.y})")
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val part = buildModeManager.selectedPart ?: run {
                    if (BuildConfig.DEBUG) Timber.d("No selected part on ACTION_UP")
                    return false
                }
                getTargetPosition(part.type)?.let { (targetX, targetY) ->
                    val distance = hypot(part.x - targetX, part.y - targetY)
                    if (BuildConfig.DEBUG) Timber.d("Checking snap for ${part.type}: distance=$distance, targetX=$targetX, targetY=$targetY")
                    if (distance < SNAP_RANGE && !checkOverlap(targetX, targetY, part)) {
                        buildModeManager.parts.removeAll { it.type == part.type || (it.x == part.x && it.y == part.y) }
                        part.x = targetX
                        part.y = targetY
                        part.scale = buildModeManager.placeholders.find { it.type == part.type }?.scale ?: 1f
                        buildModeManager.parts.add(part)
                        if (BuildConfig.DEBUG) Timber.d("Snapped ${part.type} to (x=${part.x}, y=${part.y}) with scale=${part.scale}, parts count: ${buildModeManager.parts.size}")

                        // Check if ship is spaceworthy and trigger celebratory particles
                        if (buildModeManager.parts.size == 3 && gameEngine.isShipSpaceworthy(gameEngine.screenHeight)) {
                            val shipCenterX = gameEngine.screenWidth / 2f
                            val shipCenterY = (buildModeManager.cockpitY + buildModeManager.engineY) / 2f
                            renderer.particleSystem.addCollectionParticles(shipCenterX, shipCenterY)
                            Timber.d("Ship fully assembled and spaceworthy! Triggering celebratory particles at (x=$shipCenterX, y=$shipCenterY)")
                        }
                        true
                    } else {
                        if (BuildConfig.DEBUG) Timber.d("Invalid placement - Distance=$distance, Overlap=${checkOverlap(targetX, targetY, part)}")
                        buildModeManager.parts.remove(part)
                        false
                    }
                } ?: run {
                    if (BuildConfig.DEBUG) Timber.d("No target position found for ${part.type}")
                    buildModeManager.parts.remove(part)
                    false
                }.also {
                    buildModeManager.selectedPart = null
                    if (BuildConfig.DEBUG) Timber.d("Cleared selected part")
                    // Force recheck of launch button visibility
                    gameEngine.notifyLaunchListener()
                }
            }
            else -> false
        }
    }

    fun getTargetPosition(partType: String): Pair<Float, Float>? {
        return buildModeManager.placeholders.find { it.type == partType }?.let { Pair(it.x, it.y) }
            .also { if (BuildConfig.DEBUG && it == null) Timber.d("No placeholder found for $partType") }
    }

    fun checkOverlap(x: Float, y: Float, part: Part): Boolean {
        val overlap = buildModeManager.parts.any { existingPart ->
            existingPart != part && hypot(existingPart.x - x, existingPart.y - y) < OVERLAP_THRESHOLD
        }
        if (BuildConfig.DEBUG && overlap) Timber.d("Overlap detected at (x=$x, y=$y)")
        return overlap
    }
}