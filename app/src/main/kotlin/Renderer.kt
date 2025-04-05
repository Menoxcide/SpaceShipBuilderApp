package com.example.spaceshipbuilderapp

import android.graphics.Bitmap
import android.graphics.Canvas
import timber.log.Timber
import javax.inject.Inject

class Renderer @Inject constructor(
    private val bitmapManager: BitmapManager,
    private val backgroundRenderer: BackgroundRenderer,
    private val shipRenderer: ShipRenderer,
    private val gameObjectRenderer: GameObjectRenderer,
    internal val uiRenderer: UIRenderer
) {
    private var currentShipSet: Int = 0

    val cockpitBitmap: Bitmap get() = bitmapManager.getShipSet(currentShipSet).cockpit
    val fuelTankBitmap: Bitmap get() = bitmapManager.getShipSet(currentShipSet).fuelTank
    val engineBitmap: Bitmap get() = bitmapManager.getShipSet(currentShipSet).engine

    val cockpitPlaceholderBitmap: Bitmap get() = bitmapManager.createPlaceholderBitmap(cockpitBitmap)
    val fuelTankPlaceholderBitmap: Bitmap get() = bitmapManager.createPlaceholderBitmap(fuelTankBitmap)
    val enginePlaceholderBitmap: Bitmap get() = bitmapManager.createPlaceholderBitmap(engineBitmap)

    val shipRendererInstance: ShipRenderer get() = shipRenderer

    fun setShipSet(shipSet: Int) {
        currentShipSet = shipSet
        Timber.d("Renderer ship set updated to: $shipSet")
    }

    fun updateAnimationFrame() {
        backgroundRenderer.updateAnimationFrame()
        gameObjectRenderer.updateGlowAnimation() // Add this to update glow effect
        uiRenderer.updateAnimationTime()
    }

    fun showUnlockMessage(messages: List<String>) {
        uiRenderer.showUnlockMessage(messages)
    }

    fun drawBackground(canvas: Canvas, screenWidth: Float, screenHeight: Float, statusBarHeight: Float, level: Int = 1, environment: FlightModeManager.Environment) {
        backgroundRenderer.drawBackground(canvas, screenWidth, screenHeight, level, environment)
        uiRenderer.setScreenDimensions(screenWidth, screenHeight)
    }

    fun drawParts(canvas: Canvas, parts: List<Part>) {
        parts.forEach { part ->
            val x = part.x - (part.bitmap.width * part.scale / 2f)
            val y = part.y - (part.bitmap.height * part.scale / 2f)
            canvas.save()
            canvas.translate(part.x, part.y)
            canvas.rotate(part.rotation, part.bitmap.width * part.scale / 2f, part.bitmap.height * part.scale / 2f)
            canvas.scale(part.scale, part.scale, part.bitmap.width / 2f, part.bitmap.height / 2f)
            canvas.drawBitmap(part.bitmap, -part.bitmap.width / 2f, -part.bitmap.height / 2f, null)
            canvas.restore()
        }
    }

    fun drawPlaceholders(canvas: Canvas, placeholders: List<Part>) {
        placeholders.forEach { part ->
            val x = part.x - (part.bitmap.width * part.scale / 2f)
            val y = part.y - (part.bitmap.height * part.scale / 2f)
            canvas.save()
            canvas.scale(part.scale, part.scale, part.x, part.y)
            canvas.drawBitmap(part.bitmap, x, y, null)
            canvas.restore()
        }
    }

    fun drawShip(
        canvas: Canvas,
        gameEngine: GameEngine,
        shipParts: List<Part>,
        screenWidth: Float,
        screenHeight: Float,
        shipX: Float,
        shipY: Float,
        gameState: GameState,
        mergedShipBitmap: Bitmap?,
        placeholders: List<Part>
    ) {
        shipRenderer.drawShip(
            canvas, gameEngine, shipParts, screenWidth, screenHeight,
            shipX, shipY, gameState, mergedShipBitmap, placeholders
        )
    }

    fun drawPowerUps(canvas: Canvas, powerUps: List<PowerUp>, statusBarHeight: Float) {
        gameObjectRenderer.drawPowerUps(canvas, powerUps, statusBarHeight)
    }

    fun drawAsteroids(canvas: Canvas, asteroids: List<Asteroid>, statusBarHeight: Float) {
        gameObjectRenderer.drawAsteroids(canvas, asteroids, statusBarHeight)
    }

    fun drawProjectiles(canvas: Canvas, projectiles: List<Projectile>, statusBarHeight: Float) {
        gameObjectRenderer.drawProjectiles(canvas, projectiles, statusBarHeight)
    }

    fun drawEnemyShips(canvas: Canvas, enemyShips: List<EnemyShip>, statusBarHeight: Float) {
        gameObjectRenderer.drawEnemyShips(canvas, enemyShips, statusBarHeight)
    }

    fun drawBoss(canvas: Canvas, boss: BossShip?, statusBarHeight: Float) {
        gameObjectRenderer.drawBoss(canvas, boss, statusBarHeight)
    }

    fun drawEnemyProjectiles(canvas: Canvas, enemyProjectiles: List<Projectile>, statusBarHeight: Float) {
        gameObjectRenderer.drawEnemyProjectiles(canvas, enemyProjectiles, statusBarHeight)
    }

    fun drawHomingProjectiles(canvas: Canvas, homingProjectiles: List<HomingProjectile>, statusBarHeight: Float) {
        gameObjectRenderer.drawHomingProjectiles(canvas, homingProjectiles, statusBarHeight)
    }

    fun drawStats(canvas: Canvas, gameEngine: GameEngine, statusBarHeight: Float, gameState: GameState) {
        uiRenderer.drawStats(canvas, gameEngine, statusBarHeight, gameState)
    }

    fun drawAIMessages(canvas: Canvas, aiAssistant: AIAssistant, statusBarHeight: Float) {
        uiRenderer.drawAIMessages(canvas, aiAssistant, statusBarHeight)
    }

    fun addScoreTextParticle(x: Float, y: Float, text: String) {
        shipRenderer.addScoreTextParticle(x, y, text)
    }

    fun clearParticles() {
        shipRenderer.clearParticles()
    }

    fun onDestroy() {
        bitmapManager.onDestroy()
        shipRenderer.onDestroy()
        Timber.d("Renderer onDestroy called")
    }
}