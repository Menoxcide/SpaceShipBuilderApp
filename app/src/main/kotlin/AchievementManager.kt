package com.example.spaceshipbuilderapp

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class AchievementManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val db = FirebaseFirestore.getInstance()
    private val achievements = mutableMapOf<String, Achievement>()
    private var userId: String? = null

    data class Achievement(
        val id: String,
        val name: String,
        val description: String,
        val condition: (level: Int, distanceTraveled: Float, currentScore: Int, starsCollected: Int, missilesLaunched: Int, bossesDefeated: Int) -> Boolean, // Updated to include new params
        var isUnlocked: Boolean = false,
        val rewardStars: Int = 0
    )

    init {
        initializeAchievements()
    }

    private fun initializeAchievements() {
        achievements["first_flight"] = Achievement(
            id = "first_flight",
            name = "First Flight",
            description = "Launch your ship for the first time (Distance > 0)",
            condition = { _, distanceTraveled, _, _, _, _ -> distanceTraveled > 0f },
            rewardStars = 5
        )
        achievements["space_explorer"] = Achievement(
            id = "space_explorer",
            name = "Space Explorer",
            description = "Travel 1000 units in a single flight (Distance >= 1000)",
            condition = { _, distanceTraveled, _, _, _, _ -> distanceTraveled >= 1000f },
            rewardStars = 10
        )
        achievements["asteroid_destroyer"] = Achievement(
            id = "asteroid_destroyer",
            name = "Asteroid Destroyer",
            description = "Destroy 50 asteroids (Score >= 1000)",
            condition = { _, _, currentScore, _, _, _ -> currentScore >= 1000 },
            rewardStars = 15
        )
        achievements["level_master"] = Achievement(
            id = "level_master",
            name = "Level Master",
            description = "Reach level 10 (Level >= 10)",
            condition = { level, _, _, _, _, _ -> level >= 10 },
            rewardStars = 20
        )
        achievements["star_collector"] = Achievement(
            id = "star_collector",
            name = "Star Collector",
            description = "Collect 50 stars (Stars >= 50)",
            condition = { _, _, _, starsCollected, _, _ -> starsCollected >= 50 },
            rewardStars = 25
        )
        achievements["galactic_voyager"] = Achievement(
            id = "galactic_voyager",
            name = "Galactic Voyager",
            description = "Travel 5000 units total (Total Distance >= 5000)",
            condition = { _, distanceTraveled, _, _, _, _ -> distanceTraveled >= 5000f },
            rewardStars = 30
        )
        achievements["missile_maniac"] = Achievement(
            id = "missile_maniac",
            name = "Missile Maniac",
            description = "Launch 50 homing missiles (Missiles >= 50)",
            condition = { _, _, _, _, missilesLaunched, _ -> missilesLaunched >= 50 },
            rewardStars = 20
        )
        achievements["boss_slayer"] = Achievement(
            id = "boss_slayer",
            name = "Boss Slayer",
            description = "Defeat 5 bosses (Bosses >= 5)",
            condition = { _, _, _, _, _, bossesDefeated -> bossesDefeated >= 5 },
            rewardStars = 35
        )
        achievements["survivor"] = Achievement(
            id = "survivor",
            name = "Survivor",
            description = "Reach level 20 without dying (Level >= 20)",
            condition = { level, _, _, _, _, _ -> level >= 20 },
            rewardStars = 40
        )
        achievements["stellar_hoarder"] = Achievement(
            id = "stellar_hoarder",
            name = "Stellar Hoarder",
            description = "Collect 100 stars (Stars >= 100)",
            condition = { _, _, _, starsCollected, _, _ -> starsCollected >= 100 },
            rewardStars = 50
        )
        Timber.d("Initialized ${achievements.size} achievements")
    }

    suspend fun loadAchievements(userId: String) {
        this.userId = userId
        try {
            Timber.d("Loading achievements for userId: $userId")
            val snapshot = db.collection("users").document(userId)
                .collection("achievements").get().await()
            for (doc in snapshot.documents) {
                val id = doc.id
                val isUnlocked = doc.getBoolean("isUnlocked") ?: false
                achievements[id]?.isUnlocked = isUnlocked
            }
            Timber.d("Loaded achievements for $userId: ${achievements.filter { it.value.isUnlocked }.keys}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load achievements: ${e.message}")
        }
    }

    fun checkAchievements(
        level: Int,
        distanceTraveled: Float,
        currentScore: Int,
        starsCollected: Int,
        missilesLaunched: Int,
        bossesDefeated: Int
    ): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()
        achievements.values.forEach { achievement ->
            if (!achievement.isUnlocked && achievement.condition(level, distanceTraveled, currentScore, starsCollected, missilesLaunched, bossesDefeated)) {
                achievement.isUnlocked = true
                newlyUnlocked.add(achievement)
                saveAchievement(achievement)
                Timber.d("Unlocked achievement: ${achievement.name}, rewarded ${achievement.rewardStars} stars")
            }
        }
        return newlyUnlocked
    }

    private fun saveAchievement(achievement: Achievement) {
        userId?.let { uid ->
            val data = hashMapOf(
                "isUnlocked" to achievement.isUnlocked,
                "name" to achievement.name,
                "description" to achievement.description,
                "rewardStars" to achievement.rewardStars
            )
            db.collection("users").document(uid).collection("achievements")
                .document(achievement.id).set(data)
                .addOnSuccessListener { Timber.d("Saved achievement ${achievement.id} for $uid") }
                .addOnFailureListener { e -> Timber.e(e, "Failed to save achievement ${achievement.id}") }
        }
    }

    fun getUnlockedAchievements(): List<Achievement> {
        return achievements.values.filter { it.isUnlocked }
    }

    fun getAllAchievements(): List<Achievement> {
        return achievements.values.toList()
    }

    fun getRewardStars(achievement: Achievement): Int {
        return if (achievement.isUnlocked) achievement.rewardStars else 0
    }
}