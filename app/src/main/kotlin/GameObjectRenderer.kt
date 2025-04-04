package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class GameObjectRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    private lateinit var powerUpBitmaps: Map<String, Bitmap?>
    private var asteroidBitmap: Bitmap? = null
    private var giantAsteroidBitmap: Bitmap? = null
    private var enemyShipBitmap: Bitmap? = null
    private val defaultProjectilePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    private val enemyProjectilePaint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
    }
    private val homingProjectilePaint = Paint().apply {
        color = Color.YELLOW
        isAntiAlias = true
    }
    private val plasmaPaint = Paint().apply {
        color = Color.MAGENTA
        isAntiAlias = true
    }
    private val missilePaint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
    }
    private val laserPaint = Paint().apply {
        color = Color.BLUE
        isAntiAlias = true
    }

    private val bitmapManager: BitmapManager = BitmapManager(context) // Local instance for boss bitmaps

    companion object {
        const val ENEMY_SHIP_SCALE = 0.5f // Scaling factor for enemy ships
        const val BOSS_SHIP_SCALE = 0.7f // Scaling factor for boss ships
    }

    init {
        powerUpBitmaps = mapOf(
            "power_up" to BitmapFactory.decodeResource(context.resources, R.drawable.power_up_icon),
            "shield" to BitmapFactory.decodeResource(context.resources, R.drawable.shield_icon),
            "speed" to BitmapFactory.decodeResource(context.resources, R.drawable.speed_icon),
            "stealth" to BitmapFactory.decodeResource(context.resources, R.drawable.stealth_icon),
            "warp" to BitmapFactory.decodeResource(context.resources, R.drawable.warp_icon),
            "star" to BitmapFactory.decodeResource(context.resources, R.drawable.star_icon),
            "invincibility" to BitmapFactory.decodeResource(context.resources, R.drawable.invincibility_icon)
        )
        asteroidBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.asteroid)
        enemyShipBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.enemy_ship)
        // Note: bossShipBitmap is no longer initialized here; we'll fetch it dynamically
    }

    fun drawPowerUps(canvas: Canvas, powerUps: List<PowerUp>, statusBarHeight: Float) {
        powerUps.forEach { powerUp ->
            val bitmap = powerUpBitmaps[powerUp.type]
            if (bitmap != null && !bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, powerUp.x - bitmap.width / 2f, powerUp.y - bitmap.height / 2f + statusBarHeight, null)
            } else {
                Timber.w("Bitmap for power-up ${powerUp.type} is null or recycled")
            }
        }
    }

    fun drawAsteroids(canvas: Canvas, asteroids: List<Asteroid>, statusBarHeight: Float) {
        asteroids.forEach { asteroid ->
            val bitmap = if (asteroid is GiantAsteroid) giantAsteroidBitmap else asteroidBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                val scaledWidth = bitmap.width * asteroid.size / bitmap.width
                val scaledHeight = bitmap.height * asteroid.size / bitmap.height
                canvas.save()
                canvas.translate(asteroid.x - scaledWidth / 2f, asteroid.y - scaledHeight / 2f + statusBarHeight)
                canvas.rotate(asteroid.rotation, scaledWidth / 2f, scaledHeight / 2f)
                canvas.scale(asteroid.size / bitmap.width, asteroid.size / bitmap.height)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()
            } else {
                Timber.w("Asteroid bitmap is null or recycled")
            }
        }
    }

    fun drawProjectiles(canvas: Canvas, projectiles: List<Projectile>, statusBarHeight: Float) {
        projectiles.forEach { projectile ->
            val paint = when (projectile) {
                is PlasmaProjectile -> plasmaPaint
                is MissileProjectile -> missilePaint
                is LaserProjectile -> laserPaint
                else -> defaultProjectilePaint
            }
            canvas.drawCircle(projectile.x, projectile.y + statusBarHeight, FlightModeManager.PROJECTILE_SIZE, paint)
        }
    }

    fun drawEnemyShips(canvas: Canvas, enemyShips: List<EnemyShip>, statusBarHeight: Float) {
        enemyShips.forEach { enemyShip ->
            if (enemyShipBitmap != null && !enemyShipBitmap!!.isRecycled) {
                val scaledWidth = enemyShipBitmap!!.width * ENEMY_SHIP_SCALE
                val scaledHeight = enemyShipBitmap!!.height * ENEMY_SHIP_SCALE
                canvas.save()
                canvas.translate(enemyShip.x - scaledWidth / 2f, enemyShip.y - scaledHeight / 2f + statusBarHeight)
                canvas.scale(ENEMY_SHIP_SCALE, ENEMY_SHIP_SCALE)
                canvas.drawBitmap(enemyShipBitmap!!, 0f, 0f, null)
                canvas.restore()
            } else {
                Timber.w("Enemy ship bitmap is null or recycled")
            }
        }
    }

    fun drawBoss(canvas: Canvas, boss: BossShip?, statusBarHeight: Float) {
        if (boss != null) {
            val bossShipBitmap = bitmapManager.getBossShipBitmap(boss.tier)
            if (bossShipBitmap != null && !bossShipBitmap.isRecycled) {
                val scaledWidth = bossShipBitmap.width * BOSS_SHIP_SCALE
                val scaledHeight = bossShipBitmap.height * BOSS_SHIP_SCALE
                canvas.save()
                canvas.translate(boss.x - scaledWidth / 2f, boss.y - scaledHeight / 2f + statusBarHeight)
                canvas.scale(BOSS_SHIP_SCALE, BOSS_SHIP_SCALE)
                canvas.drawBitmap(bossShipBitmap, 0f, 0f, null)
                canvas.restore()
            } else {
                Timber.w("Boss ship bitmap for tier ${boss.tier} is null or recycled")
            }
        }
    }

    fun drawEnemyProjectiles(canvas: Canvas, enemyProjectiles: List<Projectile>, statusBarHeight: Float) {
        enemyProjectiles.forEach { projectile ->
            canvas.drawCircle(projectile.x, projectile.y + statusBarHeight, FlightModeManager.PROJECTILE_SIZE, enemyProjectilePaint)
        }
    }

    fun drawHomingProjectiles(canvas: Canvas, homingProjectiles: List<HomingProjectile>, statusBarHeight: Float) {
        homingProjectiles.forEach { projectile ->
            canvas.drawCircle(projectile.x, projectile.y + statusBarHeight, FlightModeManager.PROJECTILE_SIZE, homingProjectilePaint)
        }
    }

    fun onDestroy() {
        powerUpBitmaps.values.forEach { it?.recycle() }
        asteroidBitmap?.recycle()
        giantAsteroidBitmap?.recycle()
        enemyShipBitmap?.recycle()
        bitmapManager.onDestroy() // Clean up BitmapManager bitmaps
        Timber.d("GameObjectRenderer onDestroy called, bitmaps recycled")
    }
}