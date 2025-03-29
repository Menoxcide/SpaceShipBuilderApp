package com.example.spaceshipbuilderapp

import android.graphics.Color
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

        // Group achievements by type
        val allAchievements = achievementManager.getAllAchievements()
        val groupedAchievements = allAchievements.groupBy { it.id.split("_")[0] + "_" + it.id.split("_")[1] }

        groupedAchievements.entries.forEachIndexed { index, (type, tiers) ->
            val entryLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(if (index % 2 == 0) Color.parseColor("#2A2A2A") else Color.parseColor("#1A1A1A"))
            }

            // Achievement type header
            val typeText = TextView(this).apply {
                text = tiers[0].name.split(" ")[0] + " " + tiers[0].name.split(" ")[1] // e.g., "Boss Slayer"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 8)
            }
            entryLayout.addView(typeText)

            // Display each tier
            tiers.forEach { tier ->
                val tierLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8)
                }

                val statusText = if (tier.isUnlocked) "Completed" else "In Progress"
                val statusColor = if (tier.isUnlocked) Color.GREEN else Color.WHITE
                val tierDetails = TextView(this).apply {
                    text = "${tier.name.split(" ").drop(2).joinToString(" ")}: ${tier.description}\nReward: ${tier.rewardStars} Stars\nStatus: $statusText"
                    textSize = 14f
                    setTextColor(statusColor)
                    setPadding(0, 0, 0, 4)
                }
                tierLayout.addView(tierDetails)

                if (!tier.isUnlocked) {
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
                    tierLayout.addView(progressBar)

                    val progressText = TextView(this).apply {
                        text = "Progress: $progress%"
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(this@AchievementsActivity, android.R.color.white))
                    }
                    tierLayout.addView(progressText)
                } else {
                    val completedText = TextView(this).apply {
                        text = "✔ Achievement Unlocked!"
                        textSize = 12f
                        setTextColor(Color.GREEN)
                        setPadding(0, 4, 0, 0)
                    }
                    tierLayout.addView(completedText)
                }

                entryLayout.addView(tierLayout)
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
            "first_flight_1" -> if (distanceTraveled > 0f) 100 else 0
            "first_flight_2" -> ((distanceTraveled / 100f) * 100f).toInt().coerceIn(0, 100)
            "first_flight_3" -> ((distanceTraveled / 500f) * 100f).toInt().coerceIn(0, 100)
            "space_explorer_1" -> ((distanceTraveled / 1000f) * 100f).toInt().coerceIn(0, 100)
            "space_explorer_2" -> ((distanceTraveled / 2500f) * 100f).toInt().coerceIn(0, 100)
            "space_explorer_3" -> ((distanceTraveled / 5000f) * 100f).toInt().coerceIn(0, 100)
            "asteroid_destroyer_1" -> ((currentScore.toFloat() / 500f) * 100f).toInt().coerceIn(0, 100)
            "asteroid_destroyer_2" -> ((currentScore.toFloat() / 1000f) * 100f).toInt().coerceIn(0, 100)
            "asteroid_destroyer_3" -> ((currentScore.toFloat() / 2000f) * 100f).toInt().coerceIn(0, 100)
            "level_master_1" -> ((level.toFloat() / 5f) * 100f).toInt().coerceIn(0, 100)
            "level_master_2" -> ((level.toFloat() / 10f) * 100f).toInt().coerceIn(0, 100)
            "level_master_3" -> ((level.toFloat() / 20f) * 100f).toInt().coerceIn(0, 100)
            "star_collector_1" -> ((starsCollected.toFloat() / 25f) * 100f).toInt().coerceIn(0, 100)
            "star_collector_2" -> ((starsCollected.toFloat() / 50f) * 100f).toInt().coerceIn(0, 100)
            "star_collector_3" -> ((starsCollected.toFloat() / 100f) * 100f).toInt().coerceIn(0, 100)
            "galactic_voyager_1" -> ((distanceTraveled / 2000f) * 100f).toInt().coerceIn(0, 100)
            "galactic_voyager_2" -> ((distanceTraveled / 5000f) * 100f).toInt().coerceIn(0, 100)
            "galactic_voyager_3" -> ((distanceTraveled / 10000f) * 100f).toInt().coerceIn(0, 100)
            "missile_maniac_1" -> ((missilesLaunched.toFloat() / 25f) * 100f).toInt().coerceIn(0, 100)
            "missile_maniac_2" -> ((missilesLaunched.toFloat() / 50f) * 100f).toInt().coerceIn(0, 100)
            "missile_maniac_3" -> ((missilesLaunched.toFloat() / 100f) * 100f).toInt().coerceIn(0, 100)
            "boss_slayer_1" -> ((bossesDefeated.toFloat() / 1f) * 100f).toInt().coerceIn(0, 100)
            "boss_slayer_2" -> ((bossesDefeated.toFloat() / 5f) * 100f).toInt().coerceIn(0, 100)
            "boss_slayer_3" -> ((bossesDefeated.toFloat() / 10f) * 100f).toInt().coerceIn(0, 100)
            "survivor_1" -> ((level.toFloat() / 10f) * 100f).toInt().coerceIn(0, 100)
            "survivor_2" -> ((level.toFloat() / 20f) * 100f).toInt().coerceIn(0, 100)
            "survivor_3" -> ((level.toFloat() / 30f) * 100f).toInt().coerceIn(0, 100)
            "stellar_hoarder_1" -> ((starsCollected.toFloat() / 50f) * 100f).toInt().coerceIn(0, 100)
            "stellar_hoarder_2" -> ((starsCollected.toFloat() / 100f) * 100f).toInt().coerceIn(0, 100)
            "stellar_hoarder_3" -> ((starsCollected.toFloat() / 200f) * 100f).toInt().coerceIn(0, 100)
            else -> 0
        }
    }
}