package com.example.spaceshipbuilderapp

import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot
import kotlin.random.Random
import javax.inject.Inject

class AIAssistant @Inject constructor() {
    private val messageQueue = CopyOnWriteArrayList<String>()
    private val displayedMessages = mutableListOf<Pair<String, Long>>()
    private val messageDisplayDuration = 3000L // 3 seconds
    private val maxDisplayedMessages = 3
    private var lastMissileCount = -1 // Track missile count changes
    private var lastBossState: BossShip? = null // Track boss appearance

    fun update(
        gameState: GameState,
        shipX: Float,
        shipY: Float,
        asteroids: List<Asteroid>,
        enemyShips: List<EnemyShip>,
        boss: BossShip?,
        missileCount: Int,
        maxMissiles: Int, // Added to replace shipManager.maxMissiles
        powerUpCollected: String?, // Null if no power-up collected this frame
        fuelHpGained: Boolean, // True if fuel/HP was gained this frame
        currentTime: Long
    ) {
        // Remove expired messages
        displayedMessages.removeAll { it.second + messageDisplayDuration < currentTime }

        if (displayedMessages.size >= maxDisplayedMessages) return

        // Only process in FLIGHT mode
        if (gameState != GameState.FLIGHT) return

        // Event-based commentary
        val threats = mutableListOf<String>()

        // Power-up collected
        powerUpCollected?.let { type ->
            val message = when (type) {
                "power_up" -> "Fuel and health boosted!"
                "shield" -> "Shield up, you're protected!"
                "speed" -> "Speed boost engaged!"
                "stealth" -> "Stealth mode activated!"
                "invincibility" -> "You're invincible now!"
                "warp" -> "Warp speed, nice move!"
                "star" -> "Star collected, great find!"
                else -> "Nice power-up grab!"
            }
            if (!messageQueue.contains(message)) {
                messageQueue.add(message)
                Timber.d("AI Assistant queued power-up: $message")
            }
        }

        // Fuel/HP gained (without power-up)
        if (fuelHpGained && powerUpCollected == null && Random.nextFloat() < 0.5f) { // 50% chance to avoid spam
            val message = "Fuel and health topped up!"
            if (!messageQueue.contains(message)) {
                messageQueue.add(message)
                Timber.d("AI Assistant queued fuel/HP: $message")
            }
        }

        // Boss incoming
        if (boss != null && lastBossState == null && Random.nextFloat() < 0.8f) { // 80% chance when boss appears
            val message = "Boss approaching, brace yourself!"
            if (!messageQueue.contains(message)) {
                messageQueue.add(message)
                Timber.d("AI Assistant queued boss: $message")
            }
        }
        lastBossState = boss

        // Enemy ship nearby
        enemyShips.forEach { enemy ->
            val distance = hypot(enemy.x - shipX, enemy.y - shipY)
            if (distance < 150f && Random.nextFloat() < 0.05f) { // 5% chance per frame
                threats.add("Enemy ship closing in!")
            }
        }

        // Asteroid nearby
        asteroids.forEach { asteroid ->
            val distance = hypot(asteroid.x - shipX, asteroid.y - shipY)
            if (distance < 100f && Random.nextFloat() < 0.05f) { // 5% chance per frame
                threats.add("Watch out for that asteroid!")
            }
        }

        // Missiles full
        if (missileCount > lastMissileCount && missileCount == maxMissiles && Random.nextFloat() < 0.3f) { // 30% chance when full
            val message = "Missiles fully stocked!"
            if (!messageQueue.contains(message)) {
                messageQueue.add(message)
                Timber.d("AI Assistant queued missiles full: $message")
            }
        }
        lastMissileCount = missileCount

        // Add a random threat message if any
        if (threats.isNotEmpty()) {
            val message = threats.random()
            if (!messageQueue.contains(message)) {
                messageQueue.add(message)
                Timber.d("AI Assistant queued threat: $message")
            }
        }

        // Occasional encouragement (non-event based)
        if (messageQueue.isEmpty() && Random.nextFloat() < 0.01f) { // 1% chance if no events
            val encouragement = listOf(
                "Keep it up, Captain!",
                "You're doing great!",
                "Nice maneuver!",
                "Stay sharp out there!"
            ).random()
            if (!messageQueue.contains(encouragement)) {
                messageQueue.add(encouragement)
                Timber.d("AI Assistant queued encouragement: $encouragement")
            }
        }

        // Display next message if available
        if (messageQueue.isNotEmpty() && displayedMessages.size < maxDisplayedMessages) {
            val message = messageQueue.removeAt(0)
            displayedMessages.add(Pair(message, currentTime))
            Timber.d("AI Assistant displaying: $message")
        }
    }

    fun getDisplayedMessages(): List<String> {
        return displayedMessages.map { it.first }
    }
}