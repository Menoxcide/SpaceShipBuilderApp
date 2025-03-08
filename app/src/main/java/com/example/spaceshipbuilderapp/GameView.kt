package com.example.spaceshipbuilderapp

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint()
    private val random = Random
    private var gameState = GameState.BUILD
    private val parts = mutableListOf<Part>()
    private val shipParts = mutableListOf<Part>()
    private var selectedPart: Part? = null
    private var shipX = 0f
    private var shipY = 0f
    private var shipVelocityX = 0f
    private var fuel = 0f
    private var fuelCapacity = 100f
    private var score = 0
    private var highScore = 0
    private var level = 1
    private var distanceTraveled = 0f
    private val enemies = CopyOnWriteArrayList<Enemy>()
    private val asteroids = CopyOnWriteArrayList<MagneticAsteroid>()
    private val storms = CopyOnWriteArrayList<CosmicStorm>()
    private val crystals = CopyOnWriteArrayList<FuelCrystal>()
    private val stars = CopyOnWriteArrayList<Star>()
    private val powerUps = CopyOnWriteArrayList<PowerUp>()
    private val burstParticles = CopyOnWriteArrayList<Particle>()
    private var lastEnemySpawn = 0L
    private var lastAsteroidSpawn = 0L
    private var lastStormSpawn = 0L
    private var lastCrystalSpawn = 0L
    private var lastPowerUpSpawn = 0L
    private var engineSound: MediaPlayer? = null
    private var launchSound: MediaPlayer? = null
    private var placeSound: MediaPlayer? = null
    private var warpSound: MediaPlayer? = null
    private var backgroundMusic: MediaPlayer? = null
    private var longPressX = 0f
    private var longPressY = 0f
    private var longPressStartTime: Long = 0
    private val LONG_PRESS_DELAY = 500L
    private val panelHeightDp = 150f
    private var panelHeightPx = 0f
    private var screenHeight = 0f
    private var screenWidth = 0f
    private val gridSize = 64f
    private var backgroundOffset = 0f
    private var launchTime = 0L
    private var cameraShake = 0f
    private var statusBarHeight = 0f
    private var nebulaOffset = 0f
    private var hologramAlpha = 100f
    private var rotationAnimator: ValueAnimator? = null
    private var showBlueprint = false
    private var showLeaderboard = false
    private var lowAnimationMode = false
    private var needsRedraw = true

    private var shieldActive = false
    private var shieldTimeLeft = 0f
    private var speedBoostActive = false
    private var speedBoostTimeLeft = 0f
    private var stealthActive = false
    private var stealthTimeLeft = 0f
    private var warpDriveActive = false
    private var warpDriveTimeLeft = 0f
    private var enginePower = 2f
    private var coins = 0
    private var skinIndex = 0
    private val highScores = mutableListOf<Int>()
    private val leaderboardScores = mutableListOf<Pair<String, Int>>()
    private val prefs: SharedPreferences = context.getSharedPreferences("SpaceshipBuilder", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var launchListener: ((Boolean) -> Unit)? = null
    private val particleSystem = ParticleSystem(context)

    val cockpitBitmap = BitmapFactory.decodeResource(resources, R.drawable.cockpit)
    val engineBitmap = BitmapFactory.decodeResource(resources, R.drawable.engine)
    val fuelTankBitmap = BitmapFactory.decodeResource(resources, R.drawable.fuel_tank)
    private val starBitmap = BitmapFactory.decodeResource(resources, R.drawable.star)
    private val asteroidBitmap = BitmapFactory.decodeResource(resources, R.drawable.asteroid)
    private val exhaustBitmap = BitmapFactory.decodeResource(resources, R.drawable.exhaust)
    private val fuelCrystalBitmap = BitmapFactory.decodeResource(resources, R.drawable.fuel_crystal)
    private val shieldBitmap = BitmapFactory.decodeResource(resources, R.drawable.shield_icon)
    private val speedBitmap = BitmapFactory.decodeResource(resources, R.drawable.speed_icon)
    private val stealthBitmap = BitmapFactory.decodeResource(resources, R.drawable.stealth_icon)
    private val warpBitmap = BitmapFactory.decodeResource(resources, R.drawable.warp_icon)
    private val redCockpitBitmap = BitmapFactory.decodeResource(resources, R.drawable.cockpit_red)
    private val blueCockpitBitmap = BitmapFactory.decodeResource(resources, R.drawable.cockpit_blue)
    private val redFuelTankBitmap = BitmapFactory.decodeResource(resources, R.drawable.fuel_tank_red)
    private val blueFuelTankBitmap = BitmapFactory.decodeResource(resources, R.drawable.fuel_tank_blue)
    private val redEngineBitmap = BitmapFactory.decodeResource(resources, R.drawable.engine_red)
    private val blueEngineBitmap = BitmapFactory.decodeResource(resources, R.drawable.engine_blue)

    private val cockpitWidth = 68f
    private val cockpitHeight = 73f
    private val fuelTankWidth = 68f
    private val fuelTankHeight = 64f
    private val engineWidth = 68f
    private val engineHeight = 41f
    private val asteroidWidth = 29f
    private val asteroidHeight = 26f
    private val starWidth = 31f
    private val starHeight = 30f
    private val exhaustWidth = 13f
    private val exhaustHeight = 32f
    private val SNAP_THRESHOLD = 30f
    private val MAGNETIZATION_THRESHOLD = 100f
    private val EXTENDED_SNAP_TOLERANCE = 30f

    private val graffitiPaint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
        setShadowLayer(10f, 5f, 5f, Color.BLACK)
        typeface = resources.getFont(R.font.orbitron)
    }
    private val backgroundPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 2400f, Color.parseColor("#1A0B2E"), Color.parseColor("#4B0082"), Shader.TileMode.CLAMP)
    }
    private val glowPaint = Paint().apply {
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        color = Color.parseColor("#00FFFF")
    }
    private val hologramPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 0)
    }
    private val nebulaPaint = Paint()
    private val stormPaint = Paint().apply {
        color = Color.parseColor("#800080")
        alpha = 100
    }
    private val crystalPaint = Paint().apply {
        color = Color.parseColor("#00FFFF")
        maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
    }
    private val shieldPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val alignmentPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }.apply {
        isEnabled = true
    }
    private val blueprintPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val overlayPaint = Paint().apply {
        color = Color.argb(150, 0, 0, 0)
    }

    init {
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.GREEN
        try {
            engineSound = MediaPlayer.create(context, R.raw.engine_hum)
            launchSound = MediaPlayer.create(context, R.raw.launch_sound)
            placeSound = MediaPlayer.create(context, R.raw.place_sound)
            warpSound = MediaPlayer.create(context, R.raw.warp_sound)
            backgroundMusic = MediaPlayer.create(context, R.raw.background_music)
            backgroundMusic?.isLooping = true
            backgroundMusic?.start()
        } catch (e: Exception) {
            Log.e("GameView", "Error loading sounds: ${e.message}")
        }
        val density = context.resources.displayMetrics.density
        panelHeightPx = panelHeightDp * density
        spawnInitialStars()
        loadPersistentData()
        setupHologramAnimation()
        initializeLeaderboard()
        startSpaceworthinessThread()
    }

    private fun setupHologramAnimation() {
        val animator = ValueAnimator.ofFloat(100f, 200f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                hologramAlpha = animation.animatedValue as Float
                needsRedraw = true
            }
        }
        animator.start()
    }

    private fun initializeLeaderboard() {
        leaderboardScores.add(Pair("Player1", 1500))
        leaderboardScores.add(Pair("Player2", 1200))
        leaderboardScores.add(Pair("Player3", 900))
        leaderboardScores.add(Pair("You", score))
    }

    private fun loadPersistentData() {
        coins = prefs.getInt("coins", 0)
        fuelCapacity = prefs.getFloat("fuelCapacity", 100f)
        enginePower = prefs.getFloat("enginePower", 2f)
        skinIndex = prefs.getInt("skinIndex", 0)
        lowAnimationMode = prefs.getBoolean("lowAnimationMode", false)
        (0..4).forEach { i ->
            highScores.add(prefs.getInt("score_$i", 0))
        }
    }

    private fun savePersistentData() {
        with(prefs.edit()) {
            putInt("coins", coins)
            putFloat("fuelCapacity", fuelCapacity)
            putFloat("enginePower", enginePower)
            putInt("skinIndex", skinIndex)
            putBoolean("lowAnimationMode", lowAnimationMode)
            highScores.sortedDescending().take(5).forEachIndexed { i, score ->
                putInt("score_$i", score)
            }
            apply()
        }
        leaderboardScores.removeIf { it.first == "You" }
        leaderboardScores.add(Pair("You", score))
        leaderboardScores.sortByDescending { it.second }
    }

    private fun spawnInitialStars() {
        repeat(50) {
            stars.add(Star(Random.nextFloat() * 1080f, Random.nextFloat() * 2400f, Random.nextFloat() * 0.3f + 0.1f))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        shipX = screenWidth / 2f
        shipY = screenHeight - panelHeightPx - statusBarHeight
        Log.d("GameView", "onSizeChanged: Width=$screenWidth, Height=$screenHeight, PanelHeightPx=$panelHeightPx")
        needsRedraw = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!needsRedraw) return
        needsRedraw = false

        canvas.translate(0f, statusBarHeight)

        drawBackground(canvas)
        drawNebulae(canvas)

        stars.forEach { star ->
            canvas.save()
            canvas.translate(star.x, star.y + backgroundOffset)
            canvas.scale(star.brightness, star.brightness, starWidth / 2, starHeight / 2)
            canvas.drawBitmap(starBitmap, 0f, 0f, paint)
            canvas.restore()
        }

        if (gameState == GameState.BUILD) {
            parts.forEach { part ->
                drawPartWithGlow(canvas, applySkin(part))
            }
            if (selectedPart != null && !parts.contains(selectedPart)) {
                drawPartWithGlow(canvas, applySkin(selectedPart!!), true)
                drawAlignmentGuides(canvas, selectedPart!!)
                val nearest = findNearestSnap(selectedPart!!, MAGNETIZATION_THRESHOLD)
                if (nearest != null) {
                    particleSystem.addMagnetizationParticle(selectedPart!!.x, selectedPart!!.y)
                }
                burstParticles.forEach { particle ->
                    canvas.drawBitmap(particle.bitmap, particle.x - (particle.bitmap.width * particle.scale / 2), particle.y - (particle.bitmap.height * particle.scale / 2), null)
                }
            }
            if (showBlueprint) {
                drawBlueprint(canvas)
            }
            if (showLeaderboard) {
                canvas.drawRect(0f, 0f, screenWidth, screenHeight, overlayPaint)
                drawLeaderboard(canvas)
            }
            drawStats(canvas)
            drawHighScores(canvas)
            drawShop(canvas)
            drawLaunchReadiness(canvas)
        } else {
            canvas.save()
            canvas.translate(cameraShake, 0f)

            val totalHeight = shipParts.sumOf { part ->
                when (part.type) {
                    "cockpit" -> cockpitHeight.toDouble()
                    "fuel_tank" -> fuelTankHeight.toDouble()
                    "engine" -> engineHeight.toDouble()
                    else -> 0.0
                }
            }.toFloat()
            var currentY = shipY - totalHeight

            shipParts.forEach { part ->
                val partHeight = when (part.type) {
                    "cockpit" -> cockpitHeight
                    "fuel_tank" -> fuelTankHeight
                    "engine" -> engineHeight
                    else -> 0f
                }
                canvas.drawRect(shipX - (part.bitmap.width / 2f), currentY, shipX + (part.bitmap.width / 2f), currentY + partHeight, glowPaint)
                val skinnedPart = applySkin(part)
                canvas.save()
                canvas.rotate(part.rotation, shipX, currentY + partHeight / 2)
                canvas.drawBitmap(skinnedPart.bitmap, shipX - (skinnedPart.bitmap.width / 2f), currentY, null)
                canvas.restore()
                if (part.type == "engine" && fuel > 0) {
                    particleSystem.drawPulsatingExhaust(canvas, shipX, currentY + partHeight)
                }
                currentY += partHeight
            }

            if (shieldActive) {
                canvas.drawRect(shipX - 40f, shipY - totalHeight - 10f, shipX + 40f, shipY + 10f, shieldPaint)
            }
            if (stealthActive) {
                canvas.drawColor(Color.argb(50, 0, 0, 255))
            }
            if (warpDriveActive) {
                particleSystem.drawWarpEffect(canvas, shipX, shipY)
            }
            canvas.restore()

            storms.forEach { storm ->
                storm.update()
                canvas.drawCircle(storm.x, storm.y, 50f, stormPaint)
                if (storm.isDead()) storms.remove(storm)
                if (hypot(shipX - storm.x, shipY - storm.y) < 50 && !stealthActive) {
                    fuel -= 0.5f
                    shipVelocityX *= 0.9f
                }
            }

            val asteroidSpawnInterval = 2000 - (level - 1) * 200L
            if (System.currentTimeMillis() - lastAsteroidSpawn > asteroidSpawnInterval) {
                asteroids.add(MagneticAsteroid(random.nextInt(screenWidth.toInt()).toFloat(), -50f, asteroidBitmap))
                lastAsteroidSpawn = System.currentTimeMillis()
                needsRedraw = true
            }
            asteroids.forEach { asteroid ->
                asteroid.update(level)
                canvas.save()
                canvas.translate(asteroid.x, asteroid.y)
                canvas.rotate(asteroid.rotation)
                canvas.drawBitmap(asteroid.bitmap, -(asteroid.bitmap.width / 2f), -(asteroid.bitmap.height / 2f), null)
                canvas.restore()
                if (asteroid.isDead()) asteroids.remove(asteroid)
                val distance = hypot(shipX - asteroid.x, shipY - asteroid.y)
                if (distance < 100 && !stealthActive) {
                    val pullForce = (100 - distance) / 100f * 0.5f
                    shipVelocityX += if (shipX < asteroid.x) pullForce else -pullForce
                }
            }

            if (System.currentTimeMillis() - lastCrystalSpawn > 3000) {
                crystals.add(FuelCrystal(random.nextInt(screenWidth.toInt()).toFloat(), -20f))
                lastCrystalSpawn = System.currentTimeMillis()
                needsRedraw = true
            }
            crystals.forEach { crystal ->
                crystal.update()
                canvas.drawBitmap(fuelCrystalBitmap, crystal.x - 10f, crystal.y - 10f, null)
                if (crystal.isDead()) crystals.remove(crystal)
                if (hypot(shipX - crystal.x, shipY - crystal.y) < 20) {
                    fuel = minOf(fuel + 20f, fuelCapacity)
                    score += 10
                    crystals.remove(crystal)
                    needsRedraw = true
                }
            }

            if (System.currentTimeMillis() - lastPowerUpSpawn > 4000) {
                val type = when (Random.nextInt(5)) {
                    0 -> PowerUp.TYPE_FUEL
                    1 -> PowerUp.TYPE_SHIELD
                    2 -> PowerUp.TYPE_SPEED
                    3 -> PowerUp.TYPE_STEALTH
                    4 -> PowerUp.TYPE_WARP
                    else -> PowerUp.TYPE_FUEL
                }
                val bitmap = when (type) {
                    PowerUp.TYPE_FUEL -> fuelCrystalBitmap
                    PowerUp.TYPE_SHIELD -> shieldBitmap
                    PowerUp.TYPE_SPEED -> speedBitmap
                    PowerUp.TYPE_STEALTH -> stealthBitmap
                    PowerUp.TYPE_WARP -> warpBitmap
                    else -> fuelCrystalBitmap
                }
                powerUps.add(PowerUp(random.nextInt(screenWidth.toInt()).toFloat(), -20f, type, bitmap))
                lastPowerUpSpawn = System.currentTimeMillis()
                needsRedraw = true
            }
            powerUps.forEach { powerUp ->
                powerUp.update()
                canvas.drawBitmap(powerUp.bitmap, powerUp.x - 10f, powerUp.y - 10f, null)
                if (powerUp.isDead()) powerUps.remove(powerUp)
                if (hypot(shipX - powerUp.x, shipY - powerUp.y) < 20) {
                    when (powerUp.type) {
                        PowerUp.TYPE_FUEL -> {
                            fuel = minOf(fuel + 10f, fuelCapacity)
                            score += 20
                        }
                        PowerUp.TYPE_SHIELD -> {
                            shieldActive = true
                            shieldTimeLeft = 5f
                        }
                        PowerUp.TYPE_SPEED -> {
                            speedBoostActive = true
                            speedBoostTimeLeft = 5f
                        }
                        PowerUp.TYPE_STEALTH -> {
                            stealthActive = true
                            stealthTimeLeft = 5f
                        }
                        PowerUp.TYPE_WARP -> {
                            warpDriveActive = true
                            warpDriveTimeLeft = 5f
                            warpSound?.seekTo(0)
                            warpSound?.start()
                        }
                    }
                    powerUps.remove(powerUp)
                    needsRedraw = true
                }
            }

            val enemySpawnInterval = 500 - (level - 1) * 50L
            if (System.currentTimeMillis() - lastEnemySpawn > enemySpawnInterval) {
                if (random.nextFloat() < 0.03 + (level - 1) * 0.01) {
                    enemies.add(Enemy(random.nextInt(screenWidth.toInt()).toFloat(), -50f, asteroidBitmap))
                }
                lastEnemySpawn = System.currentTimeMillis()
                needsRedraw = true
            }
            enemies.forEach { enemy ->
                enemy.update(level)
                canvas.drawBitmap(enemy.bitmap, enemy.x - (enemy.bitmap.width / 2f), enemy.y - (enemy.bitmap.height / 2f), null)
                if (enemy.isDead()) enemies.remove(enemy)
            }

            particleSystem.drawExhaustParticles(canvas)

            stars.forEach { it.update(lowAnimationMode) }

            if (shipY > 100 && fuel > 0) {
                val speed = when {
                    warpDriveActive -> enginePower * 4
                    speedBoostActive -> enginePower * 2
                    else -> enginePower
                }
                val engineCount = shipParts.count { it.type == "engine" }
                shipY -= speed * engineCount
                shipX += shipVelocityX
                shipVelocityX *= 0.95f
                shipX = shipX.coerceIn(0f, screenWidth)
                fuel -= 0.01f * engineCount
                distanceTraveled += speed * engineCount
                coins += (speed * engineCount / 10).toInt()
                backgroundOffset += if (lowAnimationMode) 2f else 5f
                nebulaOffset += if (lowAnimationMode) 1f else 2f
                if (backgroundOffset > screenHeight) backgroundOffset = 0f
                if (nebulaOffset > screenHeight) nebulaOffset = 0f

                val elapsed = (System.currentTimeMillis() - launchTime) / 1000f
                cameraShake = if (elapsed < 2f) Random.nextFloat() * 5f else 0f

                if (fuel > 0) {
                    val rocketBottom = shipY
                    particleSystem.addExhaustParticle(shipX, rocketBottom, Random.nextFloat() * 2f - 1f, 5f)
                }

                if (Random.nextFloat() < 0.001f * level) {
                    shipVelocityX += (screenWidth / 2f - shipX) * 0.1f
                }

                if (distanceTraveled >= level * 2500) {
                    endMission(true)
                }
                needsRedraw = true
            } else if (fuel <= 0) {
                endMission(false)
            }

            if (shieldActive) {
                shieldTimeLeft -= 0.016f
                if (shieldTimeLeft <= 0f) shieldActive = false
                needsRedraw = true
            }
            if (speedBoostActive) {
                speedBoostTimeLeft -= 0.016f
                if (speedBoostTimeLeft <= 0f) speedBoostActive = false
                needsRedraw = true
            }
            if (stealthActive) {
                stealthTimeLeft -= 0.016f
                if (stealthTimeLeft <= 0f) stealthActive = false
                needsRedraw = true
            }
            if (warpDriveActive) {
                warpDriveTimeLeft -= 0.016f
                if (warpDriveTimeLeft <= 0f) warpDriveActive = false
                needsRedraw = true
            }

            drawFlightStats(canvas)
            drawCometTrail(canvas)
            checkCollisions()
        }

        handler.postDelayed({ needsRedraw = true; invalidate() }, if (lowAnimationMode) 33 else 16) // 30 FPS in low mode
    }

    private fun applySkin(part: Part): Part {
        val bitmap = when (part.type) {
            "cockpit" -> when (skinIndex) {
                1 -> redCockpitBitmap
                2 -> blueCockpitBitmap
                else -> cockpitBitmap
            }
            "fuel_tank" -> when (skinIndex) {
                1 -> redFuelTankBitmap
                2 -> blueFuelTankBitmap
                else -> fuelTankBitmap
            }
            "engine" -> when (skinIndex) {
                1 -> redEngineBitmap
                2 -> blueEngineBitmap
                else -> engineBitmap
            }
            else -> part.bitmap
        }
        return Part(part.type, bitmap, part.x, part.y, part.rotation)
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawRect(0f, -statusBarHeight, screenWidth, screenHeight, backgroundPaint)
    }

    private fun drawNebulae(canvas: Canvas) {
        val nebula = RadialGradient(
            screenWidth * Random.nextFloat(),
            screenHeight * Random.nextFloat() + nebulaOffset,
            max(screenWidth, screenHeight) / 4f,
            intArrayOf(
                Color.argb(50, 255, 0, 255),
                Color.argb(30, 0, 255, 255),
                Color.argb(20, 0, 0, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        nebulaPaint.shader = nebula
        canvas.drawRect(0f, -statusBarHeight, screenWidth, screenHeight, nebulaPaint)
    }

    private fun drawPartWithGlow(canvas: Canvas, part: Part, isSelected: Boolean = false) {
        val x = part.x - (part.bitmap.width / 2f)
        val y = part.y - (part.bitmap.height / 2f)
        if (isSelected) {
            hologramPaint.alpha = hologramAlpha.toInt()
            canvas.drawBitmap(part.bitmap, x, y, hologramPaint)
        } else {
            canvas.drawRect(x, y, x + part.bitmap.width, y + part.bitmap.height, glowPaint)
            canvas.save()
            canvas.translate(part.x, part.y)
            canvas.rotate(part.rotation, part.bitmap.width / 2f, part.bitmap.height / 2f)
            canvas.drawBitmap(part.bitmap, -part.bitmap.width / 2f, -part.bitmap.height / 2f, null)
            canvas.restore()
        }
    }

    private fun drawAlignmentGuides(canvas: Canvas, part: Part) {
        val nearest = findNearestSnap(part)
        nearest?.let {
            val expectedY = when (it.type) {
                "cockpit" -> it.y + cockpitHeight
                "fuel_tank" -> it.y + fuelTankHeight
                else -> it.y
            }
            if (abs(part.x - it.x) < SNAP_THRESHOLD && abs(part.y - expectedY) < EXTENDED_SNAP_TOLERANCE) {
                canvas.drawLine(part.x, part.y, it.x, expectedY, alignmentPaint)
            }
        }
    }

    private fun drawCometTrail(canvas: Canvas) {
        val trailPaint = Paint().apply { color = Color.parseColor("#FFD700"); alpha = 150 }
        for (i in 0 until 10) {
            val progress = i / 10f
            val trailY = shipY + (progress * 100)
            canvas.drawLine(shipX, shipY, shipX, trailY, trailPaint)
        }
    }

    private fun drawBlueprint(canvas: Canvas) {
        val totalHeight = cockpitHeight + fuelTankHeight + engineHeight
        val blueprintX = screenWidth / 2f
        val blueprintY = screenHeight / 2f - totalHeight / 2f - panelHeightPx / 2f
        canvas.drawRect(
            blueprintX - cockpitWidth / 2f, blueprintY,
            blueprintX + cockpitWidth / 2f, blueprintY + cockpitHeight,
            blueprintPaint
        )
        canvas.drawRect(
            blueprintX - fuelTankWidth / 2f, blueprintY + cockpitHeight,
            blueprintX + fuelTankWidth / 2f, blueprintY + cockpitHeight + fuelTankHeight,
            blueprintPaint
        )
        canvas.drawRect(
            blueprintX - engineWidth / 2f, blueprintY + cockpitHeight + fuelTankHeight,
            blueprintX + engineWidth / 2f, blueprintY + cockpitHeight + fuelTankHeight + engineHeight,
            blueprintPaint
        )
    }

    private fun drawStats(canvas: Canvas) {
        val textHeight = 50f
        val totalHeight = textHeight * 6
        val startY = 80f + statusBarHeight
        canvas.drawRect(20f, startY - 40f, 300f, startY + totalHeight, Paint().apply { color = Color.argb(150, 0, 0, 0) })
        canvas.drawText("Level: $level", 40f, startY, graffitiPaint)
        canvas.drawText("High Score: $highScore", 40f, startY + textHeight, graffitiPaint)
        canvas.drawText("Speed: ${(shipParts.count { it.type == "engine" } * enginePower * 100).toInt()}", 40f, startY + textHeight * 2, graffitiPaint)
        canvas.drawText("Fuel: ${fuel.toInt()}/$fuelCapacity", 40f, startY + textHeight * 3, graffitiPaint)
        canvas.drawText("Points: $score", 40f, startY + textHeight * 4, graffitiPaint)
        canvas.drawText("Parts: ${parts.size}/3", 40f, startY + textHeight * 5, graffitiPaint)
        if (!isShipSpaceworthy() && parts.size == 3) {
            val reason = getSpaceworthinessFailureReason()
            canvas.drawText("Issue: $reason", 40f, startY + textHeight * 6, graffitiPaint.apply { color = Color.RED })
        }
    }

    private fun drawFlightStats(canvas: Canvas) {
        val textHeight = 50f
        val totalHeight = textHeight * 4
        val startY = 80f + statusBarHeight
        canvas.drawRect(10f, startY - 40f, 300f, startY + totalHeight, Paint().apply { color = Color.argb(150, 0, 0, 0) })
        canvas.drawText("Mission: Galactic Gauntlet - Level $level", 20f, startY, graffitiPaint)
        canvas.drawText("Fuel: ${fuel.toInt()}/$fuelCapacity", 20f, startY + textHeight, graffitiPaint)
        canvas.drawText("Score: $score", 20f, startY + textHeight * 2, graffitiPaint)
        canvas.drawText("Distance: ${distanceTraveled.toInt()}/${level * 2500}px", 20f, startY + textHeight * 3, graffitiPaint)
    }

    private fun drawHighScores(canvas: Canvas) {
        val textHeight = 40f
        val startY = 380f + statusBarHeight
        canvas.drawRect(20f, startY - 20f, 300f, startY + (textHeight * 5), Paint().apply { color = Color.argb(150, 0, 0, 0) })
        highScores.sortedDescending().take(5).forEachIndexed { index, score ->
            canvas.drawText("Top ${index + 1}: $score", 40f, startY + (index * textHeight), graffitiPaint)
        }
    }

    private fun drawShop(canvas: Canvas) {
        val buttonWidth = 200f
        val buttonHeight = 50f
        val startX = screenWidth - buttonWidth - 20f
        val startY = 80f + statusBarHeight
        val buttonPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 24f }

        canvas.drawRect(startX, startY, startX + buttonWidth, startY + buttonHeight, buttonPaint)
        canvas.drawText("Fuel +25 ($50)", startX + 10f, startY + 35f, textPaint)
        if (longPressX in startX..(startX + buttonWidth) && longPressY in startY..(startY + buttonHeight) && System.currentTimeMillis() - longPressStartTime >= LONG_PRESS_DELAY) {
            if (coins >= 50) {
                coins -= 50
                fuelCapacity += 25f
                savePersistentData()
                Log.d("GameView", "Upgraded fuel capacity to $fuelCapacity")
                needsRedraw = true
            }
        }

        canvas.drawRect(startX, startY + buttonHeight + 10f, startX + buttonWidth, startY + buttonHeight * 2 + 10f, buttonPaint)
        canvas.drawText("Engine +0.5 ($50)", startX + 10f, startY + buttonHeight + 45f, textPaint)
        if (longPressX in startX..(startX + buttonWidth) && longPressY in (startY + buttonHeight + 10f)..(startY + buttonHeight * 2 + 10f) && System.currentTimeMillis() - longPressStartTime >= LONG_PRESS_DELAY) {
            if (coins >= 50) {
                coins -= 50
                enginePower += 0.5f
                savePersistentData()
                Log.d("GameView", "Upgraded engine power to $enginePower")
                needsRedraw = true
            }
        }

        canvas.drawRect(startX, startY + buttonHeight * 2 + 20f, startX + buttonWidth, startY + buttonHeight * 3 + 20f, buttonPaint)
        canvas.drawText("Red Skin ($100)", startX + 10f, startY + buttonHeight * 2 + 55f, textPaint)
        if (longPressX in startX..(startX + buttonWidth) && longPressY in (startY + buttonHeight * 2 + 20f)..(startY + buttonHeight * 3 + 20f) && System.currentTimeMillis() - longPressStartTime >= LONG_PRESS_DELAY) {
            if (coins >= 100) {
                coins -= 100
                skinIndex = 1
                savePersistentData()
                Log.d("GameView", "Applied red skin")
                needsRedraw = true
            }
        }

        canvas.drawRect(startX, startY + buttonHeight * 3 + 30f, startX + buttonWidth, startY + buttonHeight * 4 + 30f, buttonPaint)
        canvas.drawText("Blue Skin ($100)", startX + 10f, startY + buttonHeight * 3 + 65f, textPaint)
        if (longPressX in startX..(startX + buttonWidth) && longPressY in (startY + buttonHeight * 3 + 30f)..(startY + buttonHeight * 4 + 30f) && System.currentTimeMillis() - longPressStartTime >= LONG_PRESS_DELAY) {
            if (coins >= 100) {
                coins -= 100
                skinIndex = 2
                savePersistentData()
                Log.d("GameView", "Applied blue skin")
                needsRedraw = true
            }
        }

        canvas.drawRect(startX, startY + buttonHeight * 4 + 40f, startX + buttonWidth, startY + buttonHeight * 5 + 40f, buttonPaint)
        canvas.drawText("Low Anim. Mode", startX + 10f, startY + buttonHeight * 4 + 75f, textPaint)
        if (longPressX in startX..(startX + buttonWidth) && longPressY in (startY + buttonHeight * 4 + 40f)..(startY + buttonHeight * 5 + 40f) && System.currentTimeMillis() - longPressStartTime >= LONG_PRESS_DELAY) {
            toggleLowAnimationMode()
            Log.d("GameView", "Toggled low animation mode to $lowAnimationMode")
            needsRedraw = true
        }

        val coinsText = "Coins: $coins"
        canvas.drawText(coinsText, startX, startY - 20f, graffitiPaint.apply { textAlign = Paint.Align.LEFT })
    }

    private fun drawLaunchReadiness(canvas: Canvas) {
        val text = if (isShipSpaceworthy()) "Ready to Launch!" else "Build a Valid Ship!"
        val textColor = if (isShipSpaceworthy()) Color.GREEN else Color.RED
        canvas.drawText(text, screenWidth / 2f - graffitiPaint.measureText(text) / 2, 50f + statusBarHeight, graffitiPaint.apply { color = textColor })
    }

    private fun drawLeaderboard(canvas: Canvas) {
        val startY = 500f + statusBarHeight
        val textHeight = 40f
        val closeButtonSize = 50f
        val closeButtonX = screenWidth - closeButtonSize - 20f
        val closeButtonY = startY - closeButtonSize - 20f

        canvas.drawRect(20f, startY - 20f, screenWidth - 20f, startY + (textHeight * 5), Paint().apply { color = Color.argb(200, 0, 0, 0) })
        canvas.drawRect(closeButtonX, closeButtonY, closeButtonX + closeButtonSize, closeButtonY + closeButtonSize, Paint().apply { color = Color.RED })
        canvas.drawText("X", closeButtonX + 15f, closeButtonY + 35f, graffitiPaint.apply { color = Color.WHITE; textSize = 30f })

        leaderboardScores.take(5).forEachIndexed { index, (player, score) ->
            canvas.drawText("$player: $score", 40f, startY + (index * textHeight), graffitiPaint)
        }
    }

    fun showLeaderboard() {
        showLeaderboard = true
        needsRedraw = true
    }

    fun hideLeaderboard() {
        showLeaderboard = false
        needsRedraw = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y - statusBarHeight
        val buildAreaBottom = screenHeight - panelHeightPx

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressX = x
                longPressY = y + statusBarHeight
                longPressStartTime = System.currentTimeMillis()
                if (gameState == GameState.BUILD && selectedPart != null && y < buildAreaBottom) {
                    Log.d("GameView", "Started dragging ${selectedPart?.type} at (x=$x, y=$y)")
                }
                if (showLeaderboard) {
                    val closeButtonSize = 50f
                    val closeButtonX = screenWidth - closeButtonSize - 20f
                    val closeButtonY = 500f - closeButtonSize - 20f
                    if (x >= closeButtonX && x <= closeButtonX + closeButtonSize && y + statusBarHeight >= closeButtonY && y + statusBarHeight <= closeButtonY + closeButtonSize) {
                        hideLeaderboard()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gameState == GameState.BUILD && selectedPart != null && y < buildAreaBottom) {
                    val (snappedX, snappedY) = snapToGrid(x, y, screenWidth, buildAreaBottom)
                    selectedPart?.x = snappedX
                    val nearest = findNearestSnap(selectedPart!!, MAGNETIZATION_THRESHOLD)
                    val finalY = if (nearest != null) {
                        val expectedY = when (nearest.type) {
                            "cockpit" -> nearest.y + cockpitHeight
                            "fuel_tank" -> nearest.y + fuelTankHeight
                            else -> snappedY
                        }
                        if (abs(selectedPart!!.x - nearest.x) < SNAP_THRESHOLD && abs(selectedPart!!.y - expectedY) < EXTENDED_SNAP_TOLERANCE) {
                            expectedY
                        } else {
                            snappedY
                        }
                    } else {
                        snappedY
                    }
                    selectedPart?.y = finalY.coerceIn(0f, buildAreaBottom - selectedPart!!.bitmap.height / 2f)
                    if (nearest != null && abs(selectedPart!!.x - nearest.x) < MAGNETIZATION_THRESHOLD && abs(selectedPart!!.y - finalY) < MAGNETIZATION_THRESHOLD) {
                        selectedPart!!.x = interpolate(selectedPart!!.x, nearest.x, 0.7f) // Stronger snap
                        selectedPart!!.y = interpolate(selectedPart!!.y, finalY, 0.7f)
                    }
                    checkAIAssistance()
                    Log.d("GameView", "Dragging ${selectedPart?.type} to (x=${selectedPart?.x}, y=${selectedPart?.y})")
                    needsRedraw = true
                } else if (gameState == GameState.FLIGHT) {
                    shipVelocityX = (x - shipX) / 50f
                    needsRedraw = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (gameState == GameState.BUILD && selectedPart != null && y < buildAreaBottom) {
                    val (snappedX, _) = snapToGrid(x, y, screenWidth, buildAreaBottom)
                    val type = selectedPart!!.type
                    var finalX = snappedX
                    var finalY = y

                    val nearest = findNearestSnap(selectedPart!!, SNAP_THRESHOLD)
                    nearest?.let {
                        val expectedY = when (it.type) {
                            "cockpit" -> it.y + cockpitHeight
                            "fuel_tank" -> it.y + fuelTankHeight
                            else -> y
                        }
                        if (abs(snappedX - it.x) < SNAP_THRESHOLD) {
                            finalX = it.x
                            finalY = expectedY
                            selectedPart?.rotation = it.rotation
                            animateRotation(selectedPart!!)
                            Log.d("GameView", "Snapped ${selectedPart?.type} to (x=$finalX, y=$finalY) below ${it.type}")
                        }
                    }

                    if (nearest == null) {
                        val (_, snappedY) = snapToGrid(x, y, screenWidth, buildAreaBottom)
                        finalY = snappedY
                    }

                    if (finalY < buildAreaBottom && !checkOverlap(finalX, finalY, selectedPart!!)) {
                        val cockpitCount = parts.count { it.type == "cockpit" }
                        val fuelTankCount = parts.count { it.type == "fuel_tank" }
                        val engineCount = parts.count { it.type == "engine" }

                        if (isValidPlacement(finalX, finalY, type, cockpitCount, fuelTankCount, engineCount)) {
                            selectedPart?.let { part ->
                                part.x = finalX
                                part.y = finalY
                                parts.removeIf { it.type == part.type }
                                parts.add(part)
                                placeSound?.seekTo(0)
                                placeSound?.start()
                                updateStats()
                                Log.d("GameView", "Dropped ${part.type} at (x=${part.x}, y=${part.y}, rot=${part.rotation}), Parts count: ${parts.size}")
                                if (part.type == "engine") particleSystem.addPropulsionParticles(part.x, part.y)
                            }
                        } else {
                            Log.d("GameView", "Invalid placement - Configuration invalid")
                        }
                    } else {
                        Log.d("GameView", "Invalid placement - Overlap detected or outside build area")
                    }
                    selectedPart = null
                    checkAIAssistance()
                    needsRedraw = true
                }
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && System.currentTimeMillis() - longPressStartTime >= LONG_PRESS_DELAY) {
            if (gameState == GameState.BUILD && longPressY < screenHeight - panelHeightPx + statusBarHeight) {
                val partToRemove = parts.find { hypot(it.x - longPressX, it.y - (longPressY - statusBarHeight)) < 32f }
                if (partToRemove != null) {
                    parts.remove(partToRemove)
                    updateStats()
                    Log.d("GameView", "Removed ${partToRemove.type} at (x=$longPressX, y=$longPressY) with long press")
                    needsRedraw = true
                } else {
                    val partToRotate = parts.find { hypot(it.x - longPressX, it.y - (longPressY - statusBarHeight)) < 32f }
                    if (partToRotate != null) {
                        partToRotate.rotation = (partToRotate.rotation + 90f) % 360f
                        animateRotation(partToRotate)
                        Log.d("GameView", "Rotated ${partToRotate.type} to ${partToRotate.rotation} degrees at (x=${partToRotate.x}, y=${partToRotate.y})")
                        needsRedraw = true
                    }
                }
            }
        }

        return true
    }

    private fun animateRotation(part: Part) {
        rotationAnimator?.cancel()
        val startRotation = part.rotation
        val endRotation = (startRotation + 90f) % 360f
        rotationAnimator = ValueAnimator.ofFloat(startRotation, endRotation).apply {
            duration = 300
            addUpdateListener { animation ->
                part.rotation = animation.animatedValue as Float
                needsRedraw = true
            }
        }
        rotationAnimator?.start()
    }

    private fun checkAIAssistance() {
        val cockpitCount = parts.count { it.type == "cockpit" }
        val fuelTankCount = parts.count { it.type == "fuel_tank" }
        val engineCount = parts.count { it.type == "engine" }

        showBlueprint = parts.size in 1..2 && (cockpitCount > 0 || fuelTankCount > 0 || engineCount > 0)

        when {
            cockpitCount == 0 -> Log.d("GameView", "AI Suggestion: Start with a cockpit at the top.")
            cockpitCount == 1 && fuelTankCount == 0 && engineCount == 0 -> Log.d("GameView", "AI Suggestion: Add a fuel tank below the cockpit.")
            cockpitCount == 1 && fuelTankCount == 1 && engineCount == 0 -> Log.d("GameView", "AI Suggestion: Add an engine below the fuel tank.")
            cockpitCount == 1 && fuelTankCount == 1 && engineCount == 1 -> Log.d("GameView", "AI Suggestion: Ship configuration looks good.")
            else -> Log.d("GameView", "AI Suggestion: Adjust parts for a valid configuration.")
        }
    }

    private fun snapToGrid(x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> {
        val buildAreaTop = 0f
        val buildAreaBottom = height
        val snappedX = ((x / gridSize).roundToInt() * gridSize).coerceIn(gridSize / 2, width - (gridSize / 2))
        val snappedY = ((y / gridSize).roundToInt() * gridSize).coerceIn(buildAreaTop + (gridSize / 2), buildAreaBottom - (gridSize / 2))
        val nearest = findNearestSnap(Part("", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), snappedX, snappedY, 0f), MAGNETIZATION_THRESHOLD)
        return if (nearest != null && selectedPart != null) {
            val expectedY = when (nearest.type) {
                "cockpit" -> nearest.y + cockpitHeight
                "fuel_tank" -> nearest.y + fuelTankHeight
                else -> snappedY
            }
            if (abs(snappedX - nearest.x) < SNAP_THRESHOLD) {
                Pair(nearest.x, expectedY.coerceIn(buildAreaTop + (gridSize / 2), buildAreaBottom - (gridSize / 2)))
            } else {
                Pair(snappedX, snappedY)
            }
        } else {
            Pair(snappedX, snappedY)
        }
    }

    private fun isValidPlacement(x: Float, y: Float, type: String, cockpitCount: Int, fuelTankCount: Int, engineCount: Int): Boolean {
        return when (type) {
            "cockpit" -> cockpitCount == 0
            "fuel_tank" -> fuelTankCount == 0 && cockpitCount == 1
            "engine" -> engineCount == 0 && fuelTankCount == 1
            else -> false
        }
    }

    private fun updateStats() {
        score = parts.size * 10 + distanceTraveled.toInt() / 100
        if (parts.any { it.type == "fuel_tank" } && fuel == 0f) {
            fuel = 50f + parts.count { it.type == "fuel_tank" } * 50f
        }
        highScore = maxOf(highScore, score)
        highScores.add(highScore)
        highScores.sortDescending()
        if (highScores.size > 5) highScores.take(5).toMutableList()
        level = (score / 50) + 1
        savePersistentData()
        needsRedraw = true
    }

    fun launchShip() {
        Log.d("GameView", "Attempting to launch ship. Parts: ${parts.size}, Configuration: ${parts.map { it.type }}")
        if (isShipSpaceworthy()) {
            gameState = GameState.FLIGHT
            shipParts.clear()
            shipParts.addAll(parts.sortedBy { it.y })
            parts.clear()
            fuel = shipParts.sumBy { if (it.type == "fuel_tank") 50 else 0 }.toFloat()
            shipX = screenWidth / 2f
            shipY = screenHeight - panelHeightPx - statusBarHeight
            distanceTraveled = 0f
            launchTime = System.currentTimeMillis()
            launchSound?.seekTo(0)
            launchSound?.start()
            engineSound?.start()
            launchListener?.invoke(true)
            Log.d("GameView", "Ship Launched - Fuel: $fuel")
            needsRedraw = true
        } else {
            Log.d("GameView", "Launch failed - Invalid ship configuration. Details: ${getSpaceworthinessFailureReason()}")
        }
    }

    private fun isShipSpaceworthy(): Boolean {
        if (parts.size != 3) {
            Log.d("GameView", "Invalid: Not 3 parts")
            return false
        }
        val sortedParts = parts.sortedBy { it.y }
        val cockpit = sortedParts[0]
        val fuelTank = sortedParts[1]
        val engine = sortedParts[2]

        val isValidOrder = cockpit.type == "cockpit" && fuelTank.type == "fuel_tank" && engine.type == "engine"
        val isAlignedX = abs(cockpit.x - fuelTank.x) < EXTENDED_SNAP_TOLERANCE && abs(fuelTank.x - engine.x) < EXTENDED_SNAP_TOLERANCE
        val yDiffCockpitToFuel = abs(fuelTank.y - (cockpit.y + cockpitHeight))
        val yDiffFuelToEngine = abs(engine.y - (fuelTank.y + fuelTankHeight))
        val isAlignedY = yDiffCockpitToFuel < EXTENDED_SNAP_TOLERANCE && yDiffFuelToEngine < EXTENDED_SNAP_TOLERANCE
        val isWithinBuildArea = cockpit.y < screenHeight - panelHeightPx && fuelTank.y < screenHeight - panelHeightPx && engine.y < screenHeight - panelHeightPx
        val isSameRotation = cockpit.rotation == fuelTank.rotation && fuelTank.rotation == engine.rotation

        Log.d("GameView", "Spaceworthiness Check - Order=$isValidOrder, XAlign=$isAlignedX, YDiffCockpitToFuel=$yDiffCockpitToFuel, YDiffFuelToEngine=$yDiffFuelToEngine, YAlign=$isAlignedY, Area=$isWithinBuildArea, Rotation=$isSameRotation")
        Log.d("GameView", "Positions - Cockpit y=${cockpit.y}, FuelTank y=${fuelTank.y}, Engine y=${engine.y}")

        return isValidOrder && isAlignedX && isAlignedY && isWithinBuildArea && isSameRotation
    }

    private fun getSpaceworthinessFailureReason(): String {
        val sortedParts = parts.sortedBy { it.y }
        return when {
            parts.size != 3 -> "Incorrect number of parts"
            sortedParts[0].type != "cockpit" -> "Cockpit must be topmost"
            sortedParts[1].type != "fuel_tank" -> "Fuel tank must be in middle"
            sortedParts[2].type != "engine" -> "Engine must be bottommost"
            abs(sortedParts[1].y - (sortedParts[0].y + cockpitHeight)) > EXTENDED_SNAP_TOLERANCE -> "Misaligned cockpit to fuel tank (diff=${abs(sortedParts[1].y - (sortedParts[0].y + cockpitHeight))})"
            abs(sortedParts[2].y - (sortedParts[1].y + fuelTankHeight)) > EXTENDED_SNAP_TOLERANCE -> "Misaligned fuel tank to engine (diff=${abs(sortedParts[2].y - (sortedParts[1].y + fuelTankHeight))})"
            !sortedParts.all { it.y < screenHeight - panelHeightPx } -> "Parts outside build area"
            sortedParts.any { it.x < 0 || it.x > screenWidth } -> "Parts outside horizontal bounds"
            sortedParts.map { it.rotation }.distinct().size > 1 -> "Parts have different rotations"
            else -> "Unknown failure"
        }
    }

    private fun endMission(success: Boolean) {
        if (success) {
            score += 50
            level++
        }
        gameState = GameState.BUILD
        shipParts.clear()
        parts.clear()
        fuel = 0f
        shipX = screenWidth / 2f
        shipY = screenHeight - panelHeightPx - statusBarHeight
        distanceTraveled = 0f
        asteroids.clear()
        storms.clear()
        crystals.clear()
        powerUps.clear()
        particleSystem.clearParticles()
        cameraShake = 0f
        backgroundOffset = 0f
        nebulaOffset = 0f
        shieldActive = false
        speedBoostActive = false
        stealthActive = false
        warpDriveActive = false
        engineSound?.pause()
        launchListener?.invoke(false)
        Log.d("GameView", "Mission Ended - Success: $success, New Score: $score, New Level: $level")
        savePersistentData()
        needsRedraw = true
    }

    private fun checkCollisions(): Boolean {
        if (shieldActive || stealthActive) return true
        enemies.forEach { enemy ->
            val distance = hypot(enemy.x - shipX, enemy.y - shipY)
            if (distance < 32) {
                endMission(false)
                return false
            }
            if (enemy.isDead()) enemies.remove(enemy)
        }
        return true
    }

    private fun findNearestSnap(part: Part, threshold: Float = SNAP_THRESHOLD): Part? {
        return parts.filter { it != part }
            .minByOrNull { other ->
                val dy = when (other.type) {
                    "cockpit" -> abs(part.y - (other.y + cockpitHeight))
                    "fuel_tank" -> abs(part.y - (other.y + fuelTankHeight))
                    else -> Float.MAX_VALUE
                }
                if (abs(part.x - other.x) < threshold && dy < threshold) dy else Float.MAX_VALUE
            }
    }

    private fun checkOverlap(x: Float, y: Float, part: Part): Boolean {
        val partRect = RectF(x - part.bitmap.width / 2f, y - part.bitmap.height / 2f, x + part.bitmap.width / 2f, y + part.bitmap.height / 2f)
        return parts.any { other ->
            if (other == part) false else {
                val otherRect = RectF(other.x - other.bitmap.width / 2f, other.y - other.bitmap.height / 2f, other.x + other.bitmap.width / 2f, other.y + other.bitmap.height / 2f)
                partRect.intersect(otherRect)
            }
        }
    }

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        this.launchListener = listener
    }

    fun rotatePart(type: String) {
        val part = parts.find { it.type == type }
        part?.let {
            it.rotation = (it.rotation + 90f) % 360f
            animateRotation(it)
            needsRedraw = true
            Log.d("GameView", "Voice command: Rotated $type to ${it.rotation} degrees")
        }
    }

    fun toggleLowAnimationMode() {
        lowAnimationMode = !lowAnimationMode
        savePersistentData()
        needsRedraw = true
    }

    data class Part(val type: String, val bitmap: Bitmap, var x: Float, var y: Float, var rotation: Float)
    data class Particle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var lifespan: Long, val bitmap: Bitmap, val scale: Float, val tint: Int) {
        fun update() { x += speedX; y += speedY; lifespan -= 16 }
        fun isDead() = lifespan <= 0
    }
    data class Enemy(var x: Float, var y: Float, val bitmap: Bitmap) {
        fun update(level: Int) { y += 2f + (level - 1) * 0.5f }
        fun isDead() = y > 600f
    }
    data class CosmicStorm(var x: Float, var y: Float) {
        fun update() { y += 1f }
        fun isDead() = y > 600f
    }
    data class MagneticAsteroid(var x: Float, var y: Float, val bitmap: Bitmap, var rotation: Float = 0f) {
        fun update(level: Int) { y += 1.5f + (level - 1) * 0.3f; rotation += 1f }
        fun isDead() = y > 600f
    }
    data class FuelCrystal(var x: Float, var y: Float) {
        fun update() { y += 1f }
        fun isDead() = y > 600f
    }
    data class PowerUp(var x: Float, var y: Float, val type: String, val bitmap: Bitmap) {
        fun update() { y += 1f }
        fun isDead() = y > 600f

        companion object {
            const val TYPE_FUEL = "fuel"
            const val TYPE_SHIELD = "shield"
            const val TYPE_SPEED = "speed"
            const val TYPE_STEALTH = "stealth"
            const val TYPE_WARP = "warp"
        }
    }
    data class Star(var x: Float, var y: Float, var brightness: Float) {
        fun update(lowAnimationMode: Boolean) {
            if (!lowAnimationMode) {
                brightness += if (Random.nextBoolean()) 0.005f else -0.005f
                if (brightness < 0.1f) brightness = 0.1f
                if (brightness > 0.5f) brightness = 0.5f
            }
        }
    }

    enum class GameState { BUILD, FLIGHT }

    fun setSelectedPart(part: Part?) {
        selectedPart = part
        if (selectedPart != null) {
            Log.d("GameView", "Selected ${part?.type} at initial position (x=${part?.x}, y=${part?.y}, rot=${part?.rotation})")
            needsRedraw = true
        }
    }

    fun setStatusBarHeight(height: Int) {
        statusBarHeight = height.toFloat()
        needsRedraw = true
    }

    // Background thread for spaceworthiness checks
    private val spaceworthinessThread = Thread {
        while (!Thread.currentThread().isInterrupted) {
            if (gameState == GameState.BUILD) {
                isShipSpaceworthy()
            }
            Thread.sleep(500) // Throttle to 2 checks per second
        }
    }.apply { start() }

    private fun startSpaceworthinessThread() {
        spaceworthinessThread.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        spaceworthinessThread.interrupt()
        backgroundMusic?.release()
        engineSound?.release()
        launchSound?.release()
        placeSound?.release()
        warpSound?.release()
        handler.removeCallbacksAndMessages(null)
    }

    private fun interpolate(start: Float, end: Float, factor: Float): Float {
        return start + (end - start) * factor
    }
}