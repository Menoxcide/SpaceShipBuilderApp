package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.withSave
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import timber.log.Timber

class ParticleSystem(private val context: Context) {
    private val exhaustParticles = CopyOnWriteArrayList<ExhaustParticle>()
    private val warpParticles = CopyOnWriteArrayList<WarpParticle>()
    private val collectionParticles = CopyOnWriteArrayList<CollectionParticle>()
    private val exhaustBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.exhaust)
    private val exhaustPaint = Paint().apply { isAntiAlias = true }
    private val collectionPaint = Paint().apply { isAntiAlias = true }

    companion object {
        const val EXHAUST_WIDTH = 13f
        const val EXHAUST_HEIGHT = 32f
        const val EXHAUST_LIFE_DECAY = 0.05f
        const val WARP_LIFE_DECAY = 0.03f
        const val COLLECTION_LIFE_DECAY = 0.01f
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

    data class CollectionParticle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var life: Float, var size: Float) {
        fun update() {
            x += speedX
            y += speedY
            life -= COLLECTION_LIFE_DECAY
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

    fun addCollectionParticles(x: Float, y: Float) {
        repeat(10) { // Increased number of particles for better visibility
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 5f + 2f
            collectionParticles.add(
                CollectionParticle(
                    x = x,
                    y = y,
                    speedX = kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    speedY = kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    life = 1f,
                    size = Random.nextFloat() * 5f + 3f
                )
            )
        }
        Timber.d("Added ${collectionParticles.size} collection particles at (x=$x, y=$y)")
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
                canvas.translate(particle.x, particle.y)
                canvas.scale(particle.life, particle.life, EXHAUST_WIDTH / 2, EXHAUST_HEIGHT / 2)
                canvas.drawBitmap(exhaustBitmap, 0f, 0f, exhaustPaint)
            }
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        exhaustParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${exhaustParticles.size} exhaust particles")
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
        Timber.d("Drawing ${warpParticles.size} warp particles")
    }

    fun drawCollectionParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<CollectionParticle>()
        collectionParticles.forEach { particle ->
            particle.update()
            collectionPaint.color = Color.YELLOW
            collectionPaint.alpha = (particle.life * 255).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size, collectionPaint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        collectionParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${collectionParticles.size} collection particles")
    }

    fun clearParticles() {
        exhaustParticles.clear()
        warpParticles.clear()
        collectionParticles.clear()
    }

    fun onDestroy() {
        if (!exhaustBitmap.isRecycled) exhaustBitmap.recycle()
        clearParticles()
    }
}