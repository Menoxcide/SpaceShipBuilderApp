package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.sin

class GameObjectRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val particleSystem: ParticleSystem
) {
    private lateinit var powerUpBitmaps: Map<String, Bitmap?>
    private var enemyShipBitmap: Bitmap? = null
    private var droneEnemyBitmap: Bitmap? = null
    private var armoredEnemyBitmap: Bitmap? = null
    private val projectileBitmaps: MutableMap<WeaponType, Bitmap?> = mutableMapOf()
    private val enemyProjectilePaint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
    }
    private val homingProjectilePaint = Paint().apply {
        color = Color.YELLOW
        isAntiAlias = true
    }
    private val hpBarBackgroundPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val hpBarFillPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val hpBarBorderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private var glowPhase: Float = 0f

    private val bitmapManager: BitmapManager = BitmapManager(context)

    companion object {
        const val ENEMY_SHIP_SCALE = 0.5f
        const val DRONE_ENEMY_SCALE = 0.3f
        const val ARMORED_ENEMY_SCALE = 0.7f
        const val BOSS_TARGET_WIDTH = 150f
        const val HP_BAR_WIDTH = 100f
        const val HP_BAR_HEIGHT = 10f
        const val HP_BAR_OFFSET = 20f
        const val ASTEROID_MIN_SCALE = 0.5f
        const val ASTEROID_MAX_SCALE = 1.5f
        const val PROJECTILE_SIZE = 10f
        const val GLOW_RADIUS = 50f
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
        droneEnemyBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.drone_enemy) ?: enemyShipBitmap
        armoredEnemyBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.armored_enemy) ?: enemyShipBitmap

        projectileBitmaps[WeaponType.Default] = BitmapFactory.decodeResource(context.resources, WeaponType.Default.projectileDrawableId)
        projectileBitmaps[WeaponType.Plasma] = BitmapFactory.decodeResource(context.resources, WeaponType.Plasma.projectileDrawableId)
        projectileBitmaps[WeaponType.Missile] = BitmapFactory.decodeResource(context.resources, WeaponType.Missile.projectileDrawableId)
        projectileBitmaps[WeaponType.HomingMissile] = BitmapFactory.decodeResource(context.resources, WeaponType.HomingMissile.projectileDrawableId)
        projectileBitmaps[WeaponType.Laser] = BitmapFactory.decodeResource(context.resources, WeaponType.Laser.projectileDrawableId)
    }

    fun updateGlowAnimation() {
        glowPhase = (glowPhase + 0.05f) % (2 * Math.PI.toFloat())
    }

    fun drawPowerUps(canvas: Canvas, powerUps: List<PowerUp>, statusBarHeight: Float) {
        powerUps.forEach { powerUp ->
            val bitmap = powerUpBitmaps[powerUp.type]
            if (bitmap != null && !bitmap.isRecycled) {
                val glowColor = when (powerUp.type) {
                    "power_up" -> Color.YELLOW
                    "shield" -> Color.BLUE
                    "speed" -> Color.RED
                    "stealth" -> Color.GRAY
                    "warp" -> Color.MAGENTA
                    "star" -> Color.WHITE
                    "invincibility" -> Color.GREEN
                    else -> Color.YELLOW
                }
                val glowAlpha = (sin(glowPhase) * 127 + 128).toInt()
                glowPaint.shader = RadialGradient(
                    powerUp.x, powerUp.y + statusBarHeight,
                    GLOW_RADIUS,
                    Color.argb(glowAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(powerUp.x, powerUp.y + statusBarHeight, GLOW_RADIUS, glowPaint)
                canvas.drawBitmap(bitmap, powerUp.x - bitmap.width / 2f, powerUp.y - bitmap.height / 2f + statusBarHeight, null)
            } else {
                Timber.w("Bitmap for power-up ${powerUp.type} is null or recycled")
            }
        }
    }

    fun drawAsteroids(canvas: Canvas, asteroids: List<Asteroid>, statusBarHeight: Float) {
        asteroids.forEach { asteroid ->
            val bitmap = bitmapManager.getAsteroidBitmap(asteroid.spriteId)
            if (bitmap != null && !bitmap.isRecycled) {
                val yPositionFactor = (asteroid.y + statusBarHeight + bitmap.height) / (canvas.height.toFloat() + bitmap.height)
                val dynamicScale = ASTEROID_MIN_SCALE + (ASTEROID_MAX_SCALE - ASTEROID_MIN_SCALE) * yPositionFactor.coerceIn(0f, 1f)
                val scaledWidth = bitmap.width * asteroid.size / bitmap.width * dynamicScale
                val scaledHeight = bitmap.height * asteroid.size / bitmap.height * dynamicScale

                canvas.save()
                canvas.translate(asteroid.x - scaledWidth / 2f, asteroid.y - scaledHeight / 2f + statusBarHeight)
                canvas.rotate(asteroid.rotation, scaledWidth / 2f, scaledHeight / 2f)
                canvas.scale(asteroid.size / bitmap.width * dynamicScale, asteroid.size / bitmap.height * dynamicScale)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()

                Timber.d("Drawing asteroid at (${asteroid.x}, ${asteroid.y}) with spriteId=${asteroid.spriteId}, dynamicScale=$dynamicScale, scaledWidth=$scaledWidth, scaledHeight=$scaledHeight")
            } else {
                Timber.w("Asteroid bitmap for spriteId ${asteroid.spriteId} is null or recycled")
            }
        }
    }

    fun drawProjectiles(canvas: Canvas, projectiles: List<Projectile>, statusBarHeight: Float) {
        projectiles.forEach { projectile ->
            val bitmap = projectileBitmaps[projectile.weaponType]
            if (bitmap != null && !bitmap.isRecycled) {
                val scale = PROJECTILE_SIZE / bitmap.width.toFloat()
                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                val angle = atan2(projectile.speedY, projectile.speedX) * 180 / Math.PI.toFloat() + 90
                canvas.save()
                canvas.translate(projectile.x - scaledWidth / 2f, projectile.y - scaledHeight / 2f + statusBarHeight)
                canvas.rotate(angle, scaledWidth / 2f, scaledHeight / 2f)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()
            } else {
                Timber.w("Projectile bitmap for weaponType ${projectile.weaponType} is null or recycled")
                val paint = Paint().apply {
                    color = Color.WHITE
                    isAntiAlias = true
                }
                canvas.drawCircle(projectile.x, projectile.y + statusBarHeight, FlightModeManager.PROJECTILE_SIZE, paint)
            }
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

                if (enemyShip.health > 100f) {
                    val hpBarLeft = enemyShip.x - HP_BAR_WIDTH / 2f
                    val hpBarTop = enemyShip.y - scaledHeight / 2f - HP_BAR_OFFSET - HP_BAR_HEIGHT + statusBarHeight
                    val hpBarRight = hpBarLeft + HP_BAR_WIDTH
                    val hpBarBottom = hpBarTop + HP_BAR_HEIGHT

                    canvas.drawRect(hpBarLeft, hpBarTop, hpBarRight, hpBarBottom, hpBarBackgroundPaint)
                    val hpFraction = enemyShip.health / 200f
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
            val bitmap = projectileBitmaps[projectile.weaponType]
            if (bitmap != null && !bitmap.isRecycled) {
                val scale = PROJECTILE_SIZE / bitmap.width.toFloat()
                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                val angle = atan2(projectile.speedY, projectile.speedX) * 180 / Math.PI.toFloat() + 90
                canvas.save()
                canvas.translate(projectile.x - scaledWidth / 2f, projectile.y - scaledHeight / 2f + statusBarHeight)
                canvas.rotate(angle, scaledWidth / 2f, scaledHeight / 2f)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()
            } else {
                Timber.w("Homing projectile bitmap for weaponType ${projectile.weaponType} is null or recycled")
                canvas.drawCircle(projectile.x, projectile.y + statusBarHeight, FlightModeManager.PROJECTILE_SIZE, homingProjectilePaint)
            }
        }
    }

    fun onDestroy() {
        powerUpBitmaps.values.forEach { it?.recycle() }
        enemyShipBitmap?.recycle()
        droneEnemyBitmap?.recycle()
        armoredEnemyBitmap?.recycle()
        projectileBitmaps.values.forEach { it?.recycle() }
        bitmapManager.onDestroy()
        Timber.d("GameObjectRenderer onDestroy called, bitmaps recycled")
    }
}