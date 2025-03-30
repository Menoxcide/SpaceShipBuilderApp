package com.example.spaceshipbuilderapp

import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot
import kotlin.random.Random
import javax.inject.Inject

class AIAssistant @Inject constructor() {
    private val messageQueue = CopyOnWriteArrayList<Message>()
    private val displayedMessages = mutableListOf<Pair<String, Long>>()
    private val messageDisplayDuration = 5000L // 5 seconds for reading time
    private val maxDisplayedMessages = 3

    // AI "Memory" and State
    private data class PlayerMemory(
        var powerUpsCollected: Int = 0,
        var nearMisses: Int = 0,
        var bossesDefeated: Int = 0,
        var missilesFired: Int = 0,
        var distanceMilestones: Int = 0, // Every 5000 units
        var humorLevel: Float = 0.5f,    // 0.0 (serious) to 1.0 (very sarcastic)
        var lastQuipTime: Long = 0L      // Track last low-priority quip
    )
    private val playerMemory = PlayerMemory()

    // Track previous states for change detection
    private var lastMissileCount = -1
    private var lastBossState: BossShip? = null
    private var lastFuel: Float = -1f
    private var lastHp: Float = -1f
    private var lastDistance: Float = 0f

    // Priority-based message system
    private data class Message(val text: String, val priority: Int, val category: String) {
        companion object {
            const val CRITICAL = 3 // Immediate threats, game over
            const val HIGH = 2     // Power-ups, boss events
            const val NORMAL = 1   // Gameplay updates
            const val LOW = 0      // Random quips (reduced frequency)
        }
    }

    fun update(
        gameState: GameState,
        shipX: Float,
        shipY: Float,
        asteroids: List<Asteroid>,
        enemyShips: List<EnemyShip>,
        boss: BossShip?,
        missileCount: Int,
        maxMissiles: Int,
        powerUpCollected: String?,
        fuelHpGained: Boolean,
        currentTime: Long,
        fuel: Float,
        hp: Float,
        distanceTraveled: Float
    ) {
        // Remove expired messages
        displayedMessages.removeAll { it.second + messageDisplayDuration < currentTime }

        if (displayedMessages.size >= maxDisplayedMessages) return

        // Process game state
        if (gameState != GameState.FLIGHT) {
            if (gameState == GameState.GAME_OVER && Random.nextFloat() < 0.3f) {
                queueMessage(getGameOverQuip(), Message.CRITICAL, "game_over")
            }
            return
        }

        // Update memory and state
        updateMemory(powerUpCollected, asteroids, enemyShips, boss, missileCount, distanceTraveled, shipX, shipY)

        // Critical events (immediate threats)
        checkCriticalEvents(shipX, shipY, asteroids, enemyShips, fuel, hp, currentTime)

        // High-priority events (power-ups, boss)
        checkHighPriorityEvents(powerUpCollected, fuelHpGained, boss, currentTime)

        // Normal commentary (missiles, progress)
        checkNormalCommentary(missileCount, maxMissiles, fuel, hp, distanceTraveled, currentTime)

        // Low-priority quips (random humor, slowed down)
        checkLowPriorityQuips(currentTime)

        // Display highest-priority message if available
        displayNextMessage(currentTime)
    }

    private fun updateMemory(
        powerUpCollected: String?,
        asteroids: List<Asteroid>,
        enemyShips: List<EnemyShip>,
        boss: BossShip?,
        missileCount: Int,
        distanceTraveled: Float,
        shipX: Float,
        shipY: Float
    ) {
        if (powerUpCollected != null) playerMemory.powerUpsCollected++
        if (missileCount < lastMissileCount) playerMemory.missilesFired++
        if (boss == null && lastBossState != null) playerMemory.bossesDefeated++
        if (distanceTraveled >= (playerMemory.distanceMilestones + 1) * 5000f) playerMemory.distanceMilestones++

        // Detect near misses
        val threatDistance = 100f
        asteroids.forEach { if (hypot(it.x - shipX, it.y - shipY) < threatDistance) playerMemory.nearMisses++ }
        enemyShips.forEach { if (hypot(it.x - shipX, it.y - shipY) < threatDistance) playerMemory.nearMisses++ }

        // Adjust humor based on performance
        playerMemory.humorLevel = (playerMemory.humorLevel + (playerMemory.nearMisses / 100f)).coerceIn(0f, 1f)
        lastMissileCount = missileCount
        lastBossState = boss
        lastDistance = distanceTraveled
    }

