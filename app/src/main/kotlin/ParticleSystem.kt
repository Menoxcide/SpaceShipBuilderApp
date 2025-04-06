package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.withSave
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin
import kotlin.random.Random
import timber.log.Timber

class ParticleSystem(private val context: Context) {
    private val exhaustParticles = CopyOnWriteArrayList<ExhaustParticle>()
    private val warpParticles = CopyOnWriteArrayList<WarpParticle>()
    private val collectionParticles = CopyOnWriteArrayList<CollectionParticle>()
    private val collisionParticles = CopyOnWriteArrayList<CollisionParticle>()
    private val damageTextParticles = CopyOnWriteArrayList<DamageTextParticle>()
    private val powerUpTextParticles = CopyOnWriteArrayList<PowerUpTextParticle>()
    private val explosionParticles = CopyOnWriteArrayList<ExplosionParticle>()
    private val powerUpSpriteParticles = CopyOnWriteArrayList<PowerUpSpriteParticle>()
    private val scoreTextParticles = CopyOnWriteArrayList<ScoreTextParticle>()
    private val missileExhaustParticles = CopyOnWriteArrayList<MissileExhaustParticle>()
    private val sparkleParticles = CopyOnWriteArrayList<SparkleParticle>()

    private var exhaustBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.exhaust)
        ?: throw IllegalStateException("Exhaust bitmap not found")
    private var sparkleBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.sparkle)
        ?: throw IllegalStateException("Sparkle bitmap not found")

    private val exhaustPaint = Paint().apply { isAntiAlias = true }
    private val collectionPaint = Paint().apply { isAntiAlias = true }
    private val collisionPaint = Paint().apply { isAntiAlias = true }
    private val damageTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    private val powerUpSpritePaint = Paint().apply { isAntiAlias = true }
    private val explosionPaint = Paint().apply {
        isAntiAlias = true
    }
    private val scoreTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = 30f
        color = Color.GREEN
        textAlign = Paint.Align.CENTER
    }
    private val missileExhaustPaint = Paint().apply {
        isAntiAlias = true
        color = Color.YELLOW
    }
    private val sparklePaint = Paint().apply { isAntiAlias = true }
    private val powerUpTextPaints = mapOf(
        "power_up" to Paint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.YELLOW
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
        "shield" to Paint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.BLUE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
        "speed" to Paint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.RED
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
        "stealth" to Paint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
        "warp" to Paint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.MAGENTA
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
        "star" to Paint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
        "invincibility" to Paint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.GREEN
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    )

    companion object {
        const val EXHAUST_WIDTH = 13f
        const val EXHAUST_HEIGHT = 32f
        const val EXHAUST_LIFE_DECAY = 0.05f
        const val WARP_LIFE_DECAY = 0.03f
        const val COLLECTION_LIFE_DECAY = 0.01f
        const val COLLISION_LIFE_DECAY = 0.02f
        const val DAMAGE_TEXT_LIFE_DECAY = 0.01f
        const val POWER_UP_TEXT_LIFE_DECAY = 0.02f
        const val POWER_UP_SPRITE_LIFE_DECAY = 0.005f
        const val EXPLOSION_PARTICLE_COUNT = 30
        const val EXPLOSION_LIFE_DECAY = 0.03f
        const val POWER_UP_SPRITE_COUNT = 5
        const val SCORE_TEXT_LIFE_DECAY = 0.02f
        const val MISSILE_EXHAUST_LIFE_DECAY = 0.05f
        const val SPARKLE_LIFE_DECAY = 0.02f
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

    data class CollisionParticle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var life: Float, var size: Float) {
        fun update() {
            x += speedX
            y += speedY
            life -= COLLISION_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    data class DamageTextParticle(var x: Float, var y: Float, var speedY: Float, var life: Float, val text: String) {
        fun update() {
            y += speedY
            life -= DAMAGE_TEXT_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    data class PowerUpTextParticle(var x: Float, var y: Float, var speedY: Float, var life: Float, val text: String, val type: String) {
        fun update() {
            y += speedY
            life -= POWER_UP_TEXT_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    data class ExplosionParticle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var life: Float, var size: Float, var color: Int) {
        fun update() {
            x += speedX
            y += speedY
            life -= EXPLOSION_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    data class PowerUpSpriteParticle(
        var x: Float,
        var y: Float,
        var bitmap: Bitmap,
        var scale: Float,
        var minScale: Float,
        var maxScale: Float,
        var life: Float,
        var phase: Float
    ) {
        fun update() {
            scale = minScale + (maxScale - minScale) * (sin(phase) + 1) / 2
            phase += 0.05f
            life -= POWER_UP_SPRITE_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    data class ScoreTextParticle(var x: Float, var y: Float, var speedY: Float, var life: Float, val text: String) {
        fun update() {
            y += speedY
            life -= SCORE_TEXT_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    data class MissileExhaustParticle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var life: Float, var size: Float) {
        fun update() {
            x += speedX
            y += speedY
            life -= MISSILE_EXHAUST_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    data class SparkleParticle(var x: Float, var y: Float, var speedX: Float, var speedY: Float, var life: Float, var scale: Float) {
        fun update() {
            x += speedX
            y += speedY
            life -= SPARKLE_LIFE_DECAY
        }
        fun isDead() = life <= 0f
    }

    private val powerUpBitmaps = mapOf(
        "shield" to BitmapFactory.decodeResource(context.resources, R.drawable.shield_icon),
        "speed" to BitmapFactory.decodeResource(context.resources, R.drawable.speed_icon),
        "stealth" to BitmapFactory.decodeResource(context.resources, R.drawable.stealth_icon),
        "invincibility" to BitmapFactory.decodeResource(context.resources, R.drawable.invincibility_icon)
    )

    fun addExhaustParticle(x: Float, y: Float, speedX: Float, speedY: Float) {
        exhaustParticles.add(ExhaustParticle(x, y, speedX, speedY, 1f))
    }

    fun addWarpParticle(x: Float, y: Float) {
        warpParticles.add(WarpParticle(x, y, Random.nextFloat() * 4f - 2f, Random.nextFloat() * 4f - 2f, 1f))
    }

    fun addPropulsionParticles(x: Float, y: Float, speedBoostActive: Boolean = false) {
        val speedMultiplier = if (speedBoostActive) 5f else 1f
        addExhaustParticle(
            x, y,
            Random.nextFloat() * 2f - 1f * speedMultiplier, // Adjusted to ensure symmetry around center
            Random.nextFloat() * 5f * speedMultiplier + 5f * speedMultiplier
        )
    }

    fun addCollectionParticles(x: Float, y: Float) {
        repeat(10) {
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

    fun addCollisionParticles(x: Float, y: Float) {
        repeat(15) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 6f + 3f
            collisionParticles.add(
                CollisionParticle(
                    x = x,
                    y = y,
                    speedX = kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    speedY = kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    life = 1f,
                    size = Random.nextFloat() * 4f + 2f
                )
            )
        }
        Timber.d("Added ${collisionParticles.size} collision particles at (x=$x, y=$y)")
    }

    fun addDamageTextParticle(x: Float, y: Float, damage: Int) {
        damageTextParticles.add(
            DamageTextParticle(
                x = x,
                y = y,
                speedY = -2f,
                life = 1f,
                text = "-$damage"
            )
        )
        Timber.d("Added damage text particle at (x=$x, y=$y) with text: -$damage")
    }

    fun addPowerUpTextParticle(x: Float, y: Float, text: String, powerUpType: String) {
        powerUpTextParticles.add(
            PowerUpTextParticle(
                x = x,
                y = y,
                speedY = -2f,
                life = 1.5f,
                text = text,
                type = powerUpType
            )
        )
        Timber.d("Added power-up text particle at (x=$x, y=$y) with text: $text")
    }

    fun addPowerUpSpriteParticles(x: Float, y: Float, powerUpType: String) {
        val bitmap = powerUpBitmaps[powerUpType] ?: return
        repeat(POWER_UP_SPRITE_COUNT) {
            powerUpSpriteParticles.add(
                PowerUpSpriteParticle(
                    x = x,
                    y = y,
                    bitmap = bitmap,
                    scale = 0.5f,
                    minScale = 0.5f,
                    maxScale = 1.0f,
                    life = 1.0f,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat()
                )
            )
        }
        addSparkleParticles(x, y)
        Timber.d("Added ${powerUpSpriteParticles.size} power-up sprite particles and sparkles at (x=$x, y=$y) for $powerUpType")
    }

    fun addExplosionParticles(x: Float, y: Float) {
        repeat(EXPLOSION_PARTICLE_COUNT) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 10f + 5f
            val color = if (Random.nextBoolean()) Color.rgb(255, 165, 0) else Color.YELLOW
            explosionParticles.add(
                ExplosionParticle(
                    x = x,
                    y = y,
                    speedX = kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    speedY = kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    life = 1.0f,
                    size = Random.nextFloat() * 10f + 5f,
                    color = color
                )
            )
        }
        Timber.d("Added ${explosionParticles.size} explosion particles at (x=$x, y=$y)")
    }

    fun addScoreTextParticle(x: Float, y: Float, text: String) {
        scoreTextParticles.add(
            ScoreTextParticle(
                x = x,
                y = y,
                speedY = -2f,
                life = 1.5f,
                text = text
            )
        )
        Timber.d("Added score text particle at (x=$x, y=$y) with text: $text")
    }

    fun addMissileExhaustParticles(x: Float, y: Float) {
        repeat(2) {
            val speedX = Random.nextFloat() * 2f - 1f
            val speedY = Random.nextFloat() * 3f + 2f
            missileExhaustParticles.add(
                MissileExhaustParticle(
                    x = x,
                    y = y,
                    speedX = speedX,
                    speedY = speedY,
                    life = 0.5f,
                    size = Random.nextFloat() * 3f + 2f
                )
            )
        }
    }

    fun addSparkleParticles(x: Float, y: Float) {
        repeat(10) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 4f + 2f
            sparkleParticles.add(
                SparkleParticle(
                    x = x,
                    y = y,
                    speedX = kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    speedY = kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * speed,
                    life = 1f,
                    scale = Random.nextFloat() * 0.5f + 0.5f
                )
            )
        }
        Timber.d("Added ${sparkleParticles.size} sparkle particles at (x=$x, y=$y)")
    }

    fun drawExhaustParticles(canvas: Canvas) {
        if (exhaustBitmap.isRecycled) {
            Timber.w("Exhaust bitmap was recycled, reinitializing")
            exhaustBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.exhaust)
                ?: throw IllegalStateException("Failed to reload exhaust bitmap")
        }

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
                translate(particle.x - EXHAUST_WIDTH / 2f, particle.y - EXHAUST_HEIGHT / 2f) // Center the bitmap
                scale(particle.life, particle.life)
                drawBitmap(exhaustBitmap, 0f, 0f, exhaustPaint)
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

    fun drawCollisionParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<CollisionParticle>()
        collisionParticles.forEach { particle ->
            particle.update()
            collisionPaint.color = Color.RED
            collisionPaint.alpha = (particle.life * 255).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size, collisionPaint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        collisionParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${collisionParticles.size} collision particles")
    }

    fun drawDamageTextParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<DamageTextParticle>()
        damageTextParticles.forEach { particle ->
            particle.update()
            damageTextPaint.alpha = (particle.life * 255).toInt()
            canvas.drawText(particle.text, particle.x, particle.y, damageTextPaint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        damageTextParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${damageTextParticles.size} damage text particles")
    }

    fun drawPowerUpSpriteParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<PowerUpSpriteParticle>()
        powerUpSpriteParticles.forEach { particle ->
            particle.update()
            powerUpSpritePaint.alpha = (particle.life * 191).toInt()
            val scaledWidth = particle.bitmap.width * particle.scale
            val scaledHeight = particle.bitmap.height * particle.scale
            canvas.withSave {
                translate(particle.x - scaledWidth / 2, particle.y - scaledHeight / 2)
                scale(particle.scale, particle.scale)
                drawBitmap(particle.bitmap, 0f, 0f, powerUpSpritePaint)
            }
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        powerUpSpriteParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${powerUpSpriteParticles.size} power-up sprite particles")
    }

    fun drawExplosionParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<ExplosionParticle>()
        explosionParticles.forEach { particle ->
            particle.update()
            explosionPaint.color = particle.color
            explosionPaint.alpha = (particle.life * 255).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size * particle.life, explosionPaint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        explosionParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${explosionParticles.size} explosion particles")
    }

    fun drawPowerUpTextParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<PowerUpTextParticle>()
        powerUpTextParticles.forEach { particle ->
            particle.update()
            val paint = powerUpTextPaints[particle.type] ?: powerUpTextPaints["power_up"]!!
            paint.alpha = (particle.life * 255).toInt()
            canvas.drawText(particle.text, particle.x, particle.y, paint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        powerUpTextParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${powerUpTextParticles.size} power-up text particles")
    }

    fun drawScoreTextParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<ScoreTextParticle>()
        scoreTextParticles.forEach { particle ->
            particle.update()
            scoreTextPaint.alpha = (particle.life * 255).toInt()
            canvas.drawText(particle.text, particle.x, particle.y, scoreTextPaint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        scoreTextParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${scoreTextParticles.size} score text particles")
    }

    fun drawMissileExhaustParticles(canvas: Canvas) {
        val particlesToRemove = mutableListOf<MissileExhaustParticle>()
        missileExhaustParticles.forEach { particle ->
            particle.update()
            missileExhaustPaint.alpha = (particle.life * 255).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size, missileExhaustPaint)
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        missileExhaustParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${missileExhaustParticles.size} missile exhaust particles")
    }

    fun drawSparkleParticles(canvas: Canvas) {
        if (sparkleBitmap.isRecycled) {
            Timber.w("Sparkle bitmap was recycled, reinitializing")
            sparkleBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.sparkle)
                ?: throw IllegalStateException("Failed to reload sparkle bitmap")
        }

        val particlesToRemove = mutableListOf<SparkleParticle>()
        sparkleParticles.forEach { particle ->
            particle.update()
            sparklePaint.alpha = (particle.life * 255).toInt()
            val scaledWidth = sparkleBitmap.width * particle.scale
            val scaledHeight = sparkleBitmap.height * particle.scale
            canvas.withSave {
                translate(particle.x - scaledWidth / 2, particle.y - scaledHeight / 2)
                scale(particle.scale, particle.scale)
                drawBitmap(sparkleBitmap, 0f, 0f, sparklePaint)
            }
            if (particle.isDead()) particlesToRemove.add(particle)
        }
        sparkleParticles.removeAll(particlesToRemove)
        Timber.d("Drawing ${sparkleParticles.size} sparkle particles")
    }

    fun clearParticles() {
        exhaustParticles.clear()
        warpParticles.clear()
        collectionParticles.clear()
        collisionParticles.clear()
        damageTextParticles.clear()
        powerUpTextParticles.clear()
        explosionParticles.clear()
        powerUpSpriteParticles.clear()
        scoreTextParticles.clear()
        missileExhaustParticles.clear()
        sparkleParticles.clear()
    }

    fun onDestroy() {
        clearParticles()
        Timber.d("ParticleSystem onDestroy called")
    }

    fun getGlowParticleCount(): Int {
        return powerUpSpriteParticles.size
    }
}