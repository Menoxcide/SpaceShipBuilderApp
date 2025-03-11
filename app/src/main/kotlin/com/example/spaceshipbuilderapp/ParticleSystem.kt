package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.withSave // Added import
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class ParticleSystem(private val context: Context) {
    private val exhaustParticles = CopyOnWriteArrayList<ExhaustParticle>()
    private val warpParticles = CopyOnWriteArrayList<WarpParticle>()
    private val exhaustBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.exhaust)
    private val exhaustPaint = Paint().apply { isAntiAlias = true }

    companion object {
        const val EXHAUST_WIDTH = 13f
        const val EXHAUST_HEIGHT = 32f
        const val EXHAUST_LIFE_DECAY = 0.05f
        const val WARP_LIFE_DECAY = 0.03f
    }

    data class ExhaustParticle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var life: Float) {
        fun update() {
            x += speedX
            y += speedY
            life -= EXHAUST_LIFE_DECAY
        }

        fun isDead() = life <= 0f
    }

    data class WarpParticle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var life: Float) {
        fun update() {
            x += speedX
            y += speedY
            life -= WARP_LIFE_DECAY
        }

        fun isDead() = life <= 0f
    }

    fun addExhaustParticle(x: Float, y: Float, speedX: Float, speedY: Float) {
        exhaustParticles.add(ExhaustParticle(x, y, speedX, speedY, 1f))
    }

    fun addWarpParticle(x: Float, y: Float) {
        warpParticles.add(WarpParticle(x, y, Random.nextFloat() * 4f - 2f, Random.nextFloat() * 4f - 2f, 1f))
    }

    fun addPropulsionParticles(x: Float, y: Float) {
        addExhaustParticle(x, y, Random.nextFloat() * 2f - 1f, Random.nextFloat() * 5f + 5f)
    }

    fun drawExhaustParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<ExhaustParticle>()
        exhaustParticles.forEach { particle ->
            particle.update()
            exhaustPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(1f - particle.life)
                val colorScale = floatArrayOf(
                    1f, 0f, 0f, 0f, 255f * (1f - particle.life),
                    0f, 1f, 0f, 0f, 69f * particle.life,
                    0f, 0f, 1f, 0f, 255f * particle.life,
                    0f, 0f, 0f, 1f, 0f
                )
                set(colorScale)
            })
            canvas.withSave {
                canvas.translate(particle.x, particle.y) // Explicit canvas call
                canvas.scale(particle.life, particle.life, EXHAUST_WIDTH / 2, EXHAUST_HEIGHT / 2) // Explicit canvas call
                canvas.drawBitmap(exhaustBitmap, 0f, 0f, exhaustPaint) // Explicit canvas call
            }
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        exhaustParticles.removeAll(particlesToRemove)
    }

    fun drawWarpEffect(canvas: Canvas, x: Float, y: Float) {
        val particlesToRemove = mutableListOf<WarpParticle>()
        warpParticles.forEach { particle ->
            particle.update()
            val paint = Paint().apply {
                color = Color.argb((particle.life * 255).toInt(), 255, 255, 255)
            }
            canvas.drawCircle(particle.x, particle.y, particle.life * 10f, paint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        warpParticles.removeAll(particlesToRemove)
    }

    fun clearParticles() {
        exhaustParticles.clear()
        warpParticles.clear()
    }

    fun onDestroy() {
        if (!exhaustBitmap.isRecycled) exhaustBitmap.recycle()
        clearParticles()
    }
}