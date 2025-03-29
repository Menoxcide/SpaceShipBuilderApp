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

        // Populate achievements
        val allAchievements = achievementManager.getAllAchievements()
        allAchievements.forEachIndexed { index, achievement ->
            val entryLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                // Alternate background colors
                setBackgroundColor(if (index % 2 == 0) Color.parseColor("#2A2A2A") else Color.parseColor("#1A1A1A"))
            }

            // Achievement details
            val statusText = if (achievement.isUnlocked) "Completed" else "In Progress"
            val statusColor = if (achievement.isUnlocked) Color.GREEN else Color.WHITE
            val detailsText = TextView(this).apply {
                text = "${achievement.name}\n${achievement.description}\nReward: ${achievement.rewardStars} Stars\nStatus: $statusText"
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@AchievementsActivity, android.R.color.white))
                setPadding(0, 0, 0, 8)
            }
            entryLayout.addView(detailsText)

            // Progress bar or completion indicator
            if (!achievement.isUnlocked) {
                val progress = calculateAchievementProgress(achievement)
                val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    max = 100
                    this.progress = progress
                }
                entryLayout.addView(progressBar)

                val progressText = TextView(this).apply {
                    text = "Progress: $progress%"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@AchievementsActivity, android.R.color.white))
                }
                entryLayout.addView(progressText)
            } else {
                val completedText = TextView(this).apply {
                    text = "✔ Achievement Unlocked!"
                    textSize = 14f
                    setTextColor(Color.GREEN)
                    setPadding(0, 8, 0, 0)
                }
                entryLayout.addView(completedText)
            }

            entriesContainer.addView(entryLayout)
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Play dialog open sound
        audioManager.playDialogOpenSound()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun calculateAchievementProgress(achievement: AchievementManager.Achievement): Int {
        val level: Int = gameEngine.level
        val distanceTraveled: Float = gameEngine.distanceTraveled
        val currentScore: Int = gameEngine.currentScore
        val starsCollected: Int = gameEngine.starsCollected
        val missilesLaunched: Int = gameEngine.missileCount // Placeholder, needs tracking
        val bossesDefeated: Int = (gameEngine.level - 1) / 10 // Approximate, needs tracking

        Timber.d("Calculating progress for ${achievement.id}: level=$level, distanceTraveled=$distanceTraveled, currentScore=$currentScore, starsCollected=$starsCollected, missilesLaunched=$missilesLaunched, bossesDefeated=$bossesDefeated")

        return when (achievement.id) {
            "first_flight" -> if (distanceTraveled > 0f) 100 else 0
            "space_explorer" -> ((distanceTraveled / 1000f) * 100f).toInt().coerceIn(0, 100)
            "asteroid_destroyer" -> ((currentScore.toFloat() / 1000f) * 100f).toInt().coerceIn(0, 100)
            "level_master" -> ((level.toFloat() / 10f) * 100f).toInt().coerceIn(0, 100)
            "star_collector" -> ((starsCollected.toFloat() / 50f) * 100f).toInt().coerceIn(0, 100)
            "galactic_voyager" -> ((distanceTraveled / 5000f) * 100f).toInt().coerceIn(0, 100)
            "missile_maniac" -> ((missilesLaunched.toFloat() / 50f) * 100f).toInt().coerceIn(0, 100)
            "boss_slayer" -> ((bossesDefeated.toFloat() / 5f) * 100f).toInt().coerceIn(0, 100)
            "survivor" -> ((level.toFloat() / 20f) * 100f).toInt().coerceIn(0, 100)
            "stellar_hoarder" -> ((starsCollected.toFloat() / 100f) * 100f).toInt().coerceIn(0, 100)
            else -> 0
        }
    }
}