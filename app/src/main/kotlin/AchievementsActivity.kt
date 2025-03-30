package com.example.spaceshipbuilderapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AchievementsActivity : AppCompatActivity() {
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var achievementManager: AchievementManager
    @Inject lateinit var audioManager: AudioManager

    private lateinit var entriesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

        entriesContainer = findViewById(R.id.achievementsEntries)

        // Get all achievements and determine their status
        val allAchievements = achievementManager.getAllAchievements()
        val inProgressAchievements = mutableListOf<AchievementManager.AchievementTier>()
        val completedAchievements = mutableListOf<AchievementManager.AchievementTier>()
        val lockedAchievements = mutableListOf<AchievementManager.AchievementTier>()

        allAchievements.forEach { tier ->
            val progress = calculateAchievementProgress(tier)
            val isPreviousUnlocked = achievementManager.isPreviousTierUnlocked(tier)
            when {
                tier.isUnlocked -> completedAchievements.add(tier)
                !tier.isUnlocked && isPreviousUnlocked && progress > 0 -> inProgressAchievements.add(tier)
                else -> lockedAchievements.add(tier)
            }
        }

        // Sort within each category (alphabetical for simplicity)
        inProgressAchievements.sortBy { it.name }
        completedAchievements.sortBy { it.name }
        lockedAchievements.sortBy { it.name }

        // Combine lists: In Progress, Completed, Locked
        val sortedAchievements = inProgressAchievements + completedAchievements + lockedAchievements

        sortedAchievements.forEachIndexed { index, tier ->
            val entryLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(if (index % 2 == 0) Color.parseColor("#2A2A2A") else Color.parseColor("#1A1A1A"))
            }

            val isInProgress = inProgressAchievements.contains(tier)
            val statusText = if (tier.isUnlocked) "Completed" else if (isInProgress) "In Progress" else "Locked"
            val statusColor = when {
                tier.isUnlocked -> Color.GREEN
                isInProgress -> Color.YELLOW
                else -> Color.GRAY
            }

            val tierName = TextView(this).apply {
                text = tier.name
                textSize = 16f
                setTextColor(statusColor)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 4)
            }
            entryLayout.addView(tierName)

            val tierDetails = TextView(this).apply {
                text = "${tier.description}\nReward: ${tier.rewardStars} Stars\nStatus: $statusText"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@AchievementsActivity, android.R.color.white))
                setPadding(0, 0, 0, 4)
            }
            entryLayout.addView(tierDetails)

            if (!tier.isUnlocked && isInProgress) {
                val progress = calculateAchievementProgress(tier)
                val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 4)
                    }
                    max = 100
                    this.progress = progress
                }
                entryLayout.addView(progressBar)

                val progressText = TextView(this).apply {
                    text = "Progress: $progress%"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@AchievementsActivity, android.R.color.white))
                }
                entryLayout.addView(progressText)
            } else if (tier.isUnlocked) {
                val completedText = TextView(this).apply {
                    text = "✔ Achievement Unlocked!"
                    textSize = 12f
                    setTextColor(Color.GREEN)
                    setPadding(0, 4, 0, 0)
                }
                entryLayout.addView(completedText)
            }

            entriesContainer.addView(entryLayout)
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        audioManager.playDialogOpenSound()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun calculateAchievementProgress(tier: AchievementManager.AchievementTier): Int {
        val level: Int = gameEngine.level
        val distanceTraveled: Float = gameEngine.distanceTraveled
        val currentScore: Int = gameEngine.currentScore
        val starsCollected: Int = gameEngine.starsCollected
        val missilesLaunched: Int = gameEngine.missilesLaunched
        val bossesDefeated: Int = gameEngine.bossesDefeated

        Timber.d("Calculating progress for ${tier.id}: level=$level, distanceTraveled=$distanceTraveled, currentScore=$currentScore, starsCollected=$starsCollected, missilesLaunched=$missilesLaunched, bossesDefeated=$bossesDefeated")

        return when (tier.id) {
            "first_flight_i" -> if (distanceTraveled > 0f) 100 else 0
            "first_flight_ii" -> ((distanceTraveled / 1000f) * 100f).toInt().coerceIn(0, 100)
            "first_flight_iii" -> ((distanceTraveled / 5000f) * 100f).toInt().coerceIn(0, 100)
            "space_explorer_i" -> ((distanceTraveled / 5000f) * 100f).toInt().coerceIn(0, 100)
            "space_explorer_ii" -> ((distanceTraveled / 15000f) * 100f).toInt().coerceIn(0, 100)
            "space_explorer_iii" -> ((distanceTraveled / 50000f) * 100f).toInt().coerceIn(0, 100)
            "asteroid_destroyer_i" -> ((currentScore.toFloat() / 2000f) * 100f).toInt().coerceIn(0, 100)
            "asteroid_destroyer_ii" -> ((currentScore.toFloat() / 10000f) * 100f).toInt().coerceIn(0, 100)
            "asteroid_destroyer_iii" -> ((currentScore.toFloat() / 50000f) * 100f).toInt().coerceIn(0, 100)
            "level_master_i" -> ((level.toFloat() / 15f) * 100f).toInt().coerceIn(0, 100)
            "level_master_ii" -> ((level.toFloat() / 50f) * 100f).toInt().coerceIn(0, 100)
            "level_master_iii" -> ((level.toFloat() / 100f) * 100f).toInt().coerceIn(0, 100)
            "star_collector_i" -> ((starsCollected.toFloat() / 75f) * 100f).toInt().coerceIn(0, 100)
            "star_collector_ii" -> ((starsCollected.toFloat() / 500f) * 100f).toInt().coerceIn(0, 100)
            "star_collector_iii" -> ((starsCollected.toFloat() / 2000f) * 100f).toInt().coerceIn(0, 100)
            "galactic_voyager_i" -> ((distanceTraveled / 10000f) * 100f).toInt().coerceIn(0, 100)
            "galactic_voyager_ii" -> ((distanceTraveled / 30000f) * 100f).toInt().coerceIn(0, 100)
            "galactic_voyager_iii" -> ((distanceTraveled / 100000f) * 100f).toInt().coerceIn(0, 100)
            "missile_maniac_i" -> ((missilesLaunched.toFloat() / 75f) * 100f).toInt().coerceIn(0, 100)
            "missile_maniac_ii" -> ((missilesLaunched.toFloat() / 300f) * 100f).toInt().coerceIn(0, 100)
            "missile_maniac_iii" -> ((missilesLaunched.toFloat() / 1000f) * 100f).toInt().coerceIn(0, 100)
            "boss_slayer_i" -> ((bossesDefeated.toFloat() / 5f) * 100f).toInt().coerceIn(0, 100)
            "boss_slayer_ii" -> ((bossesDefeated.toFloat() / 25f) * 100f).toInt().coerceIn(0, 100)
            "boss_slayer_iii" -> ((bossesDefeated.toFloat() / 100f) * 100f).toInt().coerceIn(0, 100)
            "survivor_i" -> ((level.toFloat() / 25f) * 100f).toInt().coerceIn(0, 100)
            "survivor_ii" -> ((level.toFloat() / 75f) * 100f).toInt().coerceIn(0, 100)
            "survivor_iii" -> ((level.toFloat() / 150f) * 100f).toInt().coerceIn(0, 100)
            "stellar_hoarder_i" -> ((starsCollected.toFloat() / 150f) * 100f).toInt().coerceIn(0, 100)
            "stellar_hoarder_ii" -> ((starsCollected.toFloat() / 1000f) * 100f).toInt().coerceIn(0, 100)
            "stellar_hoarder_iii" -> ((starsCollected.toFloat() / 5000f) * 100f).toInt().coerceIn(0, 100)
            else -> 0
        }
    }
}