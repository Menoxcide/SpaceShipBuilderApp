package com.example.spaceshipbuilderapp

import android.graphics.RectF
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

class CollisionManager @Inject constructor(
    private val renderer: Renderer,
    private val audioManager: AudioManager,
    private val gameObjectManager: GameObjectManager
) {
    fun checkCollisions(
        shipX: Float,
        shipY: Float,
        maxPartHalfWidth: Float,
        totalShipHeight: Float,
        stealthActive: Boolean,
        invincibilityActive: Boolean,
        powerUps: MutableList<PowerUp>,
        asteroids: MutableList<Asteroid>,
        projectiles: MutableList<Projectile>,
        enemyShips: MutableList<EnemyShip>,
        enemyProjectiles: MutableList<Projectile>,
        homingProjectiles: MutableList<HomingProjectile>,
        boss: BossShip?,
        onPowerUpCollected: (PowerUp) -> Unit,
        onAsteroidHit: (Asteroid) -> Unit,
        onEnemyProjectileHit: (Projectile) -> Unit,
        onEnemyShipHit: (EnemyShip) -> Unit
    ): Triple<Int, Long, Boolean> { // Changed return type to Triple to include boss collision state
        var scoreDelta = 0
        var glowStartTime = 0L
        var isCollidingWithBoss = false // Track boss collision state
        val currentTime = System.currentTimeMillis()

        val shipRect = RectF(
            shipX - maxPartHalfWidth,
            shipY - totalShipHeight / 2,
            shipX + maxPartHalfWidth,
            shipY + totalShipHeight / 2
        )

        // Check collision with boss
        if (boss != null) {
            val bossRect = RectF(boss.x - 75f, boss.y - 75f, boss.x + 75f, boss.y + 75f)
            if (shipRect.intersect(bossRect) && !stealthActive && !invincibilityActive) {
                isCollidingWithBoss = true
                renderer.shipRendererInstance.addCollisionParticles(shipX, shipY)
                // Damage text and HP reduction will be handled in FlightModeManager.kt per second
                audioManager.playCollisionSound()
                glowStartTime = currentTime
                Timber.d("Colliding with boss, continuous damage will be applied")
            }
        }

        // Check collisions with power-ups
        val powerUpsToRemove = mutableListOf<PowerUp>()
        for (powerUp in powerUps) {
            val powerUpRect = RectF(
                powerUp.x - 20f, powerUp.y - 20f,
                powerUp.x + 20f, powerUp.y + 20f
            )
            if (shipRect.intersect(powerUpRect)) {
                powerUpsToRemove.add(powerUp)
                renderer.shipRendererInstance.addPowerUpSpriteParticles(shipX, shipY, powerUp.type)
                Timber.d("Collected ${powerUp.type} power-up")
                onPowerUpCollected(powerUp)
                audioManager.playPowerUpSound()
            }
        }
        powerUps.removeAll(powerUpsToRemove)

        // Check collisions with asteroids
        val asteroidsToRemove = mutableListOf<Asteroid>()
        val asteroidsCopy = asteroids.toMutableList()
        for (asteroid in asteroidsCopy) {
            val asteroidRect = RectF(
                asteroid.x - asteroid.size, asteroid.y - asteroid.size,
                asteroid.x + asteroid.size, asteroid.y + asteroid.size
            )
            if (shipRect.intersect(asteroidRect) && !stealthActive && !invincibilityActive) {
                asteroidsToRemove.add(asteroid)
                renderer.shipRendererInstance.addCollisionParticles(shipX, shipY)
                renderer.shipRendererInstance.addDamageTextParticle(shipX, shipY, 10)
                audioManager.playCollisionSound()
                glowStartTime = currentTime
                Timber.d("Hit asteroid, HP decreased")
                onAsteroidHit(asteroid)
            }
            if (asteroid is GiantAsteroid && Random.nextFloat() < FlightModeManager.EXPLOSION_CHANCE) {
                asteroid.explode(gameObjectManager)
            }
        }
        asteroids.removeAll(asteroidsToRemove)

        // Check collisions with enemy projectiles
        val enemyProjectilesToRemove = mutableListOf<Projectile>()
        for (projectile in enemyProjectiles) {
            val projectileRect = RectF(
                projectile.x - FlightModeManager.PROJECTILE_SIZE,
                projectile.y - FlightModeManager.PROJECTILE_SIZE,
                projectile.x + FlightModeManager.PROJECTILE_SIZE,
                projectile.y + FlightModeManager.PROJECTILE_SIZE
            )
            if (shipRect.intersect(projectileRect) && !stealthActive && !invincibilityActive) {
                enemyProjectilesToRemove.add(projectile)
                renderer.shipRendererInstance.addCollisionParticles(shipX, shipY)
                renderer.shipRendererInstance.addDamageTextParticle(shipX, shipY, 10)
                audioManager.playCollisionSound()
                glowStartTime = currentTime
                Timber.d("Hit by enemy projectile, HP decreased")
                onEnemyProjectileHit(projectile)
            }
        }
        enemyProjectiles.removeAll(enemyProjectilesToRemove)

        // Check collisions with enemy ships
        val enemyShipsToRemove = mutableListOf<EnemyShip>()
        for (enemy in enemyShips) {
            val enemyRect = RectF(
                enemy.x - 50f,
                enemy.y - 50f,
                enemy.x + 50f,
                enemy.y + 50f
            )
            if (shipRect.intersect(enemyRect) && !stealthActive && !invincibilityActive) {
                enemyShipsToRemove.add(enemy)
                renderer.shipRendererInstance.addCollisionParticles(shipX, shipY)
                renderer.shipRendererInstance.addDamageTextParticle(shipX, shipY, enemy.damage.toInt())
                audioManager.playCollisionSound()
                glowStartTime = currentTime
                if (Random.nextFloat() < 0.25f) {
                    val powerUpType = if (Random.nextBoolean()) "star" else "power_up"
                    gameObjectManager.spawnPowerUp(enemy.x, enemy.y, powerUpType)
                    Timber.d("Enemy ship dropped $powerUpType at (x=${enemy.x}, y=${enemy.y})")
                }
                Timber.d("Collided with enemy ship, HP decreased by ${enemy.damage}")
                onEnemyShipHit(enemy)
            }
        }
        enemyShips.removeAll(enemyShipsToRemove)

        // Check collisions between projectiles and asteroids/enemy ships
        val projectilesToRemove = mutableListOf<Projectile>()
        val homingProjectilesToRemove = mutableListOf<HomingProjectile>()
        for (projectile in projectiles) {
            val projectileRect = RectF(
                projectile.x - FlightModeManager.PROJECTILE_SIZE,
                projectile.y - FlightModeManager.PROJECTILE_SIZE,
                projectile.x + FlightModeManager.PROJECTILE_SIZE,
                projectile.y + FlightModeManager.PROJECTILE_SIZE
            )
            for (asteroid in asteroidsCopy) {
                val asteroidRect = RectF(
                    asteroid.x - asteroid.size,
                    asteroid.y - asteroid.size,
                    asteroid.x + asteroid.size,
                    asteroid.y + asteroid.size
                )
                if (projectileRect.intersect(asteroidRect)) {
                    asteroidsToRemove.add(asteroid)
                    projectilesToRemove.add(projectile)
                    scoreDelta += FlightModeManager.ASTEROID_DESTROY_POINTS
                    renderer.shipRendererInstance.addExplosionParticles(asteroid.x, asteroid.y)
                    renderer.shipRendererInstance.addScoreTextParticle(asteroid.x, asteroid.y, "+${FlightModeManager.ASTEROID_DESTROY_POINTS}")
                    Timber.d("Projectile hit asteroid, score increased by ${FlightModeManager.ASTEROID_DESTROY_POINTS}")
                }
            }
            for (enemy in enemyShips) {
                val enemyRect = RectF(
                    enemy.x - 50f,
                    enemy.y - 50f,
                    enemy.x + 50f,
                    enemy.y + 50f
                )
                if (projectileRect.intersect(enemyRect)) {
                    enemy.health -= when (projectile) {
                        is PlasmaProjectile -> 15f // Plasma deals more damage
                        is MissileProjectile -> 20f // Missile deals even more
                        is LaserProjectile -> 12f // Laser deals moderate damage
                        else -> 10f // Default projectile damage
                    }
                    projectilesToRemove.add(projectile)
                    renderer.shipRendererInstance.addExplosionParticles(projectile.x, projectile.y)
                    Timber.d("Projectile hit enemy ship, health decreased to ${enemy.health}")
                    if (enemy.health <= 0) {
                        enemyShipsToRemove.add(enemy)
                        scoreDelta += 50
                        renderer.shipRendererInstance.addExplosionParticles(enemy.x, enemy.y)
                        renderer.shipRendererInstance.addScoreTextParticle(enemy.x, enemy.y, "+50")
                        if (Random.nextFloat() < 0.25f) {
                            val powerUpType = if (Random.nextBoolean()) "star" else "power_up"
                            gameObjectManager.spawnPowerUp(enemy.x, enemy.y, powerUpType)
                            Timber.d("Enemy ship dropped $powerUpType at (x=${enemy.x}, y=${enemy.y})")
                        }
                        Timber.d("Enemy ship destroyed, score increased by 50")
                    }
                }
            }
        }
        for (enemy in enemyShips) {
            val enemyRect = RectF(enemy.x - 50f, enemy.y - 50f, enemy.x + 50f, enemy.y + 50f)
            for (projectile in homingProjectiles) {
                if (projectile.target == enemy && projectile.checkCollision(enemyRect)) {
                    enemy.health -= 20f // Homing missile damage
                    homingProjectilesToRemove.add(projectile)
                    renderer.shipRendererInstance.addExplosionParticles(projectile.x, projectile.y)
                    Timber.d("Homing missile hit enemy ship, health decreased to ${enemy.health}")
                    if (enemy.health <= 0) {
                        enemyShipsToRemove.add(enemy)
                        scoreDelta += 50
                        renderer.shipRendererInstance.addExplosionParticles(enemy.x, enemy.y)
                        renderer.shipRendererInstance.addScoreTextParticle(enemy.x, enemy.y, "+50")
                        if (Random.nextFloat() < 0.25f) {
                            val powerUpType = if (Random.nextBoolean()) "star" else "power_up"
                            gameObjectManager.spawnPowerUp(enemy.x, enemy.y, powerUpType)
                            Timber.d("Enemy ship dropped $powerUpType at (x=${enemy.x}, y=${enemy.y})")
                        }
                        Timber.d("Enemy ship destroyed by homing missile, score increased by 50")
                    }
                }
            }
        }
        asteroids.removeAll(asteroidsToRemove)
        projectiles.removeAll(projectilesToRemove)
        enemyShips.removeAll(enemyShipsToRemove)
        homingProjectiles.removeAll(homingProjectilesToRemove)

        return Triple(scoreDelta, glowStartTime, isCollidingWithBoss)
    }

    fun checkCollision(shipX: Float, shipY: Float, maxPartHalfWidth: Float, totalShipHeight: Float, rect: RectF): Boolean {
        val shipRect = RectF(
            shipX - maxPartHalfWidth,
            shipY - totalShipHeight / 2,
            shipX + maxPartHalfWidth,
            shipY + totalShipHeight / 2
        )
        return shipRect.intersect(rect)
    }
}