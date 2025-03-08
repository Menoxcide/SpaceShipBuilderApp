package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin
import kotlin.random.Random

class ParticleSystem(private val context: Context) {
    private val exhaustParticles = CopyOnWriteArrayList<ExhaustParticle>()
    private val warpParticles = CopyOnWriteArrayList<WarpParticle>()
    private val exhaustBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.exhaust)
    private val exhaustWidth = 13f
    private val exhaustHeight = 32f

    fun addExhaustParticle(x: Float, y: Float, speedX: Float, speedY: Float) {
        exhaustParticles.add(ExhaustParticle(x, y, speedX, speedY))
    }

    fun addPropulsionParticles(x: Float, y: Float) {
        repeat(5) {
            exhaustParticles.add(ExhaustParticle(x, y, Random.nextFloat() * 2f - 1f, 5f))
        }
    }

    fun addMagnetizationParticle(x: Float, y: Float) { // For magnetization feedback
        warpParticles.add(WarpParticle(x, y, Random.nextFloat() * 2f - 1f, Random.nextFloat() * 2f - 1f))
    }

    // Re-added addWarpParticle method to fix the unresolved reference
    fun addWarpParticle(x: Float, y: Float) {
        warpParticles.add(WarpParticle(x, y, Random.nextFloat() * 4f - 2f, Random.nextFloat() * 4f - 2f))
    }

    fun drawExhaustParticles(canvas: Canvas) {
        exhaustParticles.forEach { particle ->
            particle.update()
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(1f - particle.life)
                    val colorScale = floatArrayOf(
                        1f, 0f, 0f, 0f, 255f * (1f - particle.life), // Red (FF4500 to 00FFFF)
                        0f, 1f, 0f, 0f, 69f * particle.life,          // Green
                        0f, 0f, 1f, 0f, 255f * particle.life,         // Blue
                        0f, 0f, 0f, 1f, 0f
                    )
                    set(colorScale)
                })
            }
            canvas.save()
            canvas.translate(particle.x, particle.y)
            canvas.scale(particle.life, particle.life, exhaustWidth / 2, exhaustHeight / 2)
            canvas.drawBitmap(exhaustBitmap, 0f, 0f, paint)
            canvas.restore()
            if (particle.isDead()) exhaustParticles.remove(particle)
        }
    }

    fun drawPulsatingExhaust(canvas: Canvas, x: Float, y: Float) {
        val scale = (sin(System.currentTimeMillis() / 200.0) * 0.5 + 1).toFloat()
        canvas.drawBitmap(
            Bitmap.createScaledBitmap(exhaustBitmap, (exhaustBitmap.width * scale).toInt(), (exhaustBitmap.height * scale).toInt(), true),
            x - (exhaustBitmap.width * scale / 2), y, null
        )
    }

    fun drawMagnetizationEffect(canvas: Canvas, x: Float, y: Float) { // For magnetization feedback
        repeat(3) {
            addMagnetizationParticle(x, y)
        }
        warpParticles.forEach { particle ->
            particle.update()
            val paint = Paint().apply {
                color = Color.argb((particle.life * 255).toInt(), 0, 255, 0) // Green for magnetization
            }
            canvas.drawCircle(particle.x, particle.y, particle.life * 10f, paint)
            if (particle.isDead()) warpParticles.remove(particle)
        }
    }

    fun drawWarpEffect(canvas: Canvas, x: Float, y: Float) {
        repeat(5) {
            addWarpParticle(x, y) // Now resolved with the added method
        }
        warpParticles.forEach { particle ->
            particle.update()
            val paint = Paint().apply {
                color = Color.argb((particle.life * 255).toInt(), 255, 255, 255)
            }
            canvas.drawCircle(particle.x, particle.y, particle.life * 10f, paint)
            if (particle.isDead()) warpParticles.remove(particle)
        }
    }

    fun clearParticles() {
        exhaustParticles.clear()
        warpParticles.clear()
    }

    data class ExhaustParticle(var x: Float, var y: Float, val speedX: Float, var speedY: Float, var life: Float = 1f) {
        fun update() {
            x += speedX
            y += speedY
            life -= 0.05f
        }
        fun isDead() = life <= 0
    }

    data class WarpParticle(var x: Float, var y: Float, val speedX: Float, val speedY: Float, var life: Float = 1f) {
        fun update() {
            x += speedX
            y += speedY
            life -= 0.03f
        }
        fun isDead() = life <= 0
    }
}