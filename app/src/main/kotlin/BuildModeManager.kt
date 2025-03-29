package com.example.spaceshipbuilderapp

import android.graphics.Bitmap
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

class BuildModeManager @Inject constructor(
    private val renderer: Renderer
) {
    companion object {
        const val ALIGNMENT_THRESHOLD = 75f
        const val PANEL_HEIGHT = 150f
    }

    val parts = mutableListOf<Part>()
    val placeholders = mutableListOf<Part>()
    var selectedPart: Part? = null
    var cockpitY: Float = 0f
    var fuelTankY: Float = 0f
    var engineY: Float = 0f

    fun initializePlaceholders(
        screenWidth: Float,
        screenHeight: Float,
        cockpitPlaceholderBitmap: Bitmap,
        fuelTankPlaceholderBitmap: Bitmap,
        enginePlaceholderBitmap: Bitmap,
        statusBarHeight: Float
    ) {
        val contentHeight = screenHeight - statusBarHeight - PANEL_HEIGHT
        val cockpitHeight = renderer.cockpitBitmap.height.toFloat()
        val fuelTankHeight = renderer.fuelTankBitmap.height.toFloat()
        val engineHeight = renderer.engineBitmap.height.toFloat()

        val totalHeight = cockpitHeight + fuelTankHeight + engineHeight
        val startY = statusBarHeight + (contentHeight - totalHeight) / 2f

        cockpitY = startY + cockpitHeight / 2f
        fuelTankY = cockpitY + cockpitHeight / 2f + fuelTankHeight / 2f
        engineY = fuelTankY + fuelTankHeight / 2f + engineHeight / 2f

        placeholders.clear()
        placeholders.add(Part("cockpit", cockpitPlaceholderBitmap, screenWidth / 2f, cockpitY, 0f, 1f))
        placeholders.add(Part("fuel_tank", fuelTankPlaceholderBitmap, screenWidth / 2f, fuelTankY, 0f, 1f))
        placeholders.add(Part("engine", enginePlaceholderBitmap, screenWidth / 2f, engineY, 0f, 1f))
        Timber.d("Placeholders initialized: cockpitY=$cockpitY, fuelTankY=$fuelTankY, engineY=$engineY")
    }

    fun isShipSpaceworthy(screenHeight: Float, statusBarHeight: Float): Boolean {
        if (parts.size != 3) return false
        val sortedParts = parts.sortedBy { it.y }
        val isValidOrder = sortedParts[0].type == "cockpit" && sortedParts[1].type == "fuel_tank" && sortedParts[2].type == "engine"
        val isAlignedY = abs(sortedParts[0].y - cockpitY) <= ALIGNMENT_THRESHOLD &&
                abs(sortedParts[1].y - fuelTankY) <= ALIGNMENT_THRESHOLD &&
                abs(sortedParts[2].y - engineY) <= ALIGNMENT_THRESHOLD
        val maxAllowedY = screenHeight - PANEL_HEIGHT - statusBarHeight
        val isWithinBuildArea = sortedParts.all { it.y < maxAllowedY }
        return isValidOrder && isAlignedY && isWithinBuildArea
    }

    fun getSpaceworthinessFailureReason(screenHeight: Float, statusBarHeight: Float): String {
        val sortedParts = parts.sortedBy { it.y }
        return when {
            parts.size != 3 -> "Need exactly 3 parts! Current parts: ${parts.size}"
            sortedParts[0].type != "cockpit" -> "Cockpit must be topmost! Top part: ${sortedParts[0].type}"
            sortedParts[1].type != "fuel_tank" -> "Fuel tank must be middle! Middle part: ${sortedParts[1].type}"
            sortedParts[2].type != "engine" -> "Engine must be bottom! Bottom part: ${sortedParts[2].type}"
            abs(sortedParts[0].y - cockpitY) > ALIGNMENT_THRESHOLD -> "Misaligned: Cockpit not at placeholder position!"
            abs(sortedParts[1].y - fuelTankY) > ALIGNMENT_THRESHOLD -> "Misaligned: Fuel Tank not at placeholder position!"
            abs(sortedParts[2].y - engineY) > ALIGNMENT_THRESHOLD -> "Misaligned: Engine not at placeholder position!"
            sortedParts.any { it.y >= screenHeight - PANEL_HEIGHT - statusBarHeight } -> "Parts must be above the panel!"
            else -> "Unknown failure!"
        }
    }

    fun isShipInCorrectOrder(): Boolean {
        if (parts.size != 3) return false
        val sortedParts = parts.sortedBy { it.y }
        return sortedParts[0].type == "cockpit" &&
                sortedParts[1].type == "fuel_tank" &&
                sortedParts[2].type == "engine"
    }

    fun rotatePart(partType: String) {
        parts.find { it.type == partType }?.let {
            it.rotation = (it.rotation + 90f) % 360f
            Timber.d("Rotated $partType to ${it.rotation} degrees")
        } ?: Timber.w("Part $partType not found")
    }

    fun getAndClearParts(): List<Part> {
        val partsCopy = parts.toList()
        parts.clear()
        return partsCopy
    }
}