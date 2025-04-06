package com.example.spaceshipbuilderapp

import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot
import kotlin.random.Random
import javax.inject.Inject

class AIAssistant @Inject constructor() {
    private val messageQueue = CopyOnWriteArrayList<Message>()
    private val displayedMessages =
        mutableListOf<Triple<String, Long, Int>>() // (text, startTime, priority)
    private val baseMessageDisplayDuration = 5000L // 5 seconds for critical/high messages
    private val extendedMessageDisplayDuration =
        baseMessageDisplayDuration * 3 // 15 seconds for normal/low messages
    private val maxDisplayedMessages = 3
    private val bottomOffset = 100f // Offset to avoid overlap with destroy all button
    private val messages = mutableListOf<String>()
    private val maxMessages = 3 // Limit to 3 messages for display
    // AI "Memory" and State
    private data class PlayerMemory(
        var powerUpsCollected: Int = 0,
        var nearMisses: Int = 0,
        var bossesDefeated: Int = 0,
        var missilesFired: Int = 0,
        var distanceMilestones: Int = 0, // Every 5000 units
        var lastQuipTime: Long = 0L,     // Track last low-priority quip
        var lastCriticalQuipTime: Long = 0L, // Track last critical quip
        var lastHighQuipTime: Long = 0L,     // Track last high-priority quip
        var nearMissStreak: Int = 0,         // Track consecutive near misses
        var powerUpStreak: Int = 0,          // Track consecutive power-up collections
        var recentPowerUps: MutableList<String> = mutableListOf(), // Track recent power-up types
        var recentThreats: Int = 0,          // Track recent threats encountered
        var performanceScore: Float = 0f,    // Track player performance (0-100)
        var sessionStartTime: Long = System.currentTimeMillis(), // Track session duration
        var lastQuipCategory: String? = null, // Track last quip category for chaining
        var preferredPlayStyle: PlayStyle = PlayStyle.BALANCED, // Inferred play style
        var gameIntensity: Float = 0f,       // Track game intensity (0-1)
        var currentEnvironment: FlightModeManager.Environment? = null // Track current environment
    )

    private val playerMemory = PlayerMemory()

    // Track previous states for change detection
    private var lastMissileCount = -1
    private var lastBossState: BossShip? = null
    private var lastFuel: Float = -1f
    private var lastHp: Float = -1f
    private var lastDistance: Float = 0f

    // Quip history to avoid repetition
    private val quipHistory = mutableListOf<String>()
    private val maxQuipHistory = 5 // Avoid repeating the last 5 quips

