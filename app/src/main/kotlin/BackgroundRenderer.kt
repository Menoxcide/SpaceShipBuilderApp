package com.example.spaceshipbuilderapp

import android.graphics.*
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sin
import kotlin.random.Random

class BackgroundRenderer @Inject constructor() {
    private val distantStars = mutableListOf<Star>()
    private val nearStars = mutableListOf<Star>()
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

    data class Star(
        var x: Float,
        var y: Float,
        val size: Float,
        val baseBrightness: Float,
        val phase: Float,
        val speed: Float,
        val color: Int
    )

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
    }

    fun updateAnimationFrame() {
        animationFrame = (animationFrame + 0.05f) % (2 * Math.PI.toFloat())
    }

    fun drawBackground(canvas: Canvas, screenWidth: Float, screenHeight: Float, level: Int = 1) {
        val gradientIndex = (level - 1) / 10 % 5
        val gradients = listOf(
            intArrayOf(Color.parseColor("#1A0B2E"), Color.parseColor("#2E1A4B"), Color.parseColor("#4B0082")),
            intArrayOf(Color.parseColor("#0A2E4B"), Color.parseColor("#1A4B2E"), Color.parseColor("#008282")),
            intArrayOf(Color.parseColor("#2E0A4B"), Color.parseColor("#4B2E0A"), Color.parseColor("#82004B")),
            intArrayOf(Color.parseColor("#4B2E0A"), Color.parseColor("#2E4B0A"), Color.parseColor("#828200")),
            intArrayOf(Color.parseColor("#0A4B2E"), Color.parseColor("#2E0A4B"), Color.parseColor("#00824B"))
        )
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, screenHeight,
            gradients[gradientIndex],
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
    }
}