    private fun checkCriticalEvents(shipX: Float, shipY: Float, asteroids: List<Asteroid>, enemyShips: List<EnemyShip>, fuel: Float, hp: Float, currentTime: Long) {
        if (fuel < 20f && fuel < lastFuel && Random.nextFloat() < 0.25f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) getSarcasticFuelQuip() else getSeriousFuelQuip(),
                Message.CRITICAL, "fuel"
            )
        }
        if (hp < 20f && hp < lastHp && Random.nextFloat() < 0.25f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) getSarcasticHpQuip() else getSeriousHpQuip(),
                Message.CRITICAL, "hp"
            )
        }

        asteroids.forEach { asteroid ->
            val distance = hypot(asteroid.x - shipX, asteroid.y - shipY)
            if (distance < 50f && Random.nextFloat() < 0.1f) {
                queueMessage(
                    if (playerMemory.humorLevel > 0.7f) getSarcasticAsteroidQuip() else "Asteroid dead ahead—evasive action!",
                    Message.CRITICAL, "threat"
                )
            }
        }
        enemyShips.forEach { enemy ->
            val distance = hypot(enemy.x - shipX, enemy.y - shipY)
            if (distance < 50f && Random.nextFloat() < 0.1f) {
                queueMessage(
                    if (playerMemory.humorLevel > 0.7f) getSarcasticEnemyQuip() else "Enemy closing in—fire when ready!",
                    Message.CRITICAL, "threat"
                )
            }
        }

        lastFuel = fuel
        lastHp = hp
    }

    private fun checkHighPriorityEvents(powerUpCollected: String?, fuelHpGained: Boolean, boss: BossShip?, currentTime: Long) {
        powerUpCollected?.let { type ->
            val message = when (type) {
                "power_up" -> if (playerMemory.humorLevel > 0.7f) getSarcasticPowerUpQuip() else "Fuel and health restored—nice grab!"
                "shield" -> if (playerMemory.humorLevel > 0.7f) "Shield up. Now you’re slightly less doomed!" else "Shield activated—stay safe!"
                "speed" -> if (playerMemory.humorLevel > 0.7f) "Speed boost? Don’t crash into a star!" else "Speed engaged—go get ‘em!"
                "stealth" -> if (playerMemory.humorLevel > 0.7f) "Stealth mode? Sneaky, but I still see you!" else "Stealth on—ghost those foes!"
                "invincibility" -> if (playerMemory.humorLevel > 0.7f) "Invincible? Don’t get cocky, hero!" else "Invincibility—unstoppable now!"
                "warp" -> if (playerMemory.humorLevel > 0.7f) "Warp jump? Did you even aim?" else "Warp speed—brilliant move!"
                "star" -> if (playerMemory.humorLevel > 0.7f) "A star? Don’t spend it on space junk!" else "Star collected—stellar work!"
                else -> "Power-up grabbed—use it wisely!"
            }
            queueMessage(message, Message.HIGH, "powerup")
        }

        if (fuelHpGained && powerUpCollected == null && Random.nextFloat() < 0.3f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) "Free fuel and HP? Don’t expect this charity often!"
                else "Topped up—keep it going!",
                Message.HIGH, "resource"
            )
        }

        if (boss != null && lastBossState == null && Random.nextFloat() < 0.7f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) getSarcasticBossIntroQuip() else "Boss incoming—time to shine, Captain!",
                Message.HIGH, "boss"
            )
        } else if (boss == null && lastBossState != null && Random.nextFloat() < 0.7f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) getSarcasticBossDefeatQuip() else "Boss defeated—well done, ace!",
                Message.HIGH, "boss"
            )
        }
    }

    private fun checkNormalCommentary(missileCount: Int, maxMissiles: Int, fuel: Float, hp: Float, distanceTraveled: Float, currentTime: Long) {
        if (missileCount > lastMissileCount && missileCount == maxMissiles && Random.nextFloat() < 0.25f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) getSarcasticMissileQuip() else "Missiles ready—lock and load!",
                Message.NORMAL, "missiles"
            )
        }

        if (distanceTraveled >= (playerMemory.distanceMilestones + 1) * 5000f && Random.nextFloat() < 0.4f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) getSarcasticDistanceQuip() else "5000 units down—impressive trek!",
                Message.NORMAL, "distance"
            )
        }

        if (fuel > 50f && hp > 50f && Random.nextFloat() < 0.02f) {
            queueMessage(
                if (playerMemory.humorLevel > 0.7f) "Everything’s fine? Yawn. Where’s the chaos?"
                else "Smooth sailing—keep it steady!",
                Message.NORMAL, "status"
            )
        }
    }

    private fun checkLowPriorityQuips(currentTime: Long) {
        if (messageQueue.isEmpty() && Random.nextFloat() < 0.002f && currentTime - playerMemory.lastQuipTime >= 15000L) {
            queueMessage(getRandomQuip(), Message.LOW, "quip")
            playerMemory.lastQuipTime = currentTime
        }
    }

    // Quip Generators
    private fun getGameOverQuip(): String = listOf<String>(
        "Well, that was a spectacular crash. Shall we try again, or are you done embarrassing yourself?",
        "Game over. I’d say ‘nice try,’ but we both know that’d be a lie.",
        "You’ve turned into space confetti. Bravo!",
        "Crash and burn, huh? At least you’re consistent!",
        "Game over, Captain Catastrophe. Maybe stick to simulators next time?"
    ).random()

    private fun getSarcasticFuelQuip(): String = listOf<String>(
        "Fuel’s almost gone. Planning to glide to victory, genius?",
        "Running on fumes? Bold strategy, let’s see how it pans out!",
        "Fuel’s low. Guess you’re hoping for a miracle—or a tow truck!",
        "Out of fuel soon. What’s the plan, flapping your arms?",
        "Fuel tank’s crying for mercy. Maybe stop sightseeing?"
    ).random()

    private fun getSeriousFuelQuip(): String = listOf<String>(
        "Low on fuel, Captain. Better find some quick!",
        "Fuel’s dropping fast—keep an eye out!",
        "We’re nearly out of fuel—refuel ASAP!",
        "Fuel reserves critical—act now!",
        "Low fuel warning—don’t get stranded!"
    ).random()

    private fun getSarcasticHpQuip(): String = listOf<String>(
        "HP critical. Maybe stop hugging asteroids?",
        "Hull’s a mess. Did you think this was bumper cars?",
        "HP’s in the red. Brilliant piloting there!",
        "Taking a beating, huh? Maybe dodge next time?",
        "Ship’s falling apart. Guess who’s to blame?"
    ).random()

    private fun getSeriousHpQuip(): String = listOf<String>(
        "Hull’s taking a beating—watch out!",
        "HP’s low—avoid those hits!",
        "Ship damage critical—stay alert!",
        "We’re losing integrity—evade now!",
        "Hull’s weak—protect the ship!"
    ).random()

    private fun getSarcasticAsteroidQuip(): String = listOf<String>(
        "Asteroid alert! Dodge it or become space dust!",
        "Rock incoming! You’re not planning to kiss it, right?",
        "Asteroid says hi. Don’t say hi back!",
        "Big rock, small brain—move aside!",
        "Asteroid’s got your name on it. Duck!"
    ).random()

    private fun getSarcasticEnemyQuip(): String = listOf<String>(
        "Enemy ship in your face. Shoot or say hi?",
        "Hostile incoming! Don’t wave, just fire!",
        "Enemy’s too close. Flirting or fighting?",
        "Bad guy alert! Stop admiring and start blasting!",
        "Enemy ship’s here to party. Ruin their day!"
    ).random()

    private fun getSarcasticPowerUpQuip(): String = listOf<String>(
        "Fuel and HP? You’re almost competent now!",
        "Power-up grabbed. Don’t waste it, genius!",
        "Nice catch! Even a broken clock’s right twice a day!",
        "Boost acquired. Try not to squander it!",
        "Power-up? You’re welcome, clumsy!"
    ).random()

    private fun getSarcasticBossIntroQuip(): String = listOf<String>(
        "Oh look, a boss. Try not to die immediately!",
        "Boss time! Don’t embarrass me, okay?",
        "Big bad’s here. Good luck not screwing this up!",
        "Boss approaching. You’re toast, aren’t you?",
        "Meet the boss. Spoiler: it’s not impressed!"
    ).random()

    private fun getSarcasticBossDefeatQuip(): String = listOf<String>(
        "Boss down? Shocking—you actually did it!",
        "Boss defeated. I underestimated your chaos!",
        "You beat the boss? Miracles do happen!",
        "Boss gone. Did you trip over the win button?",
        "Victory! I’ll pretend I believed in you all along!"
    ).random()

    private fun getSarcasticMissileQuip(): String = listOf<String>(
        "Missiles maxed out. Don’t waste them all at once!",
        "Full missile rack? Try aiming this time!",
        "Missiles stocked. Let’s not miss everything, hmm?",
        "Ready to launch? Don’t blow yourself up!",
        "Missiles full. You’re a walking armory now!"
    ).random()

    private fun getSarcasticDistanceQuip(): String = listOf<String>(
        "Another 5000 units? You’re basically a space tourist now!",
        "5000 more? Do you even know where you’re going?",
        "Long haul, huh? Wake me when something happens!",
        "5000 units down. Are we there yet?",
        "Distance milestone! You’re still alive—shocking!"
    ).random()

    private fun getRandomQuip(): String = if (playerMemory.humorLevel > 0.7f) listOf<String>(
        "Still alive? I’m almost impressed.",
        "You’re flying like you’ve got somewhere to be—slow down, enjoy the void!",
        "Space is quiet. Too quiet. Suspiciously quiet.",
        "I’d give you a medal, but I left them in the other galaxy.",
        "No crashes yet? You’re ruining my betting pool!",
        "Flying straight? Did someone else take the controls?",
        "You’re a natural… at barely surviving!",
        "This is going too well. Where’s the disaster?",
        "Still here? I was sure you’d be space junk by now!",
        "Nice piloting. Did you borrow someone’s skills?",
        "You’re making this look easy. Stop it, it’s weird!",
        "No explosions yet? I’m disappointed in you!",
        "Keep this up and I might have to admit you’re decent!",
        "Flying smoothly? I must be malfunctioning!",
        "You’re a space ace… or just really lucky!"
    ).random() else listOf<String>(
        "Looking good out there, Captain!",
        "The stars are shining just for you today.",
        "Keep exploring—there’s more to see!",
        "You’ve got this under control, huh?",
        "Great flying—keep up the momentum!",
        "The galaxy’s yours to conquer!",
        "Steady as she goes, Captain!",
        "You’re carving a path through the stars!",
        "Nice work—space suits you!",
        "Every mile’s a victory out here!",
        "You’re a natural in the cockpit!",
        "The void’s no match for you!",
        "Smooth moves—keep it up!",
        "You’re writing your own legend!",
        "Captain, you’re a star yourself!"
    ).random()

    private fun queueMessage(text: String, priority: Int, category: String) {
        if (!messageQueue.any { it.text == text } && !displayedMessages.any { it.first == text }) {
            messageQueue.add(Message(text, priority, category))
            Timber.d("AI Assistant queued: $text (priority=$priority, category=$category)")
        }
    }

    private fun displayNextMessage(currentTime: Long) {
        if (messageQueue.isNotEmpty() && displayedMessages.size < maxDisplayedMessages) {
            val nextMessage = messageQueue.maxByOrNull { it.priority }!!
            messageQueue.remove(nextMessage)
            displayedMessages.add(Pair(nextMessage.text, currentTime))
            Timber.d("AI Assistant displaying: ${nextMessage.text} (priority=${nextMessage.priority})")
        }
    }

    fun getDisplayedMessages(): List<String> {
        return displayedMessages.map { it.first }
    }
}