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
import kotlin.random.Random

class BitmapManager @Inject constructor(
    private val context: Context
) {
    private val bitmaps: MutableMap<Int, Bitmap> = mutableMapOf()
    private val shipSets: MutableList<ShipSet> = mutableListOf()
    private val asteroidBitmaps: MutableList<Bitmap> = mutableListOf()

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
        bitmaps[R.drawable.power_up_icon] = loadBitmap(R.drawable.power_up_icon, "Power-up")
        bitmaps[R.drawable.shield_icon] = loadBitmap(R.drawable.shield_icon, "Shield")
        bitmaps[R.drawable.speed_icon] = loadBitmap(R.drawable.speed_icon, "Speed")
        bitmaps[R.drawable.stealth_icon] = loadBitmap(R.drawable.stealth_icon, "Stealth")
        bitmaps[R.drawable.warp_icon] = loadBitmap(R.drawable.warp_icon, "Warp")
        bitmaps[R.drawable.star_icon] = loadBitmap(R.drawable.star_icon, "Star")
        bitmaps[R.drawable.invincibility_icon] = loadBitmap(R.drawable.invincibility_icon, "Invincibility")
        bitmaps[R.drawable.enemy_ship] = loadBitmap(R.drawable.enemy_ship, "Enemy ship")
        bitmaps[R.drawable.boss_projectile] = loadBitmap(R.drawable.boss_projectile, "Boss projectile")
        bitmaps[R.drawable.homing_missile] = loadBitmap(R.drawable.homing_missile, "Homing missile")

        // Load all boss ship bitmaps
        bitmaps[R.drawable.boss_ship] = loadBitmap(R.drawable.boss_ship, "Boss ship (default)")
        bitmaps[R.drawable.boss_ship_1] = loadBitmap(R.drawable.boss_ship_1, "Boss ship 1")
        bitmaps[R.drawable.boss_ship_2] = loadBitmap(R.drawable.boss_ship_2, "Boss ship 2")
        bitmaps[R.drawable.boss_ship_3] = loadBitmap(R.drawable.boss_ship_3, "Boss ship 3")
        bitmaps[R.drawable.boss_ship_4] = loadBitmap(R.drawable.boss_ship_4, "Boss ship 4")
        bitmaps[R.drawable.boss_ship_5] = loadBitmap(R.drawable.boss_ship_5, "Boss ship 5")
        bitmaps[R.drawable.boss_ship_6] = loadBitmap(R.drawable.boss_ship_6, "Boss ship 6")

        // Load all asteroid bitmaps
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid, "Asteroid"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_1, "Asteroid_1"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_2, "Asteroid_2"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_3, "Asteroid_3"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_4, "Asteroid_4"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_5, "Asteroid_5"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_6, "Asteroid_6"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_7, "Asteroid_7"))
        asteroidBitmaps.add(loadBitmap(R.drawable.asteroid_8, "Asteroid_8"))
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

    fun getBossShipBitmap(tier: Int): Bitmap {
        val resourceId = when (tier) {
            1 -> R.drawable.boss_ship_1
            2 -> R.drawable.boss_ship_2
            3 -> R.drawable.boss_ship_3
            4 -> R.drawable.boss_ship_4
            5 -> R.drawable.boss_ship_5
            6 -> R.drawable.boss_ship_6
            else -> R.drawable.boss_ship // Default for tier 0 or invalid tiers
        }
        return getBitmap(resourceId, "Boss ship tier $tier")
    }

    fun getRandomAsteroidBitmap(): Bitmap {
        val index = Random.nextInt(asteroidBitmaps.size)
        val bitmap = asteroidBitmaps[index]
        if (bitmap.isRecycled) {
            Timber.w("Asteroid bitmap $index was recycled, reinitializing")
            asteroidBitmaps[index] = loadBitmap(
                when (index) {
                    0 -> R.drawable.asteroid
                    1 -> R.drawable.asteroid_1
                    2 -> R.drawable.asteroid_2
                    3 -> R.drawable.asteroid_3
                    4 -> R.drawable.asteroid_4
                    5 -> R.drawable.asteroid_5
                    6 -> R.drawable.asteroid_6
                    7 -> R.drawable.asteroid_7
                    8 -> R.drawable.asteroid_8
                    else -> R.drawable.asteroid // Fallback
                },
                "Asteroid_$index"
            )
        }
        return asteroidBitmaps[index]
    }

    fun getAsteroidBitmap(spriteId: Int): Bitmap {
        val index = spriteId.coerceIn(0, asteroidBitmaps.size - 1) // Ensure the index is within bounds
        val bitmap = asteroidBitmaps[index]
        if (bitmap.isRecycled) {
            Timber.w("Asteroid bitmap $index was recycled, reinitializing")
            asteroidBitmaps[index] = loadBitmap(
                when (index) {
                    0 -> R.drawable.asteroid
                    1 -> R.drawable.asteroid_1
                    2 -> R.drawable.asteroid_2
                    3 -> R.drawable.asteroid_3
                    4 -> R.drawable.asteroid_4
                    5 -> R.drawable.asteroid_5
                    6 -> R.drawable.asteroid_6
                    7 -> R.drawable.asteroid_7
                    8 -> R.drawable.asteroid_8
                    else -> R.drawable.asteroid // Fallback
                },
                "Asteroid_$index"
            )
        }
        return asteroidBitmaps[index]
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
        asteroidBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        bitmaps.clear()
        asteroidBitmaps.clear()
        Timber.d("BitmapManager onDestroy called")
    }
}