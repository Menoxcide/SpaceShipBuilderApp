package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import timber.log.Timber
import javax.inject.Inject

class BitmapManager @Inject constructor(
    private val context: Context
) {
    private val bitmaps: MutableMap<Int, Bitmap> = mutableMapOf()
    private val shipSets: MutableList<ShipSet> = mutableListOf()

    init {
        // Load ship sets
        shipSets.add(
            ShipSet(
                cockpit = loadBitmap(R.drawable.cockpit, "Cockpit"),
                fuelTank = loadBitmap(R.drawable.fuel_tank, "Fuel tank"),
                engine = loadBitmap(R.drawable.engine, "Engine")
            )
        )
        shipSets.add(
            ShipSet(
                cockpit = loadBitmap(R.drawable.cockpit_1, "Cockpit_1"),
                fuelTank = loadBitmap(R.drawable.fuel_tank_1, "Fuel_tank_1"),
                engine = loadBitmap(R.drawable.engine_1, "Engine_1")
            )
        )
        shipSets.add(
            ShipSet(
                cockpit = loadBitmap(R.drawable.cockpit_2, "Cockpit_2"),
                fuelTank = loadBitmap(R.drawable.fuel_tank_2, "Fuel_tank_2"),
                engine = loadBitmap(R.drawable.engine_2, "Engine_2")
            )
        )

        // Load other bitmaps
        bitmaps[R.drawable.power_up] = loadBitmap(R.drawable.power_up, "Power-up")
        bitmaps[R.drawable.shield_icon] = loadBitmap(R.drawable.shield_icon, "Shield")
        bitmaps[R.drawable.speed_icon] = loadBitmap(R.drawable.speed_icon, "Speed")
        bitmaps[R.drawable.stealth_icon] = loadBitmap(R.drawable.stealth_icon, "Stealth")
        bitmaps[R.drawable.warp_icon] = loadBitmap(R.drawable.warp_icon, "Warp")
        bitmaps[R.drawable.star] = loadBitmap(R.drawable.star, "Star")
        bitmaps[R.drawable.asteroid] = loadBitmap(R.drawable.asteroid, "Asteroid")
        bitmaps[R.drawable.invincibility_icon] = loadBitmap(R.drawable.invincibility_icon, "Invincibility")
        bitmaps[R.drawable.enemy_ship] = loadBitmap(R.drawable.enemy_ship, "Enemy ship")
        bitmaps[R.drawable.boss_projectile] = loadBitmap(R.drawable.boss_projectile, "Boss projectile")
        bitmaps[R.drawable.homing_missile] = loadBitmap(R.drawable.homing_missile, "Homing missile")

        // Load boss ship bitmaps
        bitmaps[R.drawable.boss_ship_1] = loadBitmap(R.drawable.boss_ship_1, "Boss ship 1")
        bitmaps[R.drawable.boss_ship_2] = loadBitmap(R.drawable.boss_ship_2, "Boss ship 2")
        bitmaps[R.drawable.boss_ship_3] = loadBitmap(R.drawable.boss_ship_3, "Boss ship 3")
    }

    data class ShipSet(val cockpit: Bitmap, val fuelTank: Bitmap, val engine: Bitmap)

    private fun loadBitmap(resourceId: Int, name: String): Bitmap {
        return BitmapFactory.decodeResource(context.resources, resourceId)
            ?: throw IllegalStateException("$name bitmap not found")
    }

    fun getBitmap(resourceId: Int, name: String): Bitmap {
        val bitmap = bitmaps[resourceId]
        if (bitmap == null || bitmap.isRecycled) {
            Timber.w("$name bitmap was recycled or not found, reinitializing")
            bitmaps[resourceId] = loadBitmap(resourceId, name)
        }
        return bitmaps[resourceId]!!
    }

    fun getShipSet(index: Int): ShipSet {
        return shipSets.getOrNull(index) ?: shipSets[0]
    }

    fun createPlaceholderBitmap(original: Bitmap): Bitmap {
        return Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                val hologramPaint = Paint().apply {
                    color = Color.argb(100, 0, 255, 0)
                    alpha = 128
                }
                drawBitmap(original, 0f, 0f, hologramPaint)
            }
        }
    }

    fun onDestroy() {
        bitmaps.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        bitmaps.clear()
        Timber.d("BitmapManager onDestroy called")
    }
}