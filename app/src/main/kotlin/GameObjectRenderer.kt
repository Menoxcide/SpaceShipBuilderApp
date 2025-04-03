package com.example.spaceshipbuilderapp

import android.graphics.*
import timber.log.Timber
import javax.inject.Inject

class GameObjectRenderer @Inject constructor(
    private val bitmapManager: BitmapManager
) {
    private val powerUpPaint = Paint().apply { isAntiAlias = true }
    private val asteroidPaint = Paint().apply { isAntiAlias = true; color = Color.RED }
    private val projectilePaint = Paint().apply { isAntiAlias = true; color = Color.WHITE }
    private val enemyProjectilePaint = Paint().apply { isAntiAlias = true }
    private val bossPaint = Paint().apply { isAntiAlias = true; color = Color.RED }
    private val bossHpBarPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    private val bossHpBarBorderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    private val homingProjectilePaint = Paint().apply { isAntiAlias = true }

    fun drawPowerUps(canvas: Canvas, powerUps: List<PowerUp>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${powerUps.size} power-ups")
        powerUps.forEach { powerUp ->
            val y = powerUp.y
            val bitmap = when (powerUp.type) {
                "shield" -> bitmapManager.getBitmap(R.drawable.shield_icon, "Shield")
                "speed" -> bitmapManager.getBitmap(R.drawable.speed_icon, "Speed")
                "power_up" -> bitmapManager.getBitmap(R.drawable.power_up, "Power-up")
                "stealth" -> bitmapManager.getBitmap(R.drawable.stealth_icon, "Stealth")
                "warp" -> bitmapManager.getBitmap(R.drawable.warp_icon, "Warp")
                "star" -> bitmapManager.getBitmap(R.drawable.star, "Star")
                "invincibility" -> bitmapManager.getBitmap(R.drawable.invincibility_icon, "Invincibility")
                else -> bitmapManager.getBitmap(R.drawable.power_up, "Default Power-up")
            }
            val x = powerUp.x - bitmap.width / 2f
            canvas.drawBitmap(bitmap, x, y - bitmap.height / 2f, powerUpPaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing power-up at (x=${powerUp.x}, y=$y)")
        }
    }

    fun drawAsteroids(canvas: Canvas, asteroids: List<Asteroid>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${asteroids.size} asteroids")
        asteroids.forEach { asteroid ->
            val y = asteroid.y
            val x = asteroid.x - asteroid.size
            val bitmap = bitmapManager.getBitmap(R.drawable.asteroid, "Asteroid")
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                (asteroid.size * 2).toInt(),
                (asteroid.size * 2).toInt(),
                true
            )
            canvas.save()
            canvas.translate(x + asteroid.size, y)
            canvas.rotate(asteroid.rotation * (180f / Math.PI.toFloat()))
            canvas.drawBitmap(scaledBitmap, -asteroid.size, -asteroid.size, asteroidPaint)
            canvas.restore()
            if (BuildConfig.DEBUG) Timber.d("Drawing asteroid at (x=${asteroid.x}, y=$y) with size=${asteroid.size}")
        }
    }

    fun drawProjectiles(canvas: Canvas, projectiles: List<Projectile>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${projectiles.size} projectiles")
        projectiles.forEach { projectile ->
            canvas.drawCircle(projectile.x, projectile.y, FlightModeManager.PROJECTILE_SIZE, projectilePaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing projectile at (x=${projectile.x}, y=${projectile.y})")
        }
    }

    fun drawEnemyShips(canvas: Canvas, enemyShips: List<EnemyShip>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${enemyShips.size} enemy ships")
        enemyShips.forEach { enemy ->
            val bitmap = bitmapManager.getBitmap(R.drawable.enemy_ship, "Enemy ship")
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true)
            canvas.drawBitmap(scaledBitmap, enemy.x - 50f, enemy.y - 50f, asteroidPaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing enemy ship at (x=${enemy.x}, y=${enemy.y})")
        }
    }

    fun drawBoss(canvas: Canvas, boss: BossShip?, statusBarHeight: Float) {
        if (boss == null) return
        if (BuildConfig.DEBUG) Timber.d("Drawing boss at (x=${boss.x}, y=${boss.y}) with tier=${boss.tier}")
        val bitmapIndex = (boss.tier - 1) % 3
        val resourceId = when (bitmapIndex) {
            0 -> R.drawable.boss_ship_1
            1 -> R.drawable.boss_ship_2
            2 -> R.drawable.boss_ship_3
            else -> R.drawable.boss_ship_1
        }
        val bitmap = bitmapManager.getBitmap(resourceId, "Boss ship $bitmapIndex")
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, true)
        canvas.drawBitmap(scaledBitmap, boss.x - 75f, boss.y - 75f, bossPaint)

        val barWidth = 150f
        val barHeight = 10f
        val barX = boss.x - barWidth / 2f
        val barY = boss.y - 75f - barHeight - 5f
        val hpFraction = (boss.hp / boss.maxHp).coerceIn(0f, 1f)
        val filledWidth = barWidth * hpFraction

        val red = (255 * (1 - hpFraction)).toInt()
        val green = (255 * hpFraction).toInt()
        bossHpBarPaint.color = Color.rgb(red, green, 0)

        canvas.drawRect(barX, barY, barX + filledWidth, barY + barHeight, bossHpBarPaint)
        canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, bossHpBarBorderPaint)
    }

    fun drawEnemyProjectiles(canvas: Canvas, enemyProjectiles: List<Projectile>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${enemyProjectiles.size} enemy projectiles")
        enemyProjectiles.forEach { projectile ->
            val bitmap = bitmapManager.getBitmap(R.drawable.boss_projectile, "Boss projectile")
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 20, 20, true)
            canvas.drawBitmap(scaledBitmap, projectile.x - scaledBitmap.width / 2f, projectile.y - scaledBitmap.height / 2f, enemyProjectilePaint)
            if (BuildConfig.DEBUG) Timber.d("Drawing enemy projectile at (x=${projectile.x}, y=${projectile.y})")
        }
    }

    fun drawHomingProjectiles(canvas: Canvas, homingProjectiles: List<HomingProjectile>, statusBarHeight: Float) {
        if (BuildConfig.DEBUG) Timber.d("Drawing ${homingProjectiles.size} homing projectiles")
        homingProjectiles.forEach { projectile ->
            val bitmap = bitmapManager.getBitmap(R.drawable.homing_missile, "Homing missile")
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 20, 20, true)
            canvas.drawBitmap(
                scaledBitmap,
                projectile.x - scaledBitmap.width / 2f,
                projectile.y - scaledBitmap.height / 2f,
                homingProjectilePaint
            )
            if (BuildConfig.DEBUG) Timber.d("Drawing homing projectile at (x=${projectile.x}, y=${projectile.y})")
        }
    }
}