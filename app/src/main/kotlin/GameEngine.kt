package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

class GameEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    val renderer: Renderer,
    private val highscoreManager: HighscoreManager,
    val gameStateManager: GameStateManager,
    private val buildModeManager: BuildModeManager,
    val flightModeManager: FlightModeManager,
    private val achievementManager: AchievementManager,
    val skillManager: SkillManager,
    val aiAssistant: AIAssistant
) {
    private val db = FirebaseFirestore.getInstance()

    var level: Int
        get() = flightModeManager.shipManager.level
        set(value) {
            if (flightModeManager.shipManager.level != value) {
                flightModeManager.shipManager.level = value
                updateUnlockedShipSets()
                if (userId != null) {
                    savePersistentData(userId!!)
                }
            }
        }

    var playerName: String = "Player"

    var currentScore: Int
        get() = flightModeManager.currentScore
        set(value) {
            flightModeManager.currentScore = value
        }

    var highestScore: Int
        get() = flightModeManager.highestScore
        set(value) {
            flightModeManager.highestScore = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var highestLevel: Int
        get() = flightModeManager.shipManager.highestLevel
        set(value) {
            flightModeManager.shipManager.highestLevel = value
            updateUnlockedShipSets()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var starsCollected: Int
        get() = flightModeManager.shipManager.starsCollected
        set(value) {
            flightModeManager.shipManager.starsCollected = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var experience: Long = 0
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var shipColor: String
        get() = flightModeManager.shipManager.shipColor
        set(value) {
            flightModeManager.shipManager.shipColor = value
            Timber.d("Ship color set to $value")
            if (gameStateManager.gameState == GameState.FLIGHT) flightModeManager.shipManager.applyShipColorEffects()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var selectedShipSet: Int
        get() = flightModeManager.shipManager.selectedShipSet
        set(value) {
            flightModeManager.shipManager.selectedShipSet = value
            flightModeManager.shipManager.maxMissiles = calculateMaxMissiles()
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var reviveCount: Int
        get() = flightModeManager.shipManager.reviveCount
        set(value) {
            flightModeManager.shipManager.reviveCount = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var destroyAllCharges: Int
        get() = flightModeManager.shipManager.destroyAllCharges
        set(value) {
            flightModeManager.shipManager.destroyAllCharges = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var missilesLaunched: Int = 0
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var bossesDefeated: Int = 0
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var speedBoostExtended: Boolean = false
        set(value) {
            field = value
            if (value) {
                flightModeManager.powerUpManager.effectDuration = (10000L * 1.5).toLong()
            } else {
                flightModeManager.powerUpManager.effectDuration = 10000L
            }
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var extraMissileSlots: Int = 0
        set(value) {
            field = value.coerceIn(0, 2)
            flightModeManager.shipManager.maxMissiles = calculateMaxMissiles()
            flightModeManager.shipManager.missileCount = flightModeManager.shipManager.maxMissiles
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var fuelTankUpgraded: Boolean = false
        set(value) {
            field = value
            flightModeManager.shipManager.fuelCapacity = if (value) 150f else 100f
            flightModeManager.shipManager.fuel = flightModeManager.shipManager.fuelCapacity
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    val isDestroyAllUnlocked: Boolean
        get() = flightModeManager.shipManager.destroyAllCharges > 0

    var shipX: Float
        get() = flightModeManager.shipManager.shipX
        set(value) {
            flightModeManager.shipManager.shipX = value
        }

    var shipY: Float
        get() = flightModeManager.shipManager.shipY
        set(value) {
            flightModeManager.shipManager.shipY = value
        }

    var totalShipHeight: Float
        get() = flightModeManager.shipManager.totalShipHeight
        set(value) {
            flightModeManager.shipManager.totalShipHeight = value
        }

    var maxPartHalfWidth: Float
        get() = flightModeManager.shipManager.maxPartHalfWidth
        set(value) {
            flightModeManager.shipManager.maxPartHalfWidth = value
        }

    var mergedShipBitmap: Bitmap?
        get() = flightModeManager.shipManager.mergedShipBitmap
        set(value) {
            flightModeManager.shipManager.mergedShipBitmap = value
        }

    val powerUps: List<PowerUp>
        get() = flightModeManager.gameObjectManager.powerUps

    val asteroids: List<Asteroid>
        get() = flightModeManager.gameObjectManager.asteroids

    val projectiles: List<Projectile>
        get() = flightModeManager.gameObjectManager.projectiles

    val enemyShips: List<EnemyShip>
        get() = flightModeManager.gameObjectManager.enemyShips

    val enemyProjectiles: List<Projectile>
        get() = flightModeManager.gameObjectManager.enemyProjectiles

    val homingProjectiles: List<HomingProjectile>
        get() = flightModeManager.gameObjectManager.homingProjectiles

    var glowStartTime: Long
        get() = flightModeManager.glowStartTime
        set(value) {
            flightModeManager.glowStartTime = value
        }

    val glowDuration: Long
        get() = flightModeManager.glowDuration

    var screenWidth: Float
        get() = flightModeManager.shipManager.screenWidth
        set(value) {
            flightModeManager.shipManager.screenWidth = value
        }

    var screenHeight: Float
        get() = flightModeManager.shipManager.screenHeight
        set(value) {
            flightModeManager.shipManager.screenHeight = value
        }

    var statusBarHeight: Float
        get() = flightModeManager.statusBarHeight
        set(value) {
            flightModeManager.statusBarHeight = value
        }

    var distanceTraveled: Float
        get() = flightModeManager.distanceTraveled
        set(value) {
            flightModeManager.distanceTraveled = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var longestDistanceTraveled: Float
        get() = flightModeManager.longestDistanceTraveled
        set(value) {
            flightModeManager.longestDistanceTraveled = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var levelUpAnimationStartTime: Long
        get() = flightModeManager.levelUpAnimationStartTime
        set(value) {
            flightModeManager.levelUpAnimationStartTime = value
        }

    var shieldActive: Boolean
        get() = flightModeManager.powerUpManager.shieldActive
        set(value) {
            flightModeManager.powerUpManager.shieldActive = value
        }

    var speedBoostActive: Boolean
        get() = flightModeManager.powerUpManager.speedBoostActive
        set(value) {
            flightModeManager.powerUpManager.speedBoostActive = value
        }

    var stealthActive: Boolean
        get() = flightModeManager.powerUpManager.stealthActive
        set(value) {
            flightModeManager.powerUpManager.stealthActive = value
        }

    var invincibilityActive: Boolean
        get() = flightModeManager.powerUpManager.invincibilityActive
        set(value) {
            flightModeManager.powerUpManager.invincibilityActive = value
        }

    var missileCount: Int
        get() = flightModeManager.shipManager.missileCount
        set(value) {
            flightModeManager.shipManager.missileCount = value
        }

    var hp: Float
        get() = flightModeManager.shipManager.hp
        set(value) {
            flightModeManager.shipManager.hp = value
        }

    val maxHp: Float
        get() = flightModeManager.shipManager.maxHp

    var fuel: Float
        get() = flightModeManager.shipManager.fuel
        set(value) {
            flightModeManager.shipManager.fuel = value
        }

    val fuelCapacity: Float
        get() = flightModeManager.shipManager.fuelCapacity

    // New weapon-related properties
    var unlockedWeapons: MutableSet<WeaponType> = mutableSetOf(WeaponType.Default)
        set(value) {
            field = value
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    var selectedWeapon: WeaponType = WeaponType.Default
        set(value) {
            field = value
            flightModeManager.shipManager.selectedWeapon = value // Sync with ShipManager
            if (userId != null) {
                savePersistentData(userId!!)
            }
        }

    private var userId: String? = null

    fun getUserId(): String = userId ?: "default_user"

    fun getBoss(): BossShip? = flightModeManager.gameObjectManager.getBoss()

    suspend fun loadUserData(userId: String) {
        this.userId = userId
        try {
            Timber.d("Attempting to load user data for userId: $userId")
            val doc = db.collection("users").document(userId).get().await()
            if (doc.exists()) {
                level = doc.getLong("level")?.toInt() ?: 1
                shipColor = doc.getString("shipColor") ?: "default"
                selectedShipSet = doc.getLong("selectedShipSet")?.toInt() ?: 0
                distanceTraveled = doc.getDouble("distanceTraveled")?.toFloat() ?: 0f
                longestDistanceTraveled = doc.getDouble("longestDistanceTraveled")?.toFloat() ?: 0f
                highestScore = doc.getLong("highestScore")?.toInt() ?: 0
                highestLevel = doc.getLong("highestLevel")?.toInt() ?: 1
                starsCollected = doc.getLong("starsCollected")?.toInt() ?: 0
                reviveCount = doc.getLong("reviveCount")?.toInt() ?: 0
                destroyAllCharges = doc.getLong("destroyAllCharges")?.toInt() ?: 0
                missilesLaunched = doc.getLong("missilesLaunched")?.toInt() ?: 0
                bossesDefeated = doc.getLong("bossesDefeated")?.toInt() ?: 0
                skillManager.skillPoints = doc.getLong("skillPoints")?.toInt() ?: 0
                experience = doc.getLong("experience") ?: 0L
                val loadedSkills = doc.get("skills") as? Map<String, Long> ?: emptyMap()
                loadedSkills.forEach { (key, value) -> skillManager.skills[key] = value.toInt() }
                speedBoostExtended = doc.getBoolean("speedBoostExtended") ?: false
                extraMissileSlots = doc.getLong("extraMissileSlots")?.toInt() ?: 0
                fuelTankUpgraded = doc.getBoolean("fuelTankUpgraded") ?: false

                // Load unlocked weapons
                val unlockedWeaponsList = doc.get("unlockedWeapons") as? List<String> ?: listOf("Default")
                unlockedWeapons.clear()
                unlockedWeapons.addAll(unlockedWeaponsList.mapNotNull {
                    when (it) {
                        "Default" -> WeaponType.Default
                        "Plasma" -> WeaponType.Plasma
                        "Missile" -> WeaponType.Missile
                        "Laser" -> WeaponType.Laser
                        else -> null
                    }
                })

                // Load selected weapon
                selectedWeapon = when (doc.getString("selectedWeapon")) {
                    "Plasma" -> WeaponType.Plasma
                    "Missile" -> WeaponType.Missile
                    "Laser" -> WeaponType.Laser
                    else -> WeaponType.Default
                }

                updateUnlockedShipSets()
                Timber.d("Loaded user data for $userId: level=$level, shipColor=$shipColor, selectedShipSet=$selectedShipSet, distanceTraveled=$distanceTraveled, longestDistanceTraveled=$longestDistanceTraveled, highestScore=$highestScore, highestLevel=$highestLevel, starsCollected=$starsCollected, reviveCount=$reviveCount, destroyAllCharges=$destroyAllCharges, missilesLaunched=$missilesLaunched, bossesDefeated=$bossesDefeated, skillPoints=${skillManager.skillPoints}, experience=$experience, skills=${skillManager.skills}, speedBoostExtended=$speedBoostExtended, extraMissileSlots=$extraMissileSlots, fuelTankUpgraded=$fuelTankUpgraded, unlockedWeapons=$unlockedWeapons, selectedWeapon=$selectedWeapon")

                if (gameStateManager.gameState != GameState.GAME_OVER) {
                    gameStateManager.loadPausedStateFromFirebase(userId, flightModeManager.gameObjectManager)
                    val loadedPausedState = gameStateManager.getPausedState()
                    if (loadedPausedState != null) {
                        Timber.d("Paused state loaded and preserved: $loadedPausedState")
                    } else {
                        Timber.d("No paused state loaded from Firebase")
                    }
                } else {
                    Timber.d("Skipped paused state load due to GAME_OVER state")
                }
            } else {
                Timber.d("User data not found for $userId, initializing with defaults")
                val userData = hashMapOf(
                    "level" to 1,
                    "shipColor" to "default",
                    "selectedShipSet" to 0,
                    "distanceTraveled" to 0f,
                    "longestDistanceTraveled" to 0f,
                    "highestScore" to 0,
                    "highestLevel" to 1,
                    "starsCollected" to 0,
                    "reviveCount" to 0,
                    "destroyAllCharges" to 0,
                    "missilesLaunched" to 0,
                    "bossesDefeated" to 0,
                    "skillPoints" to 0,
                    "experience" to 0L,
                    "skills" to skillManager.skills,
                    "speedBoostExtended" to false,
                    "extraMissileSlots" to 0,
                    "fuelTankUpgraded" to false,
                    "unlockedWeapons" to listOf("Default"),
                    "selectedWeapon" to "Default"
                )
                db.collection("users").document(userId).set(userData).await()
                level = 1
                shipColor = "default"
                selectedShipSet = 0
                distanceTraveled = 0f
                longestDistanceTraveled = 0f
                highestScore = 0
                highestLevel = 1
                starsCollected = 0
                reviveCount = 0
                destroyAllCharges = 0
                missilesLaunched = 0
                bossesDefeated = 0
                skillManager.skillPoints = 0
                experience = 0L
                skillManager.skills.clear()
                skillManager.skills.putAll(mapOf(
                    "projectile_damage" to 0, "firing_rate" to 0, "homing_missiles" to 0,
                    "speed_boost" to 0, "fuel_efficiency" to 0, "power_up_duration" to 0,
                    "max_hp" to 0, "hp_regeneration" to 0, "shield_strength" to 0
                ))
                speedBoostExtended = false
                extraMissileSlots = 0
                fuelTankUpgraded = false
                unlockedWeapons = mutableSetOf(WeaponType.Default)
                selectedWeapon = WeaponType.Default
                updateUnlockedShipSets()
                gameStateManager.resetPausedState()
                Timber.d("Initialized new user data for $userId")
            }
            achievementManager.loadAchievements(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load user data from Firestore: ${e.message}")
            level = 1
            shipColor = "default"
            selectedShipSet = 0
            distanceTraveled = 0f
            longestDistanceTraveled = 0f
            highestScore = 0
            highestLevel = 1
            starsCollected = 0
            reviveCount = 0
            destroyAllCharges = 0
            missilesLaunched = 0
            bossesDefeated = 0
            skillManager.skillPoints = 0
            experience = 0L
            skillManager.skills.clear()
            skillManager.skills.putAll(mapOf(
                "projectile_damage" to 0, "firing_rate" to 0, "homing_missiles" to 0,
                "speed_boost" to 0, "fuel_efficiency" to 0, "power_up_duration" to 0,
                "max_hp" to 0, "hp_regeneration" to 0, "shield_strength" to 0
            ))
            speedBoostExtended = false
            extraMissileSlots = 0
            fuelTankUpgraded = false
            unlockedWeapons = mutableSetOf(WeaponType.Default)
            selectedWeapon = WeaponType.Default
            updateUnlockedShipSets()
            gameStateManager.resetPausedState()
            Timber.w("Using default user data due to Firestore error")
        }
    }

    private fun updateUnlockedShipSets() {
        flightModeManager.shipManager.updateUnlockedShipSets(highestLevel, starsCollected)
    }

    private fun calculateMaxMissiles(): Int {
        val baseMissiles = when (selectedShipSet) {
            0 -> 3
            1 -> 4
            2 -> 5
            else -> 3
        }
        return baseMissiles + (skillManager.skills["homing_missiles"] ?: 0) + extraMissileSlots
    }

    fun unlockShipSet(set: Int) {
        flightModeManager.shipManager.unlockShipSet(set)
        if (userId != null) {
            savePersistentData(userId!!)
        }
    }

    fun getUnlockedShipSets(): Set<Int> = flightModeManager.shipManager.getUnlockedShipSets()

    fun setScreenDimensions(width: Float, height: Float, statusBarHeight: Float = this.statusBarHeight) {
        flightModeManager.setScreenDimensions(width, height, statusBarHeight)
        if (gameStateManager.gameState == GameState.BUILD) {
            buildModeManager.initializePlaceholders(
                screenWidth,
                screenHeight,
                renderer.cockpitPlaceholderBitmap,
                renderer.fuelTankPlaceholderBitmap,
                renderer.enginePlaceholderBitmap,
                statusBarHeight
            )
        }
        Timber.d("GameEngine screen dimensions set: width=$screenWidth, height=$screenHeight, statusBarHeight=$statusBarHeight")
    }

    fun update(screenWidth: Float, screenHeight: Float, userId: String) {
        if (this.screenHeight == 0f || this.screenWidth == 0f) {
            Timber.w("screenHeight or screenWidth not set, using passed values: width=$screenWidth, height=$screenHeight")
            this.screenWidth = screenWidth
            this.screenHeight = screenHeight
        }
        flightModeManager.update(
            userId,
            onLevelChange = { newLevel -> level = newLevel },
            onHighestLevelChange = { newHighestLevel -> highestLevel = newHighestLevel },
            onHighestScoreChange = { newHighestScore -> highestScore = newHighestScore },
            onStarsCollectedChange = { newStarsCollected -> starsCollected = newStarsCollected },
            onDistanceTraveledChange = { newDistanceTraveled -> distanceTraveled = newDistanceTraveled },
            onLongestDistanceTraveledChange = { newLongestDistanceTraveled -> longestDistanceTraveled = newLongestDistanceTraveled },
            onBossDefeatedChange = { bossesDefeated++ }
        )
        val newAchievements = achievementManager.checkAchievements(level, distanceTraveled, currentScore, starsCollected, missilesLaunched, bossesDefeated)
        if (newAchievements.isNotEmpty()) {
            newAchievements.forEach { achievement ->
                starsCollected += achievementManager.getRewardStars(achievement)
                renderer.showUnlockMessage(listOf(achievement.name))
            }
        }

        aiAssistant.update(
            gameState = gameStateManager.gameState,
            shipX = shipX,
            shipY = shipY,
            asteroids = asteroids,
            enemyShips = enemyShips,
            boss = getBoss(),
            missileCount = missileCount,
            maxMissiles = flightModeManager.shipManager.maxMissiles,
            powerUpCollected = null,
            fuelHpGained = false,
            currentTime = System.currentTimeMillis(),
            fuel = fuel,
            hp = hp,
            distanceTraveled = distanceTraveled
        )
    }

    fun checkCollision(rect: RectF): Boolean {
        return flightModeManager.collisionManager.checkCollision(
            shipX = shipX,
            shipY = shipY,
            maxPartHalfWidth = maxPartHalfWidth,
            totalShipHeight = totalShipHeight,
            rect = rect
        )
    }

    fun launchShip(screenWidth: Float, screenHeight: Float): Boolean {
        if (buildModeManager.isShipSpaceworthy(screenHeight, statusBarHeight)) {
            val pausedState = gameStateManager.getPausedState()
            val isResuming = pausedState != null

            if (isResuming) {
                Timber.d("Resuming from paused state, applying stats: $pausedState")
                hp = pausedState!!.hp
                fuel = pausedState.fuel
                missileCount = pausedState.missileCount
                shipX = pausedState.shipX
                shipY = pausedState.shipY
                currentScore = pausedState.currentScore
                distanceTraveled = pausedState.distanceTraveled
                level = pausedState.level
                shieldActive = pausedState.shieldActive
                speedBoostActive = pausedState.speedBoostActive
                stealthActive = pausedState.stealthActive
                invincibilityActive = pausedState.invincibilityActive
                flightModeManager.powerUpManager.setShieldEndTime(pausedState.shieldEndTime)
                flightModeManager.powerUpManager.setSpeedBoostEndTime(pausedState.speedBoostEndTime)
                flightModeManager.powerUpManager.setStealthEndTime(pausedState.stealthEndTime)
                flightModeManager.powerUpManager.setInvincibilityEndTime(pausedState.invincibilityEndTime)
                flightModeManager.setSessionDistanceTraveled(pausedState.sessionDistanceTraveled)
                flightModeManager.setLastScoreUpdateTime(pausedState.lastScoreUpdateTime)
                flightModeManager.setLastDistanceUpdateTime(pausedState.lastDistanceUpdateTime)
                glowStartTime = pausedState.glowStartTime
                levelUpAnimationStartTime = pausedState.levelUpAnimationStartTime
                flightModeManager.setContinuesUsed(pausedState.continuesUsed)
            }

            gameStateManager.setGameState(
                GameState.FLIGHT,
                screenWidth,
                screenHeight,
                flightModeManager::resetFlightData,
                ::savePersistentData,
                userId,
                this
            )
            val sortedParts = buildModeManager.getAndClearParts().sortedBy { it.y }
            flightModeManager.launchShip(screenWidth, screenHeight, sortedParts, userId)
            val newAchievements = achievementManager.checkAchievements(level, distanceTraveled, currentScore, starsCollected, missilesLaunched, bossesDefeated)
            if (newAchievements.isNotEmpty()) {
                newAchievements.forEach { achievement ->
                    starsCollected += achievementManager.getRewardStars(achievement)
                    renderer.showUnlockMessage(listOf(achievement.name))
                }
            }
            return true
        } else {
            val reason = buildModeManager.getSpaceworthinessFailureReason(screenHeight, statusBarHeight)
            Timber.w("Launch failed: $reason")
            return false
        }
    }

    fun spawnProjectile() {
        flightModeManager.spawnProjectile()
    }

    fun launchHomingMissile(target: Any) {
        flightModeManager.launchHomingMissile(target)
        missilesLaunched++
        Timber.d("Homing missile launched, total: $missilesLaunched")
    }

    fun destroyAll(): Boolean {
        return flightModeManager.destroyAll()
    }

    fun moveShip(direction: Int) {
        flightModeManager.moveShip(direction)
    }

    fun stopShip() {
        flightModeManager.stopShip()
    }

    fun isShipSpaceworthy(screenHeight: Float): Boolean {
        return buildModeManager.isShipSpaceworthy(screenHeight, statusBarHeight)
    }

    fun getSpaceworthinessFailureReason(screenHeight: Float): String {
        return buildModeManager.getSpaceworthinessFailureReason(screenHeight, statusBarHeight)
    }

    fun isShipInCorrectOrder(): Boolean {
        return buildModeManager.isShipInCorrectOrder()
    }

    fun rotatePart(partType: String) {
        buildModeManager.rotatePart(partType)
    }

    fun upgradeSkill(skillId: String): Boolean {
        return skillManager.upgradeSkill(skillId)
    }

    fun savePersistentData(userId: String) {
        val userData = hashMapOf(
            "level" to level,
            "shipColor" to shipColor,
            "selectedShipSet" to selectedShipSet,
            "distanceTraveled" to distanceTraveled,
            "longestDistanceTraveled" to longestDistanceTraveled,
            "highestScore" to highestScore,
            "highestLevel" to highestLevel,
            "starsCollected" to starsCollected,
            "reviveCount" to reviveCount,
            "destroyAllCharges" to destroyAllCharges,
            "missilesLaunched" to missilesLaunched,
            "bossesDefeated" to bossesDefeated,
            "skillPoints" to skillManager.skillPoints,
            "experience" to experience,
            "skills" to skillManager.skills,
            "speedBoostExtended" to speedBoostExtended,
            "extraMissileSlots" to extraMissileSlots,
            "fuelTankUpgraded" to fuelTankUpgraded,
            "unlockedWeapons" to unlockedWeapons.map {
                when (it) {
                    is WeaponType.Default -> "Default"
                    is WeaponType.Plasma -> "Plasma"
                    is WeaponType.Missile -> "Missile"
                    is WeaponType.Laser -> "Laser"
                }
            },
            "selectedWeapon" to when (selectedWeapon) {
                is WeaponType.Plasma -> "Plasma"
                is WeaponType.Missile -> "Missile"
                is WeaponType.Laser -> "Laser"
                else -> "Default"
            }
        )
        db.collection("users").document(userId).set(userData)
            .addOnSuccessListener { Timber.d("Saved persistent user data for $userId, experience: $experience, speedBoostExtended=$speedBoostExtended, extraMissileSlots=$extraMissileSlots, fuelTankUpgraded=$fuelTankUpgraded, unlockedWeapons=$unlockedWeapons, selectedWeapon=$selectedWeapon") }
            .addOnFailureListener { e -> Timber.e(e, "Failed to save persistent user data for $userId: ${e.message}") }
    }

    fun setLaunchListener(listener: (Boolean) -> Unit) {
        gameStateManager.setLaunchListener(listener)
    }

    fun setGameOverListener(listener: (Boolean, Boolean, () -> Unit, () -> Unit, () -> Unit) -> Unit) {
        gameStateManager.setGameOverListener(listener)
    }

    fun notifyLaunchListener() {
        gameStateManager.notifyLaunchListener(gameStateManager.gameState == GameState.FLIGHT)
    }

    fun onDestroy() {
        Timber.d("GameEngine onDestroy called")
        flightModeManager.onDestroy()
    }

    fun addExperience(score: Int) {
        experience += score
        Timber.d("Added $score to experience, new total: $experience")
    }

    fun resetPausedState() {
        Timber.d("Resetting paused state in GameEngine")
        shipX = screenWidth / 2f
        shipY = screenHeight / 2f
        hp = flightModeManager.shipManager.maxHp
        fuel = 0f
        missileCount = flightModeManager.shipManager.maxMissiles
        currentScore = 0
        distanceTraveled = 0f
        level = 1
        shieldActive = false
        speedBoostActive = false
        stealthActive = false
        invincibilityActive = false
        flightModeManager.powerUpManager.resetPowerUpEffects()
        flightModeManager.gameObjectManager.clearGameObjects()
        flightModeManager.setSessionDistanceTraveled(0f)
        flightModeManager.setLastScoreUpdateTime(System.currentTimeMillis())
        flightModeManager.setLastDistanceUpdateTime(System.currentTimeMillis())
        glowStartTime = 0L
        levelUpAnimationStartTime = 0L
        flightModeManager.setContinuesUsed(0)
        Timber.d("Paused state reset: hp=$hp, fuel=$fuel, score=$currentScore, distance=$distanceTraveled, level=$level")
    }
}