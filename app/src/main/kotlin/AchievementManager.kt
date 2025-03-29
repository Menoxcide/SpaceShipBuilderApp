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
    private val achievements = mutableMapOf<String, List<AchievementTier>>()
    private var userId: String? = null

    data class AchievementTier(
        val id: String, // e.g., "boss_slayer_1"
        val name: String, // e.g., "Boss Slayer 1"
        val description: String,
        val condition: (level: Int, distanceTraveled: Float, currentScore: Int, starsCollected: Int, missilesLaunched: Int, bossesDefeated: Int) -> Boolean,
        var isUnlocked: Boolean = false,
        val rewardStars: Int = 0
    )

    init {
        initializeAchievements()
    }

    private fun initializeAchievements() {
        // First Flight
        achievements["first_flight"] = listOf(
            AchievementTier("first_flight_1", "First Flight 1", "Launch your ship (Distance > 0)", { _, d, _, _, _, _ -> d > 0f }, rewardStars = 5),
            AchievementTier("first_flight_2", "First Flight 2", "Travel 100 units (Distance >= 100)", { _, d, _, _, _, _ -> d >= 100f }, rewardStars = 10),
            AchievementTier("first_flight_3", "First Flight 3", "Travel 500 units (Distance >= 500)", { _, d, _, _, _, _ -> d >= 500f }, rewardStars = 15)
        )

        // Space Explorer
        achievements["space_explorer"] = listOf(
            AchievementTier("space_explorer_1", "Space Explorer 1", "Travel 1000 units (Distance >= 1000)", { _, d, _, _, _, _ -> d >= 1000f }, rewardStars = 10),
            AchievementTier("space_explorer_2", "Space Explorer 2", "Travel 2500 units (Distance >= 2500)", { _, d, _, _, _, _ -> d >= 2500f }, rewardStars = 20),
            AchievementTier("space_explorer_3", "Space Explorer 3", "Travel 5000 units (Distance >= 5000)", { _, d, _, _, _, _ -> d >= 5000f }, rewardStars = 30)
        )

        // Asteroid Destroyer
        achievements["asteroid_destroyer"] = listOf(
            AchievementTier("asteroid_destroyer_1", "Asteroid Destroyer 1", "Destroy 25 asteroids (Score >= 500)", { _, _, s, _, _, _ -> s >= 500 }, rewardStars = 10),
            AchievementTier("asteroid_destroyer_2", "Asteroid Destroyer 2", "Destroy 50 asteroids (Score >= 1000)", { _, _, s, _, _, _ -> s >= 1000 }, rewardStars = 15),
            AchievementTier("asteroid_destroyer_3", "Asteroid Destroyer 3", "Destroy 100 asteroids (Score >= 2000)", { _, _, s, _, _, _ -> s >= 2000 }, rewardStars = 25)
        )

        // Level Master
        achievements["level_master"] = listOf(
            AchievementTier("level_master_1", "Level Master 1", "Reach level 5 (Level >= 5)", { l, _, _, _, _, _ -> l >= 5 }, rewardStars = 10),
            AchievementTier("level_master_2", "Level Master 2", "Reach level 10 (Level >= 10)", { l, _, _, _, _, _ -> l >= 10 }, rewardStars = 20),
            AchievementTier("level_master_3", "Level Master 3", "Reach level 20 (Level >= 20)", { l, _, _, _, _, _ -> l >= 20 }, rewardStars = 30)
        )

        // Star Collector
        achievements["star_collector"] = listOf(
            AchievementTier("star_collector_1", "Star Collector 1", "Collect 25 stars (Stars >= 25)", { _, _, _, s, _, _ -> s >= 25 }, rewardStars = 15),
            AchievementTier("star_collector_2", "Star Collector 2", "Collect 50 stars (Stars >= 50)", { _, _, _, s, _, _ -> s >= 50 }, rewardStars = 25),
            AchievementTier("star_collector_3", "Star Collector 3", "Collect 100 stars (Stars >= 100)", { _, _, _, s, _, _ -> s >= 100 }, rewardStars = 40)
        )

        // Galactic Voyager
        achievements["galactic_voyager"] = listOf(
            AchievementTier("galactic_voyager_1", "Galactic Voyager 1", "Travel 2000 units total (Distance >= 2000)", { _, d, _, _, _, _ -> d >= 2000f }, rewardStars = 15),
            AchievementTier("galactic_voyager_2", "Galactic Voyager 2", "Travel 5000 units total (Distance >= 5000)", { _, d, _, _, _, _ -> d >= 5000f }, rewardStars = 30),
            AchievementTier("galactic_voyager_3", "Galactic Voyager 3", "Travel 10000 units total (Distance >= 10000)", { _, d, _, _, _, _ -> d >= 10000f }, rewardStars = 50)
        )

        // Missile Maniac
        achievements["missile_maniac"] = listOf(
            AchievementTier("missile_maniac_1", "Missile Maniac 1", "Launch 25 missiles (Missiles >= 25)", { _, _, _, _, m, _ -> m >= 25 }, rewardStars = 10),
            AchievementTier("missile_maniac_2", "Missile Maniac 2", "Launch 50 missiles (Missiles >= 50)", { _, _, _, _, m, _ -> m >= 50 }, rewardStars = 20),
            AchievementTier("missile_maniac_3", "Missile Maniac 3", "Launch 100 missiles (Missiles >= 100)", { _, _, _, _, m, _ -> m >= 100 }, rewardStars = 35)
        )

        // Boss Slayer
        achievements["boss_slayer"] = listOf(
            AchievementTier("boss_slayer_1", "Boss Slayer 1", "Defeat 1 boss (Bosses >= 1)", { _, _, _, _, _, b -> b >= 1 }, rewardStars = 15),
            AchievementTier("boss_slayer_2", "Boss Slayer 2", "Defeat 5 bosses (Bosses >= 5)", { _, _, _, _, _, b -> b >= 5 }, rewardStars = 35),
            AchievementTier("boss_slayer_3", "Boss Slayer 3", "Defeat 10 bosses (Bosses >= 10)", { _, _, _, _, _, b -> b >= 10 }, rewardStars = 60)
        )

        // Survivor
        achievements["survivor"] = listOf(
            AchievementTier("survivor_1", "Survivor 1", "Reach level 10 (Level >= 10)", { l, _, _, _, _, _ -> l >= 10 }, rewardStars = 20),
            AchievementTier("survivor_2", "Survivor 2", "Reach level 20 (Level >= 20)", { l, _, _, _, _, _ -> l >= 20 }, rewardStars = 40),
            AchievementTier("survivor_3", "Survivor 3", "Reach level 30 (Level >= 30)", { l, _, _, _, _, _ -> l >= 30 }, rewardStars = 60)
        )

        // Stellar Hoarder
        achievements["stellar_hoarder"] = listOf(
            AchievementTier("stellar_hoarder_1", "Stellar Hoarder 1", "Collect 50 stars (Stars >= 50)", { _, _, _, s, _, _ -> s >= 50 }, rewardStars = 25),
            AchievementTier("stellar_hoarder_2", "Stellar Hoarder 2", "Collect 100 stars (Stars >= 100)", { _, _, _, s, _, _ -> s >= 100 }, rewardStars = 50),
            AchievementTier("stellar_hoarder_3", "Stellar Hoarder 3", "Collect 200 stars (Stars >= 200)", { _, _, _, s, _, _ -> s >= 200 }, rewardStars = 75)
        )

        Timber.d("Initialized ${achievements.size} achievement types with ${achievements.values.sumOf { it.size }} tiers")
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
                achievements.values.flatten().find { it.id == id }?.isUnlocked = isUnlocked
            }
            Timber.d("Loaded achievements for $userId: ${achievements.values.flatten().filter { it.isUnlocked }.map { it.id }}")
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
    ): List<AchievementTier> {
        val newlyUnlocked = mutableListOf<AchievementTier>()
        achievements.values.forEach { tiers ->
            tiers.forEach { tier ->
                if (!tier.isUnlocked && tier.condition(level, distanceTraveled, currentScore, starsCollected, missilesLaunched, bossesDefeated)) {
                    // Check if previous tier is unlocked (if applicable)
                    val tierIndex = tiers.indexOf(tier)
                    if (tierIndex == 0 || tiers[tierIndex - 1].isUnlocked) {
                        tier.isUnlocked = true
                        newlyUnlocked.add(tier)
                        saveAchievement(tier)
                        Timber.d("Unlocked achievement: ${tier.name}, rewarded ${tier.rewardStars} stars")
                    }
                }
            }
        }
        return newlyUnlocked
    }

    private fun saveAchievement(tier: AchievementTier) {
        userId?.let { uid ->
            val data = hashMapOf(
                "isUnlocked" to tier.isUnlocked,
                "name" to tier.name,
                "description" to tier.description,
                "rewardStars" to tier.rewardStars
            )
            db.collection("users").document(uid).collection("achievements")
                .document(tier.id).set(data)
                .addOnSuccessListener { Timber.d("Saved achievement ${tier.id} for $uid") }
                .addOnFailureListener { e -> Timber.e(e, "Failed to save achievement ${tier.id}") }
        }
    }

    fun getUnlockedAchievements(): List<AchievementTier> {
        return achievements.values.flatten().filter { it.isUnlocked }
    }

    fun getAllAchievements(): List<AchievementTier> {
        return achievements.values.flatten()
    }

    fun getRewardStars(tier: AchievementTier): Int {
        return if (tier.isUnlocked) tier.rewardStars else 0
    }
}