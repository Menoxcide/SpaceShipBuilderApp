package com.example.spaceshipbuilderapp

import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot
import kotlin.random.Random
import javax.inject.Inject

class AIAssistant @Inject constructor() {
    private val messageQueue = CopyOnWriteArrayList<Message>()
    private val displayedMessages = mutableListOf<Triple<String, Long, Int>>() // Added priority to track display duration
    private val baseMessageDisplayDuration = 5000L // 5 seconds for critical/high messages
    private val extendedMessageDisplayDuration = baseMessageDisplayDuration * 3 // 15 seconds for normal/low messages
    private val maxDisplayedMessages = 3
    private val bottomOffset = 100f // Offset to avoid overlap with destroy all button

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
        // Remove expired messages based on their priority
        displayedMessages.removeAll { (text, startTime, priority) ->
            val duration = if (priority >= Message.HIGH) baseMessageDisplayDuration else extendedMessageDisplayDuration
            startTime + duration < currentTime
        }

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

        // Low-priority quips (random humor, slowed down further)
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
        // Slowed down to 45 seconds (already set as 45000L)
        if (messageQueue.isEmpty() && Random.nextFloat() < 0.002f && currentTime - playerMemory.lastQuipTime >= 45000L) {
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
        "Game over, Captain Catastrophe. Maybe stick to simulators next time?",
        "Boom! You’re a space firework now—pretty, but useless.",
        "That’s it, game over. Even the asteroids feel sorry for you.",
        "You’ve just redefined ‘crash landing.’ Well done, I guess?"
    ).random()

    private fun getSarcasticFuelQuip(): String = listOf<String>(
        "Fuel’s almost gone. Planning to glide to victory, genius?",
        "Running on fumes? Bold strategy, let’s see how it pans out!",
        "Fuel’s low. Guess you’re hoping for a miracle—or a tow truck!",
        "Out of fuel soon. What’s the plan, flapping your arms?",
        "Fuel tank’s crying for mercy. Maybe stop sightseeing?",
        "Fuel’s nearly out. Did you forget how spaceships work?",
        "Low fuel warning—guess who didn’t plan ahead?",
        "Fuel’s dwindling. Time to pray to the space gods!"
    ).random()

    private fun getSeriousFuelQuip(): String = listOf<String>(
        "Low on fuel, Captain. Better find some quick!",
        "Fuel’s dropping fast—keep an eye out!",
        "We’re nearly out of fuel—refuel ASAP!",
        "Fuel reserves critical—act now!",
        "Low fuel warning—don’t get stranded!",
        "Fuel’s running low—search for a power-up!",
        "Critical fuel levels—emergency refuel needed!",
        "Fuel gauge in the red—find a source fast!"
    ).random()

    private fun getSarcasticHpQuip(): String = listOf<String>(
        "HP critical. Maybe stop hugging asteroids?",
        "Hull’s a mess. Did you think this was bumper cars?",
        "HP’s in the red. Brilliant piloting there!",
        "Taking a beating, huh? Maybe dodge next time?",
        "Ship’s falling apart. Guess who’s to blame?",
        "HP’s toast. What’s your encore, Captain Chaos?",
        "Hull integrity? More like hull insecurity!",
        "Low HP? You’re a magnet for trouble!"
    ).random()

    private fun getSeriousHpQuip(): String = listOf<String>(
        "Hull’s taking a beating—watch out!",
        "HP’s low—avoid those hits!",
        "Ship damage critical—stay alert!",
        "We’re losing integrity—evade now!",
        "Hull’s weak—protect the ship!",
        "HP dropping fast—defensive maneuvers!",
        "Ship’s damaged—repair or retreat!",
        "Low health warning—brace for impact!"
    ).random()

    private fun getSarcasticAsteroidQuip(): String = listOf<String>(
        "Asteroid alert! Dodge it or become space dust!",
        "Rock incoming! You’re not planning to kiss it, right?",
        "Asteroid says hi. Don’t say hi back!",
        "Big rock, small brain—move aside!",
        "Asteroid’s got your name on it. Duck!",
        "Incoming rock! Time to not be a pancake!",
        "Asteroid ahead—don’t make it your new hood ornament!",
        "Rock alert! Swerve or become gravel!"
    ).random()

    private fun getSarcasticEnemyQuip(): String = listOf<String>(
        "Enemy ship in your face. Shoot or say hi?",
        "Hostile incoming! Don’t wave, just fire!",
        "Enemy’s too close. Flirting or fighting?",
        "Bad guy alert! Stop admiring and start blasting!",
        "Enemy ship’s here to party. Ruin their day!",
        "Hostile on your tail—don’t let them autograph your hull!",
        "Enemy closing in—time to not be their target practice!",
        "Foe detected! Don’t let them steal your lunch money!"
    ).random()

    private fun getSarcasticPowerUpQuip(): String = listOf<String>(
        "Fuel and HP? You’re almost competent now!",
        "Power-up grabbed. Don’t waste it, genius!",
        "Nice catch! Even a broken clock’s right twice a day!",
        "Boost acquired. Try not to squander it!",
        "Power-up? You’re welcome, clumsy!",
        "Got a power-up? Don’t trip over it!",
        "Fuel and health? You’re still a disaster waiting to happen!",
        "Power-up snagged. Miracles do exist!"
    ).random()