    // Play style enum
    enum class PlayStyle {
        AGGRESSIVE, CAUTIOUS, COLLECTOR, BALANCED
    }

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
        distanceTraveled: Float,
        currentEnvironment: FlightModeManager.Environment? = null // Add environment awareness
    ) {
        // Remove expired messages based on their priority
        displayedMessages.removeAll { (text, startTime, priority) ->
            val duration =
                if (priority >= Message.HIGH) baseMessageDisplayDuration else extendedMessageDisplayDuration
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
        updateMemory(
            powerUpCollected,
            asteroids,
            enemyShips,
            boss,
            missileCount,
            distanceTraveled,
            shipX,
            shipY,
            currentEnvironment
        )

        // Calculate game intensity (0-1)
        playerMemory.gameIntensity = calculateGameIntensity(asteroids, enemyShips, boss, fuel, hp)

        // Dynamic cooldown based on game intensity
        val criticalCooldown =
            (3000L * (1 - playerMemory.gameIntensity)).toLong().coerceAtLeast(1500L)
        val highCooldown = (4000L * (1 - playerMemory.gameIntensity)).toLong().coerceAtLeast(2000L)

        if (currentTime - playerMemory.lastCriticalQuipTime < criticalCooldown && Random.nextFloat() > 0.2f) return
        if (currentTime - playerMemory.lastHighQuipTime < highCooldown && Random.nextFloat() > 0.3f) return

        // Critical events (immediate threats)
        checkCriticalEvents(shipX, shipY, asteroids, enemyShips, fuel, hp, currentTime)

        // High-priority events (power-ups, boss)
        checkHighPriorityEvents(powerUpCollected, fuelHpGained, boss, currentTime)

        // Normal commentary (missiles, progress)
        checkNormalCommentary(missileCount, maxMissiles, fuel, hp, distanceTraveled, currentTime)

        // Low-priority quips (random commentary, slowed down to 60 seconds)
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
        shipY: Float,
        currentEnvironment: FlightModeManager.Environment?
    ) {
        if (powerUpCollected != null) {
            playerMemory.powerUpsCollected++
            playerMemory.powerUpStreak++
            playerMemory.recentPowerUps.add(powerUpCollected)
            if (playerMemory.recentPowerUps.size > 3) playerMemory.recentPowerUps.removeAt(0)
            playerMemory.performanceScore += 5f // Boost performance for collecting power-ups
        } else {
            playerMemory.powerUpStreak = 0
        }

        if (missileCount < lastMissileCount) {
            playerMemory.missilesFired++
            playerMemory.performanceScore += 2f // Boost for aggressive play
        }
        if (boss == null && lastBossState != null) {
            playerMemory.bossesDefeated++
            playerMemory.performanceScore += 20f // Big boost for defeating a boss
        }
        if (distanceTraveled >= (playerMemory.distanceMilestones + 1) * 5000f) {
            playerMemory.distanceMilestones++
            playerMemory.performanceScore += 10f // Boost for distance
        }

        // Detect near misses
        val threatDistance = 100f
        val currentNearMisses =
            (asteroids.count { hypot(it.x - shipX, it.y - shipY) < threatDistance } +
                    enemyShips.count { hypot(it.x - shipX, it.y - shipY) < threatDistance })
        if (currentNearMisses > 0) {
            playerMemory.nearMisses += currentNearMisses
            playerMemory.nearMissStreak++
            playerMemory.recentThreats += currentNearMisses
            playerMemory.performanceScore -= 2f // Slight penalty for near misses
        } else {
            playerMemory.nearMissStreak = 0
        }
        if (playerMemory.recentThreats > 5) playerMemory.recentThreats =
            5 // Cap for context weighting

        // Update current environment
        playerMemory.currentEnvironment = currentEnvironment

        // Update performance score (0-100)
        playerMemory.performanceScore = playerMemory.performanceScore.coerceIn(0f, 100f)

        // Infer play style
        updatePlayStyle()

        lastMissileCount = missileCount
        lastBossState = boss
        lastDistance = distanceTraveled
    }

    private fun calculateGameIntensity(
        asteroids: List<Asteroid>,
        enemyShips: List<EnemyShip>,
        boss: BossShip?,
        fuel: Float,
        hp: Float
    ): Float {
        var intensity = 0f
        intensity += asteroids.size * 0.05f
        intensity += enemyShips.size * 0.1f
        if (boss != null) intensity += 0.5f
        if (fuel < 20f) intensity += 0.3f
        if (hp < 20f) intensity += 0.3f
        return intensity.coerceIn(0f, 1f)
    }

    private fun updatePlayStyle() {
        val aggressiveScore = playerMemory.missilesFired * 2f + playerMemory.bossesDefeated * 10f
        val cautiousScore = playerMemory.nearMisses * 1f
        val collectorScore = playerMemory.powerUpsCollected * 3f

        playerMemory.preferredPlayStyle = when {
            aggressiveScore > cautiousScore && aggressiveScore > collectorScore -> PlayStyle.AGGRESSIVE
            cautiousScore > aggressiveScore && cautiousScore > collectorScore -> PlayStyle.CAUTIOUS
            collectorScore > aggressiveScore && collectorScore > cautiousScore -> PlayStyle.COLLECTOR
            else -> PlayStyle.BALANCED
        }
    }

    private fun checkCriticalEvents(
        shipX: Float,
        shipY: Float,
        asteroids: List<Asteroid>,
        enemyShips: List<EnemyShip>,
        fuel: Float,
        hp: Float,
        currentTime: Long
    ) {
        // Dynamic priority adjustment: if both fuel and HP are low, increase priority
        val fuelCritical = fuel < 20f && fuel < lastFuel
        val hpCritical = hp < 20f && hp < lastHp
        val priority = if (fuelCritical && hpCritical) Message.CRITICAL + 1 else Message.CRITICAL

        if (fuelCritical && Random.nextFloat() < 0.25f) {
            queueMessage(getFuelQuip(), priority, "fuel")
            playerMemory.lastCriticalQuipTime = currentTime
        }
        if (hpCritical && Random.nextFloat() < 0.25f) {
            queueMessage(getHpQuip(), priority, "hp")
            playerMemory.lastCriticalQuipTime = currentTime
        }

        asteroids.forEach { asteroid ->
            val distance = hypot(asteroid.x - shipX, asteroid.y - shipY)
            if (distance < 50f && Random.nextFloat() < 0.1f) {
                queueMessage(getAsteroidQuip("asteroid"), Message.CRITICAL, "threat")
                playerMemory.lastCriticalQuipTime = currentTime
            }
        }
        enemyShips.forEach { enemy ->
            val distance = hypot(enemy.x - shipX, enemy.y - shipY)
            if (distance < 50f && Random.nextFloat() < 0.1f) {
                queueMessage(getEnemyQuip("enemy"), Message.CRITICAL, "threat")
                playerMemory.lastCriticalQuipTime = currentTime
            }
        }

        // Streak-based commentary for near misses
        if (playerMemory.nearMissStreak >= 3 && Random.nextFloat() < 0.3f) {
            queueMessage(getNearMissStreakQuip(), Message.NORMAL, "streak")
            playerMemory.lastCriticalQuipTime = currentTime
        }

        lastFuel = fuel
        lastHp = hp
    }

    private fun checkHighPriorityEvents(
        powerUpCollected: String?,
        fuelHpGained: Boolean,
        boss: BossShip?,
        currentTime: Long
    ) {
        powerUpCollected?.let { type ->
            val message = when (type) {
                "power_up" -> getPowerUpQuip()
                "shield" -> "Shield up! You’re untouchable now—yayaya!"
                "speed" -> "Speed boost engaged! You’re flyin’ with pure momentum!"
                "stealth" -> "Stealth mode on! You’re a ghost in the void!"
                "invincibility" -> "Invincibility engaged! You’re a god out there!"
                "warp" -> "Warp speed activated! You’re teleportin’ like a pro!"
                "star" -> "Star snatched! You’re collectin’ trophies like a pro, kid!"
                else -> "Power-up grabbed! You’re steppin’ up out there!"
            }
            queueMessage(message, Message.HIGH, "powerup")
            playerMemory.lastHighQuipTime = currentTime
        }

        if (fuelHpGained && powerUpCollected == null && Random.nextFloat() < 0.3f) {
            queueMessage(
                "Fuel and HP boosted! You’re back in the fight—yayaya!",
                Message.HIGH,
                "resource"
            )
            playerMemory.lastHighQuipTime = currentTime
        }

        if (boss != null && lastBossState == null && Random.nextFloat() < 0.7f) {
            queueMessage(getBossIntroQuip(), Message.HIGH, "boss")
            playerMemory.lastHighQuipTime = currentTime
        } else if (boss == null && lastBossState != null && Random.nextFloat() < 0.7f) {
            queueMessage(getBossDefeatQuip(), Message.HIGH, "boss")
            playerMemory.lastHighQuipTime = currentTime
        }

        // Streak-based commentary for power-up collections
        if (playerMemory.powerUpStreak >= 2 && Random.nextFloat() < 0.4f) {
            queueMessage(getPowerUpStreakQuip(), Message.NORMAL, "streak")
            playerMemory.lastHighQuipTime = currentTime
        }
    }

    private fun checkNormalCommentary(
        missileCount: Int,
        maxMissiles: Int,
        fuel: Float,
        hp: Float,
        distanceTraveled: Float,
        currentTime: Long
    ) {
        if (missileCount > lastMissileCount && missileCount == maxMissiles && Random.nextFloat() < 0.25f) {
            queueMessage(getMissileQuip(), Message.NORMAL, "missiles")
        }

        if (distanceTraveled >= (playerMemory.distanceMilestones + 1) * 5000f && Random.nextFloat() < 0.4f) {
            queueMessage(getDistanceQuip(), Message.NORMAL, "distance")
        }

        if (fuel > 50f && hp > 50f && Random.nextFloat() < 0.02f) {
            queueMessage(
                "You’re in complete control out there! Keep dominating!",
                Message.NORMAL,
                "status"
            )
        }
    }

    private fun checkLowPriorityQuips(currentTime: Long) {
        // Slowed down to 60 seconds (60000L) and reduced probability
        if (messageQueue.isEmpty() && Random.nextFloat() < 0.001f && currentTime - playerMemory.lastQuipTime >= 60000L) {
            queueMessage(getRandomQuip(), Message.LOW, "quip")
            playerMemory.lastQuipTime = currentTime
        }
    }

    // Quip Generators in Dr. Disrespect Style, Focused on the Player
    private fun getGameOverQuip(): String {
        val sessionDuration = System.currentTimeMillis() - playerMemory.sessionStartTime
        val tone = if (playerMemory.performanceScore < 30f) "encouraging" else "mocking"
        val quips = when (tone) {
            "encouraging" -> listOf(
                "Game over! You crashed hard—don’t sweat it, you’ll bounce back!",
                "You’re done! That explosion was rough—get back in the fight!",
                "Boom! You’re space dust now—shake it off and try again!",
                "Game over! The intensity got you—rise up and do better!",
                "You got smoked! That’s a wrap—yayaya, you’ll get ‘em next time!",
                "Lights out! You burned out—don’t let it break you!",
                "Game over! Outta gas, outta glory—keep pushin’ forward!",
                "You’re finished! That crash was harsh—get up and fight!",
                "You blew it! That explosion’s on you—learn from it!",
                "Game over! You tanked it—don’t let it stop you!",
                "You’re toast! That run was rough—try harder next time!",
                "Boom! You’re done! That fight was tough—keep going!",
                "Game over! You choked under pressure—rise above it!",
                "You’re out! That crash was brutal—don’t give up now!",
                "Lights out! You failed big time—get back in the game!",
                "Game over! You’re a wreck—yayaya, you’ll do better!",
                "You’re done! That exit was rough—don’t let it define you, rookie!",
                "Boom! You’re space junk now—get up and fight again!",
                "Game over! You’re a galactic failure—prove ‘em wrong!",
                "You’re finished! That attempt was rough—keep at it!",
                "Lights out! You crumbled in the void—rise up stronger!",
                "Game over! You’re a cosmic catastrophe—don’t stop now!",
                "You got smoked! That crash was rough—yayaya, you’ll recover, kid!",
                "You’re out! You crashed harder than a meteor—get back out there!"
            )

            else -> listOf(
                "Game over! You crashed hard—pathetic performance!",
                "You’re done! That was a pitiful explosion!",
                "Boom! You’re space dust now—total failure out there!",
                "Game over! You couldn’t handle the intensity, could ya?",
                "You got smoked! That’s a wrap—yayaya!",
                "Lights out! You burned out in a blaze of shame!",
                "Game over! Outta gas, outta glory—what a mess!",
                "You’re finished! That crash was a disgrace!",
                "You blew it! That explosion’s all on you!",
                "Game over! You tanked it—total meltdown!",
                "You’re toast! That was a pitiful run!",
                "Boom! You’re done! You call that a fight?",
                "Game over! You choked under pressure!",
                "You’re out! That crash was a total embarrassment!",
                "Lights out! You failed big time out there!",
                "Game over! You’re a wreck—yayaya, what a mess!",
                "You’re done! That was a shameful exit, rookie!",
                "Boom! You’re space junk now—complete disaster!",
                "Game over! You’re a galactic failure!",
                "You’re finished! That was a laughable attempt!",
                "Lights out! You crumbled in the void!",
                "Game over! You’re a cosmic catastrophe!",
                "You got smoked! That was a pitiful crash, kid!",
                "You’re out! You crashed harder than a meteor!"
            )
        }
        return selectQuip(quips, "game_over")
    }

    private fun getFuelQuip(): String {
        val tone = if (playerMemory.performanceScore < 30f) "encouraging" else "urgent"
        val quips = when (tone) {
            "encouraging" -> listOf(
                "You’re runnin’ on empty! Find some fuel—don’t give up!",
                "Fuel’s low! You can still make it—grab some juice!",
                "You’re sputterin’! Keep going, you’ll find fuel soon!",
                "Fuel’s critical! Hang in there—don’t let it stop you!",
                "You’re outta fuel soon! Keep pushin’—yayaya, you got this!",
                "Fuel’s in the red! You’re close—find some gas now!",
                "You’re burnin’ fumes! Don’t lose hope—get some fuel!",
                "Low on fuel? You’re tougher than this—hustle for gas!",
                "You’re dryin’ up! Keep fightin’—fuel’s out there!",
                "Fuel’s droppin’ fast! You can do this—act quick!",
                "You’re on empty! Don’t let it end—find fuel now!",
                "Fuel gauge is screamin’! You’re not done—move fast!",
                "You’re outta gas soon! Keep your eyes peeled for fuel!",
                "Fuel’s critical! You’re stronger than this—find some juice!",
                "You’re runnin’ on vapors! Don’t give up—get fuel now!",
                "Low fuel? You’ve got the grit—don’t stall out on me!",
                "You’re burnin’ out! Keep pushin’—fuel’s around the corner!",
                "Fuel’s in the red! You’re not out yet—push your luck!",
                "You’re dry as a bone! Don’t stop now—get some gas!",
                "Fuel’s droppin’! You’re close—find some fast!",
                "You’re on your last drops! Don’t let it end—keep going!",
                "Fuel’s critical! You’re a fighter—don’t crash now!",
                "You’re runnin’ dry! Keep fightin’, rookie—you’ll make it!",
                "Low on fuel? You better hustle—yayaya, you got this, kid!"
            )

            else -> listOf(
                "You’re runnin’ on empty! Find some fuel—move it!",
                "Fuel’s low! You gonna stall out like that?",
                "You’re sputterin’! Get some juice—now!",
                "Fuel’s critical! Don’t choke out there!",
                "You’re outta fuel soon! Don’t get stranded—yayaya!",
                "Fuel’s in the red! You better find some fast!",
                "You’re burnin’ fumes! Step up and get some gas!",
                "Low on fuel? You better hustle or you’re done!",
                "You’re dryin’ up! Find fuel before you’re toast!",
                "Fuel’s droppin’ fast! You better act quick!",
                "You’re on empty! Get fuel or you’re grounded!",
                "Fuel gauge is screamin’! Move your ass—now!",
                "You’re outta gas soon! Don’t let your run end!",
                "Fuel’s critical! You better find some juice!",
                "You’re runnin’ on vapors! Get fuel—pronto!",
                "Low fuel? You better not stall out on me!",
                "You’re burnin’ out! Find some fuel—stat!",
                "Fuel’s in the red! You’re pushin’ your luck!",
                "You’re dry as a bone! Get some gas—move it!",
                "Fuel’s droppin’! You better find some fast!",
                "You’re on your last drops! Don’t screw this up!",
                "Fuel’s critical! You’re flirtin’ with disaster!",
                "You’re runnin’ dry! Get fuel or you’re done, rookie!",
                "Low on fuel? You better hustle—yayaya, kid!"
            )
        }
        return selectQuip(quips, "fuel")
    }

    private fun getHpQuip(): String {
        val tone = if (playerMemory.performanceScore < 30f) "encouraging" else "urgent"
        val quips = when (tone) {
            "encouraging" -> listOf(
                "You’re takin’ too many hits! Hang in there—get in the game!",
                "Hull’s gettin’ smashed! You can pull through—keep fighting!",
                "You’re fallin’ apart! Don’t give up—toughen up out there!",
                "HP’s in the red! You’re stronger than this—don’t break down!",
                "You’re gettin’ pummeled! Keep pushin’—yayaya, you got this!",
                "Ship’s on its last legs! Don’t let it end—fight harder!",
                "You’re droppin’ HP fast! You can do this—stay in the fight!",
                "You’re a mess out there! Don’t give up—move it!",
                "You’re gettin’ hammered! You’re tougher than this—pull it together!",
                "Hull’s takin’ a beating! Keep going—you can shape up!",
                "You’re breakin’ down! Don’t let it stop you—get it together!",
                "HP’s critical! You’re not done—don’t let it end like this!",
                "You’re gettin’ wrecked! Keep fightin’—yayaya, you’ll make it!",
                "Ship’s crumblin’! You can turn it around—don’t give up!",
                "You’re losin’ HP fast! Don’t go down easy—keep pushin’!",
                "You’re a wreck! You’re not out yet—stay in the fight!",
                "You’re takin’ a thrashin’! Don’t let it break you—get it together!",
                "Hull’s screamin’! You’re a fighter—fight harder!",
                "You’re on your last breath! Don’t give up—you can do this!",
                "HP’s in the danger zone! You’re tougher than this—pull through!",
                "You’re gettin’ smashed! Don’t let it end here—keep going!",
                "You’re fallin’ to pieces! You’ll make it—stay alive—yayaya, rookie!",
                "Ship’s breakin’ apart! You’re not done—survive this!",
                "You’re a sittin’ duck! Fight back or you’re done, kid!"
            )

            else -> listOf(
                "You’re takin’ too many hits! Get in the game!",
                "Hull’s gettin’ smashed! You call that piloting?",
                "You’re fallin’ apart! Toughen up out there!",
                "HP’s in the red! Don’t break down on me!",
                "You’re gettin’ pummeled! Step up your game—yayaya!",
                "Ship’s on its last legs! Don’t crash and burn!",
                "You’re droppin’ HP fast! Fight harder, now!",
                "You’re a mess out there! Stay alive—move it!",
                "You’re gettin’ hammered! Pull it together!",
                "Hull’s takin’ a beating! You better shape up!",
                "You’re breakin’ down! Get your act together!",
                "HP’s critical! Don’t let it end like this!",
                "You’re gettin’ wrecked! Fight back—yayaya!",
                "Ship’s crumblin’! You better turn it around!",
                "You’re losin’ HP fast! Don’t go down easy!",
                "You’re a wreck! Stay in the fight—move it!",
                "You’re takin’ a thrashin’! Get it together!",
                "Hull’s screamin’! You better fight harder!",
                "You’re on your last breath! Don’t give up!",
                "HP’s in the danger zone! Pull through—now!",
                "You’re gettin’ smashed! Don’t let it end here!",
                "You’re fallin’ to pieces! Stay alive—yayaya, rookie!",
                "Ship’s breakin’ apart! You better survive!",
                "You’re a sittin’ duck! Fight back or you’re done, kid!"
            )
        }
        return selectQuip(quips, "hp")
    }

    private fun getAsteroidQuip(threatType: String): String {
        val quips = listOf(
            "That $threatType’s dead ahead! Dodge it or you’re done!",
            "Incoming $threatType! Swerve—Violence. Speed. Momentum!",
            "That $threatType’s in your face! Move or get smashed!",
            "Big $threatType alert! You better evade—now!",
            "That $threatType’s closin’ in! Don’t let it end you!",
            "That $threatType’s got your number! You better dodge fast!",
            "Incoming $threatType! Don’t ruin your run—yayaya!",
            "That $threatType’s in your grill! You better not mess this up!",
            "That $threatType’s headin’ your way! Swerve or get crushed!",
            "That $threatType alert! You better move—don’t choke!",
            "Big $threatType in your path! Dodge it—move it!",
            "That $threatType’s comin’ in hot! You better evade!",
            "That $threatType’s closin’ fast! Don’t let it smash you!",
            "That $threatType’s on you! Swerve—yayaya!",
            "Big $threatType’s in your face! You better dodge quick!",
            "That $threatType’s got you in its sights! Move—now!",
            "That $threatType’s barrelin’ down! Don’t get flattened!",
            "That $threatType’s comin’ for you! Evade or you’re toast!",
            "Big $threatType alert! You better not get hit!",
            "That $threatType’s in your way! Dodge it—stat!",
            "That $threatType’s closin’ in! You better swerve fast, rookie!",
            "That $threatType’s on your tail! Don’t let it crush you!",
            "Big $threatType’s comin’ hot! Move or you’re done!",
            "That $threatType’s in your grill! Evade—yayaya, kid!"
        )
        return selectQuip(quips, "threat")
    }

    private fun getEnemyQuip(threatType: String): String {
        val quips = listOf(
            "That $threatType ship’s on you! Blast ‘em—show ‘em who’s boss!",
            "Hostile $threatType’s too close! Fire—don’t let ‘em disrespect you!",
            "That $threatType’s in your face! Light ‘em up, now!",
            "Bad $threatType’s closin’ in! Strike hard—yayaya!",
            "That $threatType ship’s here! Show ‘em your violence!",
            "Hostile $threatType on your six! You better fight back hard!",
            "That $threatType’s all up in your grill! Make ‘em regret it!",
            "Foe $threatType’s testin’ you! Prove you’re the top dog—now!",
            "That $threatType’s comin’ for you! Blast ‘em to pieces!",
            "Hostile $threatType’s in your sights! Fire—don’t hold back!",
            "That $threatType’s got you in their crosshairs! Strike first!",
            "Bad $threatType’s on your tail! Show ‘em what you’re made of!",
            "That $threatType ship’s closin’ in! You better bring the heat!",
            "Hostile $threatType’s on your ass! Fight back—yayaya!",
            "That $threatType’s in your face! You better take ‘em down!",
            "Foe $threatType’s comin’ at you! Blast ‘em outta the void!",
            "That $threatType’s testin’ your mettle! Show ‘em your fury!",
            "Hostile $threatType’s got you pinned! Strike hard—now!",
            "That $threatType ship’s on you! You better not choke!",
            "Bad $threatType’s in your grill! Light ‘em up—stat!",
            "That $threatType’s comin’ in hot! You better fight back, kid!",
            "Hostile $threatType’s on your six! Don’t let ‘em take you down!",
            "That $threatType’s got guts comin’ at you! Show ‘em yours, rookie!",
            "Foe $threatType’s in your sights! Blast ‘em—yayaya!"
        )
        return selectQuip(quips, "threat")
    }

    private fun getPowerUpQuip(): String {
        val quips = listOf(
            "You grabbed fuel and HP! You’re back at peak power!",
            "Power-up secured! You’re unstoppable out there!",
            "You got the juice! Your engine’s roarin’ now!",
            "Fuel and HP in the bag! You’re crushin’ it!",
            "You snagged that power-up! Keep the heat on—yayaya!",
            "Power-up grabbed! You’re in the fight to win!",
            "You boosted up! Keep dominating the void!",
            "Fuel and HP restored! You’re a winner out there!",
            "You nabbed that power-up! You’re a galactic beast!",
            "Power-up in your hands! You’re a force to reckon with!",
            "You got the goods! Keep slayin’ out there!",
            "Fuel and HP boosted! You’re back in the game!",
            "You grabbed the juice! You’re a cosmic warrior—yayaya!",
            "Power-up secured! You’re tearin’ it up now!",
            "You got that boost! Keep the momentum rollin’!",
            "Fuel and HP in the bag! You’re a galactic pro, kid!",
            "You snagged that power-up! You’re on fire out there!",
            "Power-up grabbed! You’re a void-dominatin’ beast!",
            "You boosted your stats! Keep crushin’ the stars!",
            "Fuel and HP restored! You’re a cosmic juggernaut!",
            "You got the juice! You’re a galactic terror—yayaya, rookie!",
            "Power-up in your grip! You’re a force of nature!",
            "You nabbed that boost! Keep the violence comin’!",
            "Fuel and HP secured! You’re a star in the void!"
        )
        return selectQuip(quips, "powerup")
    }

    private fun getPowerUpStreakQuip(): String {
        val quips = listOf(
            "You’re on a power-up streak! You’re a collectin’ machine!",
            "Power-up streak! You’re rackin’ ‘em up like a pro!",
            "You’re grabbin’ power-ups left and right! Keep it up!",
            "Streakin’ with power-ups! You’re a galactic hoarder!",
            "You’re on a roll with those power-ups! Don’t stop now!",
            "Power-up streak! You’re a cosmic scavenger—yayaya!",
            "You’re stackin’ power-ups! You’re a void predator!",
            "Streakin’ with the goods! You’re a galactic pro, kid!",
            "You’re hoardin’ power-ups! Keep that momentum!",
            "Power-up streak! You’re a force out there—yayaya!",
            "You’re grabbin’ everything! You’re a cosmic beast!",
            "Streakin’ through danger! You’re a cosmic survivor!",
            "You’re on a dodgin’ roll! Keep weavin’ through!",
            "Near-miss streak! You’re a galactic escape artist!",
            "You’re slippin’ past everything! That streak’s hot!",
            "Streakin’ with near misses! You’re a void predator!",
            "You’re dodgin’ non-stop! You’re a cosmic beast—yayaya!",
            "Near-miss streak! You’re a force of nature out there!",
            "You’re weavin’ through danger! That streak’s insane!",
            "Streakin’ with dodges! You’re a galactic warrior!",
            "You’re on a near-miss tear! Keep slippin’ through!",
            "Near-miss streak! You’re a cosmic pro—yayaya, champ!",
            "You’re dodgin’ like a pro! That streak’s on point!",
            "Streakin’ through the void! You’re a survival king!"
        )
        return selectQuip(quips, "streak")
    }

    private fun getNearMissStreakQuip(): String {
        val quips = listOf(
            "You’re dodgin’ like a pro! That’s a near-miss streak!",
            "Near-miss streak! You’re dancin’ through danger!",
            "You’re on a dodgin’ streak! Keep slippin’ through!",
            "Streakin’ with near misses! You’re untouchable—yayaya!",
            "You’re dodgin’ everything! That streak’s impressive!",
            "Near-miss streak! You’re a cosmic acrobat!",
            "You’re slippin’ through danger! That streak’s on fire!",
            "Streakin’ with dodges! You’re a galactic ninja!",
            "You’re on a near-miss run! Keep that momentum!",
            "Near-miss streak! You’re a void-dancin’ beast—yayaya!",
            "You’re dodgin’ like a pro! That streak’s legendary!",
            "Streakin’ through danger! You’re a cosmic survivor!",
            "You’re on a dodgin’ roll! Keep weavin’ through!",
            "Near-miss streak! You’re a galactic escape artist!",
            "You’re slippin’ past everything! That streak’s hot!",
            "Streakin’ with near misses! You’re a void predator!",
            "You’re dodgin’ non-stop! You’re a cosmic beast—yayaya!",
            "Near-miss streak! You’re a force of nature out there!",
            "You’re weavin’ through danger! That streak’s insane!",
            "Streakin’ with dodges! You’re a galactic warrior!",
            "You’re on a near-miss tear! Keep slippin’ through!",
            "Near-miss streak! You’re a cosmic pro—yayaya, champ!",
            "You’re dodgin’ like a pro! That streak’s on point!",
            "Streakin’ through the void! You’re a survival king!"
        )
        return selectQuip(quips, "streak")
    }

    private fun getBossIntroQuip(): String {
        val quips = listOf(
            "Boss incoming! You better step up—yayaya!",
            "Big bad’s here! You ready to dominate this punk?",
            "Boss time! Bring the violence—now!",
            "Here comes the boss! Don’t mess this up!",
            "Boss alert! You’re in the arena—show your stuff!",
            "Big guy’s arrived! You gonna fight or what?",
            "Boss is here! You better bring the heat!",
            "Boss incoming! You’re up—make it count!",
            "Big bad’s in your sights! Take ‘em down—now!",
            "Boss time! You better not choke out there!",
            "Here comes the boss! You ready to crush ‘em?",
            "Boss alert! You’re in the fight—don’t hold back!",
            "Big guy’s comin’ for you! Show ‘em who’s boss!",
            "Boss is here! You better bring the fury—yayaya!",
            "Boss incoming! You’re up—don’t let ‘em win!",
            "Big bad’s arrived! You better fight hard!",
            "Boss time! You’re in the spotlight—make it count!",
            "Here comes the boss! You better dominate—now!",
            "Boss alert! You’re in the ring—bring the heat!",
            "Big guy’s in your face! You better not back down!",
            "Boss is comin’ for you! Show ‘em your violence!",
            "Boss incoming! You’re up—yayaya, don’t choke!",
            "Big bad’s here! You better fight like a pro, kid!",
            "Boss time! You’re in the fight—make ‘em pay, rookie!"
        )
        return selectQuip(quips, "boss")
    }

    private fun getBossDefeatQuip(): String {
        val quips = listOf(
            "Boss down! You dominated—yayaya!",
            "You smoked that boss! You’re a galactic beast!",
            "Big bad’s outta here! You’re on fire!",
            "Boss defeated! You’re a legend out there!",
            "You crushed that punk! Keep slayin’—yayaya!",
            "Boss is toast! You’re a galactic warrior!",
            "Big guy’s done! You brought the momentum!",
            "Boss out! You’re a force in the void!",
            "You took down the boss! You’re a cosmic terror!",
            "Boss smoked! You’re a void-dominatin’ beast!",
            "Big bad’s history! You’re a galactic juggernaut!",
            "Boss defeated! You’re a star in the void—yayaya!",
            "You crushed that boss! You’re a cosmic pro, kid!",
            "Big guy’s outta here! You’re a galactic predator!",
            "Boss down! You’re a force of nature out there!",
            "You smoked that punk! You’re a void conqueror!",
            "Boss is toast! You’re a cosmic warrior—yayaya!",
            "Big bad’s done! You’re a galactic legend!",
            "Boss defeated! You’re a star in the void!",
            "You took down the boss! You’re a cosmic beast!",
            "Boss out! You’re a galactic terror—yayaya, rookie!",
            "Big guy’s history! You’re a void-dominatin’ pro!",
            "Boss smoked! You’re a force in the void!",
            "You crushed that boss! You’re a cosmic juggernaut!"
        )
        return selectQuip(quips, "boss")
    }

    private fun getMissileQuip(): String {
        val quips = listOf(
            "You got missiles maxed out! Rain hellfire—now!",
            "Full missile rack! You’re locked and loaded!",
            "You’re ready to launch! Make it hurt—yayaya!",
            "Missiles stocked! You’re a weapon out there!",
            "You’re armed to the teeth! Bring the boom!",
            "Missiles full! Unleash the fury—now!",
            "You’re loaded up! Show ‘em your violence!",
            "Missile count maxed! You’re a beast—fire away!",
            "You got firepower! Blast ‘em outta the void!",
            "Missiles ready! You’re a cosmic destroyer—yayaya!",
            "You’re packin’ heat! Make ‘em feel the pain!",
            "Full arsenal! You’re a galactic terror—now fire!",
            "You’re ready to rock! Unleash the chaos out there!",
            "Missiles stocked up! You’re a void-dominatin’ beast!",
            "You got the big guns! Show ‘em what you’re made of!",
            "Missile rack full! You’re a cosmic juggernaut—fire!",
            "You’re armed and dangerous! Blast ‘em—yayaya!",
            "Missiles maxed! You’re a galactic predator—strike!",
            "You’re loaded for bear! Make the void shake!",
            "Full missile load! You’re a force—unleash it!",
            "You got the firepower! Bring the pain—now, kid!",
            "Missiles ready to roll! You’re a cosmic warrior!",
            "You’re packin’ serious heat! Fire away—yayaya, rookie!",
            "Missile count maxed! You’re a void-dominatin’ force!"
        )
        return selectQuip(quips, "missiles")
    }

    private fun getDistanceQuip(): String {
        val sessionDuration = System.currentTimeMillis() - playerMemory.sessionStartTime
        val quips = if (sessionDuration > 5 * 60 * 1000) { // After 5 minutes
            listOf(
                "You hit 5000 units! Your speed’s legendary—yayaya!",
                "Another 5000 units! You’re blazin’ through space!",
                "Distance milestone! Your momentum’s unstoppable!",
                "You made 5000 more units! You’re crushin’ the void!",
                "Long haul conquered! You’re a galactic legend!",
                "You got 5000 units down! Your speed owns the stars!",
                "Distance smashed! You’re dominating out there!",
                "You’re at 5000 units! You’re a cosmic force—yayaya!",
                "You’re rackin’ up the miles! Keep tearin’ it up!",
                "5000 units in the bag! You’re a void-dominatin’ beast!",
                "You’re cruisin’ through space! Keep that momentum!",
                "Distance milestone! You’re a galactic juggernaut!",
                "You hit 5000 units! You’re a cosmic warrior—yayaya!",
                "Another 5000 units! You’re a star in the void!",
                "You made it 5000 more! You’re a galactic predator!",
                "Long haul down! You’re a force of nature out there!",
                "You got 5000 units! You’re a void conqueror!",
                "Distance crushed! You’re a cosmic pro, kid!",
                "You’re at 5000 units! You’re a galactic terror!",
                "5000 units in the books! Keep dominating!",
                "You’re rackin’ up distance! You’re a void beast!",
                "Distance milestone! You’re a cosmic juggernaut—yayaya, rookie!",
                "You hit 5000 units! You’re a galactic legend!",
                "Another 5000 units! You’re a force in the void!"
            )
        } else {
            listOf(
                "You hit 5000 units! Nice start—keep that speed up!",
                "Another 5000 units! You’re just gettin’ started!",
                "Distance milestone! You’re buildin’ momentum—nice!",
                "You made 5000 more units! You’re on your way to glory!",
                "Long haul conquered! You’re showin’ some promise!",
                "You got 5000 units down! Keep pushin’ the pace!",
                "Distance smashed! You’re makin’ a name out there!",
                "You’re at 5000 units! Solid work—keep it rollin’!",
                "You’re rackin’ up the miles! Don’t slow down now!",
                "5000 units in the bag! You’re a void-dominatin’ force!",
                "You’re cruisin’ through space! Keep that energy up!",
                "Distance milestone! You’re a galactic contender!",
                "You hit 5000 units! You’re a cosmic fighter—yayaya!",
                "Another 5000 units! You’re a rising star in the void!",
                "You made it 5000 more! You’re a galactic contender!",
                "Long haul down! You’re a force to watch out there!",
                "You got 5000 units! You’re a void conqueror in the making!",
                "Distance crushed! You’re a cosmic pro, kid!",
                "You’re at 5000 units! You’re a galactic terror!",
                "5000 units in the books! Keep dominating!",
                "You’re rackin’ up distance! You’re a void beast!",
                "Distance milestone! You’re a cosmic juggernaut—yayaya, rookie!",
                "You hit 5000 units! You’re a galactic legend in the making!",
                "Another 5000 units! You’re a force in the void!"
            )
        }
        return selectQuip(quips, "distance")
    }

    private fun getRandomQuip(): String {
        val sessionDuration = System.currentTimeMillis() - playerMemory.sessionStartTime
        val environment = when (playerMemory.currentEnvironment) {
            FlightModeManager.Environment.ASTEROID_FIELD -> "asteroid field"
            FlightModeManager.Environment.NEBULA -> "nebula"
            null -> "void" // Handle null case
            else -> "void"
        }
        val playStyle = playerMemory.preferredPlayStyle
        val tone = if (playerMemory.performanceScore < 30f) "encouraging" else "celebratory"
        val quips = when (tone) {
            "encouraging" -> listOf(
                "You’re still in the game! Keep that violence flowin’!",
                "You’re killin’ it out there! Dominate—yayaya!",
                "You’re ownin’ this $environment! The void’s yours!",
                "You’re untouchable! Rise to the top—now!",
                "You’re flyin’ high! Keep that momentum rollin’!",
                "You’re dominatin’! The stars bow to you!",
                "You’re runnin’ the show! Keep slayin’ out there!",
                "You’re carvin’ up the stars! Pure violence in motion!",
                "You’re a beast out there! Keep crushin’ it!",
                "You’re rackin’ up wins! You’re a legend—yayaya!",
                "You’re a natural! This cockpit’s your throne!",
                "You’re rulin’ the $environment! The galaxy’s yours!",
                "You’re movin’ smooth! Keep those skills sharp!",
                "You’re writin’ history! A legend in the makin’!",
                "You’re a star out here! Shine bright, baby!",
                "You’re a force of nature! Keep tearin’ it up!",
                "You’re slicin’ through space! Pure speed—yayaya!",
                "You’re a galactic gladiator! The $environment’s your arena!",
                "You’re on fire out there! Bring the heat—now!",
                "You’re a cosmic storm! Thunder through the stars!",
                "You’re a legend in the makin’! Keep dominating!",
                "You’re unstoppable! Violence. Speed. Momentum!",
                "You’re tearin’ up the $environment! Show ‘em who’s boss!",
                "You’re a cosmic titan! Crush everything out there!",
                "You’re the top dog in this space! Keep reigning!",
                "You’re bringin’ the violence! Slay ‘em all—yayaya!",
                "You’re a galactic juggernaut! Nothing stops you!",
                "You’re speedin’ through space! Own the void!",
                "You’re a cosmic conqueror! Rule the galaxy—now!",
                "You’re a beast in the $environment! Keep the carnage comin’!",
                "You’re rulin’ this galaxy! You’re a true warrior!",
                "You’re a galactic terror! Make ‘em quake out there!",
                "You’re a cosmic predator! Hunt ‘em down—yayaya!",
                "You’re a star in the void! Keep burnin’ bright!",
                "You’re a galactic warrior! Fight like a champion!",
                "You’re a void-dominatin’ beast! Keep the chaos comin’!",
                "You’re a galactic force! Tear through the stars!",
                "You’re a cosmic destroyer! Nothing can stop you—yayaya!",
                "You’re a star-slayin’ pro! Keep ownin’ the void!",
                "You’re a galactic king! Rule with an iron fist!",
                "You’re a cosmic warrior! Fight like you mean it!",
                "You’re a void predator! Hunt ‘em down—yayaya!",
                "You’re a galactic juggernaut! Crush ‘em all!",
                "You’re a star in the makin’! Shine brighter—now!",
                "You’re a cosmic beast! Keep dominatin’ out there!",
                "You’re a void conqueror! The galaxy’s yours—yayaya!",
                "You’re a galactic terror! Make ‘em run in fear!",
                "You’re a cosmic pro! Keep slayin’ the stars, champ!",
                "You’re a void-dominatin’ force! Nothing stops you!",
                "You’re a galactic gladiator! The void’s your coliseum!",
                "You’re a star-slayin’ warrior! Keep the violence comin’!",
                "You’re a cosmic legend! Your name echoes in the void!",
                "You’re a void predator! Hunt ‘em down—yayaya!",
                "You’re a galactic destroyer! Crush everything out there!",
                "You’re a cosmic juggernaut! Keep tearin’ it up!",
                "You’re a star-dominatin’ beast! The void’s your playground!",
                "You’re a galactic pro! Keep ownin’ the stars, kid!",
                "You’re a void warrior! Fight with everything you got!",
                "You’re a cosmic terror! Make ‘em quiver—yayaya!",
                "You’re a galactic force! Nothing can stop your run!",
                "You’re a star-slayin’ legend! Keep burnin’ bright!",
                "You’re a void conqueror! Rule the galaxy—yayaya!",
                "You’re a cosmic destroyer! Tear through the void!",
                "You’re a galactic juggernaut! Keep crushin’ it!",
                "You’re a star-dominatin’ warrior! The void’s yours!",
                "You’re a void predator! Hunt ‘em down—yayaya!",
                "You’re a cosmic pro! Keep slayin’ out there, rookie!",
                "You’re a galactic terror! Make ‘em run for their lives!",
                "You’re a star-slayin’ force! Keep dominatin’—yayaya!"
            )

            "celebratory" -> listOf(
                "You’re still in the game! Keep that violence flowin’!",
                "You’re killin’ it out there! Dominate—yayaya!",
                "You’re ownin’ this $environment! The void’s yours!",
                "You’re untouchable! Rise to the top—now!",
                "You’re flyin’ high! Keep that momentum rollin’!",
                "You’re dominatin’! The stars bow to you!",
                "You’re runnin’ the show! Keep slayin’ out there!",
                "You’re carvin’ up the stars! Pure violence in motion!",
                "You’re a beast out there! Keep crushin’ it!",
                "You’re rackin’ up wins! You’re a legend—yayaya!",
                "You’re a natural! This cockpit’s your throne!",
                "You’re rulin’ the $environment! The galaxy’s yours!",
                "You’re movin’ smooth! Keep those skills sharp!",
                "You’re writin’ history! A legend in the makin’!",
                "You’re a star out here! Shine bright, baby!",
                "You’re a force of nature! Keep tearin’ it up!",
                "You’re slicin’ through space! Pure speed—yayaya!",
                "You’re a galactic gladiator! The $environment’s your arena!",
                "You’re on fire out there! Bring the heat—now!",
                "You’re a cosmic storm! Thunder through the stars!",
                "You’re a legend in the makin’! Keep dominating!",
                "You’re unstoppable! Violence. Speed. Momentum!",
                "You’re tearin’ up the $environment! Show ‘em who’s boss!",
                "You’re a cosmic titan! Crush everything out there!",
                "You’re the top dog in this space! Keep reigning!",
                "You’re bringin’ the violence! Slay ‘em all—yayaya!",
                "You’re a galactic juggernaut! Nothing stops you!",
                "You’re speedin’ through space! Own the void!",
                "You’re a cosmic conqueror! Rule the galaxy—now!",
                "You’re a beast in the $environment! Keep the carnage comin’!",
                "You’re rulin’ this galaxy! You’re a true warrior!",
                "You’re a galactic terror! Make ‘em quake out there!",
                "You’re a cosmic predator! Hunt ‘em down—yayaya!",
                "You’re a star in the void! Keep burnin’ bright!",
                "You’re a galactic warrior! Fight like a champion!",
                "You’re a void-dominatin’ beast! Keep the chaos comin’!",
                "You’re a galactic force! Tear through the stars!",
                "You’re a cosmic destroyer! Nothing can stop you—yayaya!",
                "You’re a star-slayin’ pro! Keep ownin’ the void!",
                "You’re a galactic king! Rule with an iron fist!",
                "You’re a cosmic warrior! Fight like you mean it!",
                "You’re a void predator! Hunt ‘em down—yayaya!",
                "You’re a galactic juggernaut! Crush ‘em all!",
                "You’re a star in the makin’! Shine brighter—now!",
                "You’re a cosmic beast! Keep dominatin’ out there!",
                "You’re a void conqueror! The galaxy’s yours—yayaya!",
                "You’re a galactic terror! Make ‘em run in fear!",
                "You’re a cosmic pro! Keep slayin’ the stars, champ!",
                "You’re a void-dominatin’ force! Nothing stops you!",
                "You’re a galactic gladiator! The void’s your coliseum!",
                "You’re a star-slayin’ warrior! Keep the violence comin’!",
                "You’re a cosmic legend! Your name echoes in the void!",
                "You’re a void predator! Hunt ‘em down—yayaya!",
                "You’re a galactic destroyer! Crush everything out there!",
                "You’re a cosmic juggernaut! Keep tearin’ it up!",
                "You’re a star-dominatin’ beast! The void’s your playground!",
                "You’re a galactic pro! Keep ownin’ the stars, kid!",
                "You’re a void warrior! Fight with everything you got!",
                "You’re a cosmic terror! Make ‘em quiver—yayaya!",
                "You’re a galactic force! Nothing can stop your run!",
                "You’re a star-slayin’ legend! Keep burnin’ bright!",
                "You’re a void conqueror! Rule the galaxy—yayaya!",
                "You’re a cosmic destroyer! Tear through the void!",
                "You’re a galactic juggernaut! Keep crushin’ it!",
                "You’re a star-dominatin’ warrior! The void’s yours!",
                "You’re a void predator! Hunt ‘em down—yayaya!",
                "You’re a cosmic pro! Keep slayin’ out there, rookie!",
                "You’re a galactic terror! Make ‘em run for their lives!",
                "You’re a star-slayin’ force! Keep dominatin’—yayaya!"
            )

            else -> listOf(
                "Something went wrong, but you’re still in the game! Keep pushing—yayaya!"
            ) // Fallback for unexpected tone values
        }
        return selectQuip(quips, "quip")
    }

    private fun selectQuip(quips: List<String>, category: String): String {
        // Context-aware weighting
        val weights = quips.map { quip ->
            var weight = 1.0f
            if (quip in quipHistory) weight *= 0.1f // Reduce weight if recently used
            when (category) {
                "powerup" -> if (playerMemory.recentPowerUps.isNotEmpty()) weight *= 2.0f
                "threat" -> if (playerMemory.recentThreats > 0) weight *= (1 + playerMemory.recentThreats * 0.5f)
                "streak" -> if (playerMemory.powerUpStreak >= 2 || playerMemory.nearMissStreak >= 3) weight *= 3.0f
                "quip" -> {
                    when (playerMemory.preferredPlayStyle) {
                        PlayStyle.AGGRESSIVE -> if (quip.contains("violence") || quip.contains("slay")) weight *= 1.5f
                        PlayStyle.CAUTIOUS -> if (quip.contains("dodge") || quip.contains("survive")) weight *= 1.5f
                        PlayStyle.COLLECTOR -> if (quip.contains("collect") || quip.contains("grab")) weight *= 1.5f
                        else -> weight // Handle BALANCED case
                    }
                    if (playerMemory.lastQuipCategory == "streak" && quip.contains("dodge")) weight *= 2.0f // Quip chaining
                }
            }
            weight
        }

        // Weighted random selection
        val totalWeight = weights.sum()
        val randomWeight = Random.nextFloat() * totalWeight
        var cumulativeWeight = 0f
        for (i in quips.indices) {
            cumulativeWeight += weights[i]
            if (randomWeight <= cumulativeWeight) {
                val selectedQuip = quips[i]
                // Update quip history and last category
                quipHistory.add(selectedQuip)
                if (quipHistory.size > maxQuipHistory) quipHistory.removeAt(0)
                playerMemory.lastQuipCategory = category
                return selectedQuip
            }
        }
        // Fallback
        val selectedQuip = quips.random()
        quipHistory.add(selectedQuip)
        if (quipHistory.size > maxQuipHistory) quipHistory.removeAt(0)
        playerMemory.lastQuipCategory = category
        return selectedQuip
    }

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

    fun addMessage(message: String) {
        if (messages.size >= maxMessages) {
            messages.removeAt(0) // Remove oldest message if at limit
        }
        messages.add(message)
        Timber.d("AI Assistant message added: $message")
    }

    fun getBottomOffset(): Float {
        return bottomOffset
    }
}