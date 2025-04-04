package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class GameObjectRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    private lateinit var powerUpBitmaps: Map<String, Bitmap?>
    private var enemyShipBitmap: Bitmap? = null
    private var droneEnemyBitmap: Bitmap? = null // New bitmap for drones
    private var armoredEnemyBitmap: Bitmap? = null // New bitmap for armored enemies
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
    private val hpBarBackgroundPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val hpBarFillPaint = Paint().apply {
        color = Color.GREEN // Initial color, will be adjusted dynamically
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val hpBarBorderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val bitmapManager: BitmapManager = BitmapManager(context) // Local instance for bitmaps

    companion object {
        const val ENEMY_SHIP_SCALE = 0.5f // Scaling factor for enemy ships
        const val DRONE_ENEMY_SCALE = 0.3f // Smaller scale for drones
        const val ARMORED_ENEMY_SCALE = 0.7f // Larger scale for armored enemies
        const val BOSS_TARGET_WIDTH = 150f // Target width in pixels for all boss ships
        const val HP_BAR_WIDTH = 100f // Fixed width of the HP bar
        const val HP_BAR_HEIGHT = 10f // Fixed height of the HP bar
        const val HP_BAR_OFFSET = 20f // Distance above the boss ship
        const val ASTEROID_MIN_SCALE = 0.5f // Minimum scale when asteroid is at top
        const val ASTEROID_MAX_SCALE = 1.5f // Maximum scale when asteroid is at bottom
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
        enemyShipBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.enemy_ship)
        // Initialize new bitmaps for drone and armored enemies
        // Note: You'll need to add these drawables to your res/drawable folder
        droneEnemyBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.drone_enemy) ?: enemyShipBitmap
        armoredEnemyBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.armored_enemy) ?: enemyShipBitmap
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
            val bitmap = bitmapManager.getRandomAsteroidBitmap()
            if (bitmap != null && !bitmap.isRecycled) {
                // Calculate dynamic scale based on y-position (top = smaller, bottom = larger)
                val yPositionFactor = (asteroid.y + statusBarHeight + bitmap.height) / (canvas.height.toFloat() + bitmap.height) // Normalize y from 0 (top) to 1 (bottom)
                val dynamicScale = ASTEROID_MIN_SCALE + (ASTEROID_MAX_SCALE - ASTEROID_MIN_SCALE) * yPositionFactor.coerceIn(0f, 1f)
                val scaledWidth = bitmap.width * asteroid.size / bitmap.width * dynamicScale
                val scaledHeight = bitmap.height * asteroid.size / bitmap.height * dynamicScale

                canvas.save()
                canvas.translate(asteroid.x - scaledWidth / 2f, asteroid.y - scaledHeight / 2f + statusBarHeight)
                canvas.rotate(asteroid.rotation, scaledWidth / 2f, scaledHeight / 2f)
                canvas.scale(asteroid.size / bitmap.width * dynamicScale, asteroid.size / bitmap.height * dynamicScale)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()

                Timber.d("Drawing asteroid at (${asteroid.x}, ${asteroid.y}) with dynamicScale=$dynamicScale, scaledWidth=$scaledWidth, scaledHeight=$scaledHeight")
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
            val (bitmap, scale) = when (enemyShip) {
                is DroneEnemy -> Pair(droneEnemyBitmap, DRONE_ENEMY_SCALE)
                is ArmoredEnemy -> Pair(armoredEnemyBitmap, ARMORED_ENEMY_SCALE)
                else -> Pair(enemyShipBitmap, ENEMY_SHIP_SCALE)
            }
            if (bitmap != null && !bitmap.isRecycled) {
                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                canvas.save()
                canvas.translate(enemyShip.x - scaledWidth / 2f, enemyShip.y - scaledHeight / 2f + statusBarHeight)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()

                // Draw HP bar for enemies with health > 100 (e.g., ArmoredEnemy)
                if (enemyShip.health > 100f) {
                    val hpBarLeft = enemyShip.x - HP_BAR_WIDTH / 2f
                    val hpBarTop = enemyShip.y - scaledHeight / 2f - HP_BAR_OFFSET - HP_BAR_HEIGHT + statusBarHeight
                    val hpBarRight = hpBarLeft + HP_BAR_WIDTH
                    val hpBarBottom = hpBarTop + HP_BAR_HEIGHT

                    canvas.drawRect(hpBarLeft, hpBarTop, hpBarRight, hpBarBottom, hpBarBackgroundPaint)

                    val hpFraction = enemyShip.health / 200f // Assuming 200 is max for ArmoredEnemy
                    hpBarFillPaint.color = when {
                        hpFraction > 0.5f -> Color.GREEN
                        hpFraction > 0.25f -> Color.YELLOW
                        else -> Color.RED
                    }
                    val hpBarFillRight = hpBarLeft + HP_BAR_WIDTH * hpFraction.coerceIn(0f, 1f)
                    canvas.drawRect(hpBarLeft, hpBarTop, hpBarFillRight, hpBarBottom, hpBarFillPaint)

                    val hpBarRect = RectF(hpBarLeft, hpBarTop, hpBarRight, hpBarBottom)
                    canvas.drawRect(hpBarRect, hpBarBorderPaint)
                }
            } else {
                Timber.w("Enemy ship bitmap is null or recycled for ${enemyShip.javaClass.simpleName}")
            }
        }
    }

    fun drawBoss(canvas: Canvas, boss: BossShip?, statusBarHeight: Float) {
        if (boss != null) {
            val bossShipBitmap = bitmapManager.getBossShipBitmap(boss.tier)
            if (bossShipBitmap != null && !bossShipBitmap.isRecycled) {
                val originalWidth = bossShipBitmap.width.toFloat()
                val scaleFactor = BOSS_TARGET_WIDTH / originalWidth
                val scaledWidth = bossShipBitmap.width * scaleFactor
                val scaledHeight = bossShipBitmap.height * scaleFactor

                canvas.save()
                canvas.translate(boss.x - scaledWidth / 2f, boss.y - scaledHeight / 2f + statusBarHeight)
                canvas.scale(scaleFactor, scaleFactor)
                canvas.drawBitmap(bossShipBitmap, 0f, 0f, null)
                canvas.restore()

                val hpBarLeft = boss.x - HP_BAR_WIDTH / 2f
                val hpBarTop = boss.y - scaledHeight / 2f - HP_BAR_OFFSET - HP_BAR_HEIGHT + statusBarHeight
                val hpBarRight = hpBarLeft + HP_BAR_WIDTH
                val hpBarBottom = hpBarTop + HP_BAR_HEIGHT

                canvas.drawRect(hpBarLeft, hpBarTop, hpBarRight, hpBarBottom, hpBarBackgroundPaint)

                val hpFraction = boss.hp / boss.maxHp
                hpBarFillPaint.color = when {
                    hpFraction > 0.5f -> Color.GREEN
                    hpFraction > 0.25f -> Color.YELLOW
                    else -> Color.RED
                }
                val hpBarFillRight = hpBarLeft + HP_BAR_WIDTH * hpFraction.coerceIn(0f, 1f)
                canvas.drawRect(hpBarLeft, hpBarTop, hpBarFillRight, hpBarBottom, hpBarFillPaint)

                val hpBarRect = RectF(hpBarLeft, hpBarTop, hpBarRight, hpBarBottom)
                canvas.drawRect(hpBarRect, hpBarBorderPaint)

                Timber.d("Drawing boss ship for tier ${boss.tier} with scaleFactor=$scaleFactor, scaledWidth=$scaledWidth, scaledHeight=$scaledHeight, HP=${boss.hp}/${boss.maxHp}, hpFraction=$hpFraction")
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
        enemyShipBitmap?.recycle()
        droneEnemyBitmap?.recycle()
        armoredEnemyBitmap?.recycle()
        bitmapManager.onDestroy()
        Timber.d("GameObjectRenderer onDestroy called, bitmaps recycled")
    }
}