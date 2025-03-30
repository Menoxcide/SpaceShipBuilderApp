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
    private val achievements = mutableListOf<AchievementTier>()
    private var userId: String? = null

    data class AchievementTier(
        val id: String, // e.g., "first_flight_i"
        val name: String, // e.g., "First Flight I"
        val description: String,
        val condition: (level: Int, distanceTraveled: Float, currentScore: Int, starsCollected: Int, missilesLaunched: Int, bossesDefeated: Int) -> Boolean,
        var isUnlocked: Boolean = false,
        val rewardStars: Int = 0
    )

    init {
        initializeAchievements()
    }

    private fun initializeAchievements() {
        achievements.clear()

        // First Flight
        achievements.add(AchievementTier("first_flight_i", "First Flight I", "Launch your ship (Distance > 0)", { _, d, _, _, _, _ -> d > 0f }, rewardStars = 5))
        achievements.add(AchievementTier("first_flight_ii", "First Flight II", "Travel 1000 units (Distance >= 1000)", { _, d, _, _, _, _ -> d >= 1000f }, rewardStars = 15))
        achievements.add(AchievementTier("first_flight_iii", "First Flight III", "Travel 5000 units (Distance >= 5000)", { _, d, _, _, _, _ -> d >= 5000f }, rewardStars = 30))

        // Space Explorer
        achievements.add(AchievementTier("space_explorer_i", "Space Explorer I", "Travel 5000 units (Distance >= 5000)", { _, d, _, _, _, _ -> d >= 5000f }, rewardStars = 20))
        achievements.add(AchievementTier("space_explorer_ii", "Space Explorer II", "Travel 15000 units (Distance >= 15000)", { _, d, _, _, _, _ -> d >= 15000f }, rewardStars = 40))
        achievements.add(AchievementTier("space_explorer_iii", "Space Explorer III", "Travel 50000 units (Distance >= 50000)", { _, d, _, _, _, _ -> d >= 50000f }, rewardStars = 75))

        // Asteroid Destroyer
        achievements.add(AchievementTier("asteroid_destroyer_i", "Asteroid Destroyer I", "Earn 2000 points from asteroids (Score >= 2000)", { _, _, s, _, _, _ -> s >= 2000 }, rewardStars = 20))
        achievements.add(AchievementTier("asteroid_destroyer_ii", "Asteroid Destroyer II", "Earn 10000 points from asteroids (Score >= 10000)", { _, _, s, _, _, _ -> s >= 10000 }, rewardStars = 40))
        achievements.add(AchievementTier("asteroid_destroyer_iii", "Asteroid Destroyer III", "Earn 50000 points from asteroids (Score >= 50000)", { _, _, s, _, _, _ -> s >= 50000 }, rewardStars = 75))

        // Level Master
        achievements.add(AchievementTier("level_master_i", "Level Master I", "Reach level 15 (Level >= 15)", { l, _, _, _, _, _ -> l >= 15 }, rewardStars = 25))
        achievements.add(AchievementTier("level_master_ii", "Level Master II", "Reach level 50 (Level >= 50)", { l, _, _, _, _, _ -> l >= 50 }, rewardStars = 50))
        achievements.add(AchievementTier("level_master_iii", "Level Master III", "Reach level 100 (Level >= 100)", { l, _, _, _, _, _ -> l >= 100 }, rewardStars = 100))

        // Star Collector
        achievements.add(AchievementTier("star_collector_i", "Star Collector I", "Collect 75 stars (Stars >= 75)", { _, _, _, s, _, _ -> s >= 75 }, rewardStars = 25))
        achievements.add(AchievementTier("star_collector_ii", "Star Collector II", "Collect 500 stars (Stars >= 500)", { _, _, _, s, _, _ -> s >= 500 }, rewardStars = 60))
        achievements.add(AchievementTier("star_collector_iii", "Star Collector III", "Collect 2000 stars (Stars >= 2000)", { _, _, _, s, _, _ -> s >= 2000 }, rewardStars = 150))

        // Galactic Voyager
        achievements.add(AchievementTier("galactic_voyager_i", "Galactic Voyager I", "Travel 10000 units total (Distance >= 10000)", { _, d, _, _, _, _ -> d >= 10000f }, rewardStars = 30))
        achievements.add(AchievementTier("galactic_voyager_ii", "Galactic Voyager II", "Travel 30000 units total (Distance >= 30000)", { _, d, _, _, _, _ -> d >= 30000f }, rewardStars = 60))
        achievements.add(AchievementTier("galactic_voyager_iii", "Galactic Voyager III", "Travel 100000 units total (Distance >= 100000)", { _, d, _, _, _, _ -> d >= 100000f }, rewardStars = 125))

        // Missile Maniac
        achievements.add(AchievementTier("missile_maniac_i", "Missile Maniac I", "Launch 75 missiles (Missiles >= 75)", { _, _, _, _, m, _ -> m >= 75 }, rewardStars = 25))
        achievements.add(AchievementTier("missile_maniac_ii", "Missile Maniac II", "Launch 300 missiles (Missiles >= 300)", { _, _, _, _, m, _ -> m >= 300 }, rewardStars = 50))
        achievements.add(AchievementTier("missile_maniac_iii", "Missile Maniac III", "Launch 1000 missiles (Missiles >= 1000)", { _, _, _, _, m, _ -> m >= 1000 }, rewardStars = 100))

        // Boss Slayer
        achievements.add(AchievementTier("boss_slayer_i", "Boss Slayer I", "Defeat 5 bosses (Bosses >= 5)", { _, _, _, _, _, b -> b >= 5 }, rewardStars = 30))
        achievements.add(AchievementTier("boss_slayer_ii", "Boss Slayer II", "Defeat 25 bosses (Bosses >= 25)", { _, _, _, _, _, b -> b >= 25 }, rewardStars = 60))
        achievements.add(AchievementTier("boss_slayer_iii", "Boss Slayer III", "Defeat 100 bosses (Bosses >= 100)", { _, _, _, _, _, b -> b >= 100 }, rewardStars = 150))

        // Survivor
        achievements.add(AchievementTier("survivor_i", "Survivor I", "Reach level 25 (Level >= 25)", { l, _, _, _, _, _ -> l >= 25 }, rewardStars = 35))
        achievements.add(AchievementTier("survivor_ii", "Survivor II", "Reach level 75 (Level >= 75)", { l, _, _, _, _, _ -> l >= 75 }, rewardStars = 70))
        achievements.add(AchievementTier("survivor_iii", "Survivor III", "Reach level 150 (Level >= 150)", { l, _, _, _, _, _ -> l >= 150 }, rewardStars = 125))

        // Stellar Hoarder
        achievements.add(AchievementTier("stellar_hoarder_i", "Stellar Hoarder I", "Collect 150 stars (Stars >= 150)", { _, _, _, s, _, _ -> s >= 150 }, rewardStars = 35))
        achievements.add(AchievementTier("stellar_hoarder_ii", "Stellar Hoarder II", "Collect 1000 stars (Stars >= 1000)", { _, _, _, s, _, _ -> s >= 1000 }, rewardStars = 75))
        achievements.add(AchievementTier("stellar_hoarder_iii", "Stellar Hoarder III", "Collect 5000 stars (Stars >= 5000)", { _, _, _, s, _, _ -> s >= 5000 }, rewardStars = 200))

        Timber.d("Initialized ${achievements.size} individual achievements")
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
                achievements.find { it.id == id }?.isUnlocked = isUnlocked
            }
            Timber.d("Loaded achievements for $userId: ${achievements.filter { it.isUnlocked }.map { it.id }}")
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
        achievements.forEach { tier ->
            if (!tier.isUnlocked && isPreviousTierUnlocked(tier) && tier.condition(level, distanceTraveled, currentScore, starsCollected, missilesLaunched, bossesDefeated)) {
                tier.isUnlocked = true
                newlyUnlocked.add(tier)
                saveAchievement(tier)
                Timber.d("Unlocked achievement: ${tier.name}, rewarded ${tier.rewardStars} stars")
            }
        }
        return newlyUnlocked
    }

    fun isPreviousTierUnlocked(tier: AchievementTier): Boolean {
        val tierNumber = when {
            tier.id.endsWith("_i") -> 1
            tier.id.endsWith("_ii") -> 2
            tier.id.endsWith("_iii") -> 3
            else -> return true // Shouldn't happen with current structure
        }
        if (tierNumber == 1) return true // Tier I has no previous tier
        val baseId = tier.id.substringBeforeLast("_")
        val previousTierId = when (tierNumber) {
            2 -> "${baseId}_i"
            3 -> "${baseId}_ii"
            else -> return true
        }
        return achievements.find { it.id == previousTierId }?.isUnlocked ?: false
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
        return achievements.filter { it.isUnlocked }
    }

    fun getAllAchievements(): List<AchievementTier> {
        return achievements.toList()
    }

    fun getRewardStars(tier: AchievementTier): Int {
        return if (tier.isUnlocked) tier.rewardStars else 0
    }
}