    private fun getSarcasticBossIntroQuip(): String = listOf<String>(
        "Oh look, a boss. Try not to die immediately!",
        "Boss time! Don’t embarrass me, okay?",
        "Big bad’s here. Good luck not screwing this up!",
        "Boss approaching. You’re toast, aren’t you?",
        "Meet the boss. Spoiler: it’s not impressed!",
        "Boss incoming! Don’t wet your space pants!",
        "Big guy’s here. Time to not be a snack!",
        "Boss alert! Let’s see how fast you fail!"
    ).random()

    private fun getSarcasticBossDefeatQuip(): String = listOf<String>(
        "Boss down? Shocking—you actually did it!",
        "Boss defeated. I underestimated your chaos!",
        "You beat the boss? Miracles do happen!",
        "Boss gone. Did you trip over the win button?",
        "Victory! I’ll pretend I believed in you all along!",
        "Boss kaput. Did you mean to do that?",
        "Big bad’s toast—guess you’re not totally hopeless!",
        "Boss out. I’m stunned you pulled it off!"
    ).random()

    private fun getSarcasticMissileQuip(): String = listOf<String>(
        "Missiles maxed out. Don’t waste them all at once!",
        "Full missile rack? Try aiming this time!",
        "Missiles stocked. Let’s not miss everything, hmm?",
        "Ready to launch? Don’t blow yourself up!",
        "Missiles full. You’re a walking armory now!",
        "Missile count maxed—don’t shoot your own foot!",
        "Armed to the teeth? Aim’s still optional, right?",
        "Missiles ready. Don’t waste them on nothing!"
    ).random()

    private fun getSarcasticDistanceQuip(): String = listOf<String>(
        "Another 5000 units? You’re basically a space tourist now!",
        "5000 more? Do you even know where you’re going?",
        "Long haul, huh? Wake me when something happens!",
        "5000 units down. Are we there yet?",
        "Distance milestone! You’re still alive—shocking!",
        "5000 units? You’re a galactic road tripper!",
        "Another milestone? Yawn, call me when it’s over!",
        "You’ve gone far. Still nowhere useful, huh?"
    ).random()

    private fun getRandomQuip(): String = if (playerMemory.humorLevel > 0.7f) listOf<String>(
        // Existing sarcastic quips
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
        "You’re a space ace… or just really lucky!",
        // New sarcastic quips
        "Wow, you’ve dodged everything. Did the universe take a nap?",
        "No damage yet? Are you bribing the asteroids?",
        "You’re still in one piece. I need to speak to my manager!",
        "Flying this well? I didn’t sign up for competence!",
        "No near misses? What’s next, a perfect landing?",
        "You’re cruising along like you own the galaxy—newsflash, you don’t!",
        "All systems green? Boring. Give me some drama!",
        "Still kicking? I underestimated your knack for chaos!",
        "You’re too good at this. Did you hack my sarcasm module?",
        "No wrecks yet? I’m running out of witty comebacks!",
        "Smooth flight, huh? Don’t get used to it, rookie!",
        "You’re alive? I need to recalibrate my expectations!",
        "No collisions? Are you sure you’re not in a simulation?",
        "Flying like a pro? I’ll believe it when pigs orbit!",
        "You’ve got this down? Nah, I’m still betting on a crash!",
        "Space is your playground? More like your scrapyard!",
        "No explosions? You’re killing my vibe here!",
        "Still going strong? I’ll give it five minutes!",
        "You’re a survivor? Or just too stubborn to crash!",
        "No dents yet? I’m starting to think you’re a hologram!",
        "Flying this well? Did you bribe the galaxy?",
        "You’re a legend? More like a fluke in the making!",
        "No chaos yet? I’m filing a complaint with the universe!",
        "You’re intact? Someone’s rewriting the script!",
        "Still soaring? I’ll get the popcorn for your inevitable fall!"
    ).random() else listOf<String>(
        // Existing serious quips
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
        "Captain, you’re a star yourself!",
        // New serious quips
        "Your skill is unmatched—keep pushing forward!",
        "The cosmos bends to your will, Captain!",
        "A steady hand at the helm—well done!",
        "You’re navigating like a seasoned explorer!",
        "The stars align with every move you make!",
        "Precision flying—textbook perfection!",
        "You’re a beacon in the darkness of space!",
        "Masterful control—keep leading the way!",
        "The galaxy salutes your prowess, Captain!",
        "Every maneuver’s a step toward greatness!",
        "You’re in sync with the universe—nice work!",
        "A true pilot’s spirit shines through you!",
        "The void trembles at your command!",
        "Your journey inspires the stars themselves!",
        "Flawless flight—keep charting the unknown!",
        "You’re the heartbeat of this mission!",
        "Space bows to your skill—carry on!",
        "A captain’s resolve guides us through!",
        "Your path lights up the galaxy—stay true!",
        "Every second proves your worth out here!",
        "The cosmos is your canvas—paint it bold!",
        "Steady flying—destiny’s in your hands!",
        "You’re the pilot every starship needs!",
        "The galaxy’s vast, but you’re its master!",
        "Your wings cut through the void—brilliant!"
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
            displayedMessages.add(Triple(nextMessage.text, currentTime, nextMessage.priority))
            Timber.d("AI Assistant displaying: ${nextMessage.text} (priority=${nextMessage.priority})")
        }
    }

    fun getDisplayedMessages(): List<String> {
        return displayedMessages.map { it.first }
    }

    fun getBottomOffset(): Float {
        return bottomOffset
    }
}