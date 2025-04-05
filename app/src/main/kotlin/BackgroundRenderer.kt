package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sin
import kotlin.random.Random

class BackgroundRenderer @Inject constructor(
    private val context: Context // Add context to load bitmaps
) {
    private val distantStars = mutableListOf<Star>()
    private val nearStars = mutableListOf<Star>()
    private val dustParticles = mutableListOf<DustParticle>() // New: Dust particles for asteroid fields
    private val wispParticles = mutableListOf<WispParticle>() // New: Wisp particles for nebulae
    private var animationFrame = 0f

    private val backgroundPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, 2400f,
            intArrayOf(Color.parseColor("#1A0B2E"), Color.parseColor("#2E1A4B"), Color.parseColor("#4B0082")),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val starPaint = Paint().apply { isAntiAlias = true }
    private val dustPaint = Paint().apply { isAntiAlias = true } // Paint for dust particles
    private val wispPaint = Paint().apply { isAntiAlias = true } // Paint for wisp particles

    private var dustBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.dust)
        ?: throw IllegalStateException("Dust bitmap not found")
    private var wispBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.wisp)
        ?: throw IllegalStateException("Wisp bitmap not found")

    data class Star(
        var x: Float,
        var y: Float,
        val size: Float,
        val baseBrightness: Float,
        val phase: Float,
        val speed: Float,
        val color: Int
    )

    data class DustParticle(
        var x: Float,
        var y: Float,
        var speedX: Float,
        var speedY: Float,
        var life: Float,
        var scale: Float
    ) {
        fun update(screenWidth: Float, screenHeight: Float) {
            x += speedX
            y += speedY
            life -= 0.01f
            if (y > screenHeight + 10f || x < -10f || x > screenWidth + 10f) {
                y -= screenHeight + 20f
                x = Random.nextFloat() * screenWidth
                life = 1f
            }
        }
        fun isDead() = life <= 0f
    }

    data class WispParticle(
        var x: Float,
        var y: Float,
        var speedX: Float,
        var speedY: Float,
        var life: Float,
        var scale: Float,
        var phase: Float
    ) {
        fun update(screenWidth: Float, screenHeight: Float) {
            x += speedX
            y += speedY
            scale = 0.5f + 0.5f * sin(phase)
            phase += 0.05f
            life -= 0.005f
            if (y > screenHeight + 10f || x < -10f || x > screenWidth + 10f) {
                y -= screenHeight + 20f
                x = Random.nextFloat() * screenWidth
                life = 1f
            }
        }
        fun isDead() = life <= 0f
    }

    init {
        repeat(100) {
            distantStars.add(
                Star(
                    x = Random.nextFloat() * 10000f,
                    y = Random.nextFloat() * 10000f,
                    size = Random.nextFloat() * 1f + 0.5f,
                    baseBrightness = Random.nextFloat() * 0.5f + 0.1f,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    speed = 1f,
                    color = Color.argb(255, 200, 200, 255)
                )
            )
        }
        repeat(50) {
            nearStars.add(
                Star(
                    x = Random.nextFloat() * 10000f,
                    y = Random.nextFloat() * 10000f,
                    size = Random.nextFloat() * 2f + 1f,
                    baseBrightness = Random.nextFloat() * 0.5f + 0.5f,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    speed = 3f,
                    color = Color.argb(255, 255, 255, 200)
                )
            )
        }
        // Initialize dust particles
        repeat(50) {
            dustParticles.add(
                DustParticle(
                    x = Random.nextFloat() * 10000f,
                    y = Random.nextFloat() * 10000f,
                    speedX = Random.nextFloat() * 2f - 1f,
                    speedY = Random.nextFloat() * 3f + 1f,
                    life = 1f,
                    scale = Random.nextFloat() * 0.5f + 0.5f
                )
            )
        }
        // Initialize wisp particles
        repeat(30) {
            wispParticles.add(
                WispParticle(
                    x = Random.nextFloat() * 10000f,
                    y = Random.nextFloat() * 10000f,
                    speedX = Random.nextFloat() * 1f - 0.5f,
                    speedY = Random.nextFloat() * 2f + 0.5f,
                    life = 1f,
                    scale = 1f,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat()
                )
            )
        }
    }

    fun updateAnimationFrame() {
        animationFrame = (animationFrame + 0.05f) % (2 * Math.PI.toFloat())
    }

    fun drawBackground(canvas: Canvas, screenWidth: Float, screenHeight: Float, level: Int = 1, environment: FlightModeManager.Environment) {
        val gradientColors = when (environment) {
            FlightModeManager.Environment.ASTEROID_FIELD -> intArrayOf(
                Color.parseColor("#2E2E2E"),
                Color.parseColor("#4B4B4B"),
                Color.parseColor("#6C6C6C")
            )
            FlightModeManager.Environment.NEBULA -> intArrayOf(
                Color.parseColor("#4B0082"),
                Color.parseColor("#9400D3"),
                Color.parseColor("#8A2BE2")
            )
            FlightModeManager.Environment.NORMAL -> {
                val gradientIndex = (level - 1) / 10 % 5
                val gradients = listOf(
                    intArrayOf(Color.parseColor("#1A0B2E"), Color.parseColor("#2E1A4B"), Color.parseColor("#4B0082")),
                    intArrayOf(Color.parseColor("#0A2E4B"), Color.parseColor("#1A4B2E"), Color.parseColor("#008282")),
                    intArrayOf(Color.parseColor("#2E0A4B"), Color.parseColor("#4B2E0A"), Color.parseColor("#82004B")),
                    intArrayOf(Color.parseColor("#4B2E0A"), Color.parseColor("#2E4B0A"), Color.parseColor("#828200")),
                    intArrayOf(Color.parseColor("#0A4B2E"), Color.parseColor("#2E0A4B"), Color.parseColor("#00824B"))
                )
                gradients[gradientIndex]
            }
        }
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, screenHeight,
            gradientColors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, backgroundPaint)

        distantStars.forEach { star ->
            star.y += star.speed
            if (star.y > screenHeight + star.size) {
                star.y -= screenHeight + star.size * 2
                star.x = Random.nextFloat() * screenWidth
            }
            val brightness = star.baseBrightness * (sin(animationFrame + star.phase) * 0.3f + 0.7f).coerceIn(0f, 1f)
            starPaint.color = Color.argb((brightness * 255).toInt(), Color.red(star.color), Color.green(star.color), Color.blue(star.color))
            canvas.drawCircle(star.x, star.y, star.size, starPaint)
        }

        nearStars.forEach { star ->
            star.y += star.speed
            if (star.y > screenHeight + star.size) {
                star.y -= screenHeight + star.size * 2
                star.x = Random.nextFloat() * screenWidth
            }
            val brightness = star.baseBrightness * (sin(animationFrame + star.phase) * 0.4f + 0.6f).coerceIn(0f, 1f)
            starPaint.color = Color.argb((brightness * 255).toInt(), Color.red(star.color), Color.green(star.color), Color.blue(star.color))
            canvas.drawCircle(star.x, star.y, star.size, starPaint)
        }

        // Draw environment-specific particles
        when (environment) {
            FlightModeManager.Environment.ASTEROID_FIELD -> drawDustParticles(canvas, screenWidth, screenHeight)
            FlightModeManager.Environment.NEBULA -> drawWispParticles(canvas, screenWidth, screenHeight)
            else -> {} // No additional particles for NORMAL environment
        }
    }

    private fun drawDustParticles(canvas: Canvas, screenWidth: Float, screenHeight: Float) {
        if (dustBitmap.isRecycled) {
            Timber.w("Dust bitmap was recycled, reinitializing")
            dustBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.dust)
                ?: throw IllegalStateException("Failed to reload dust bitmap")
        }

        val particlesToRemove = mutableListOf<DustParticle>()
        dustParticles.forEach { particle ->
            particle.update(screenWidth, screenHeight)
            dustPaint.alpha = (particle.life * 128).toInt() // Semi-transparent
            val scaledWidth = dustBitmap.width * particle.scale
            val scaledHeight = dustBitmap.height * particle.scale
            canvas.save()
            canvas.translate(particle.x - scaledWidth / 2f, particle.y - scaledHeight / 2f)
            canvas.scale(particle.scale, particle.scale)
            canvas.drawBitmap(dustBitmap, 0f, 0f, dustPaint)
            canvas.restore()
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        dustParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${dustParticles.size} dust particles")
    }

    private fun drawWispParticles(canvas: Canvas, screenWidth: Float, screenHeight: Float) {
        if (wispBitmap.isRecycled) {
            Timber.w("Wisp bitmap was recycled, reinitializing")
            wispBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.wisp)
                ?: throw IllegalStateException("Failed to reload wisp bitmap")
        }

        val particlesToRemove = mutableListOf<WispParticle>()
        wispParticles.forEach { particle ->
            particle.update(screenWidth, screenHeight)
            wispPaint.alpha = (particle.life * 191).toInt() // Slightly more opaque than dust
            val scaledWidth = wispBitmap.width * particle.scale
            val scaledHeight = wispBitmap.height * particle.scale
            canvas.save()
            canvas.translate(particle.x - scaledWidth / 2f, particle.y - scaledHeight / 2f)
            canvas.scale(particle.scale, particle.scale)
            canvas.drawBitmap(wispBitmap, 0f, 0f, wispPaint)
            canvas.restore()
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        wispParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${wispParticles.size} wisp particles")
    }
}