package com.example.spaceshipbuilderapp

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class LeaderboardActivity : AppCompatActivity() {
    @Inject lateinit var highscoreManager: HighscoreManager
    @Inject lateinit var audioManager: AudioManager

    private lateinit var entriesContainer: LinearLayout
    private var currentPage = 0
    private val pageSize = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        entriesContainer = findViewById(R.id.leaderboardEntries)
        val prevButton = findViewById<Button>(R.id.prevButton)
        val nextButton = findViewById<Button>(R.id.nextButton)

        // Get userId from intent or default to "default_user" (adjust based on your app's user management)
        val userId = intent.getStringExtra("userId") ?: "default_user"

        // Load highscores asynchronously and update UI when done
        CoroutineScope(Dispatchers.Main).launch {
            try {
                highscoreManager.initialize(userId)
                updateLeaderboardEntries()
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize highscore manager: ${e.message}")
                // Optionally show an error message to the user
            }
        }

        prevButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updateLeaderboardEntries()
            }
        }

        nextButton.setOnClickListener {
            val totalPages = highscoreManager.getTotalPages(pageSize)
            if (currentPage < totalPages - 1) {
                currentPage++
                updateLeaderboardEntries()
            }
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Play dialog open sound
        audioManager.playDialogOpenSound()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun updateLeaderboardEntries() {
        entriesContainer.removeAllViews()
        val scores = highscoreManager.getHighscores(currentPage, pageSize)
        val totalPages = highscoreManager.getTotalPages(pageSize)
        findViewById<Button>(R.id.prevButton).isEnabled = currentPage > 0
        findViewById<Button>(R.id.nextButton).isEnabled = currentPage < totalPages - 1

        if (scores.isEmpty()) {
            val noScoresText = TextView(this).apply {
                text = "No highscores available yet."
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@LeaderboardActivity, android.R.color.white))
                setPadding(0, 0, 0, 8)
            }
            entriesContainer.addView(noScoresText)
        } else {
            scores.forEachIndexed { index, entry ->
                val position = currentPage * pageSize + index + 1
                val entryLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, 8)
                }

                // Add icon based on position
                val iconResId = when (position) {
                    1 -> R.drawable.ic_trophy_gold
                    2 -> R.drawable.ic_trophy_silver
                    3 -> R.drawable.ic_trophy_bronze
                    else -> R.drawable.ic_trophy_default
                }
                val iconView = ImageView(this).apply {
                    setImageResource(iconResId)
                    layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                        setMargins(0, 0, 8, 0)
                    }
                }
                entryLayout.addView(iconView)

                // Add entry text
                val entryText = TextView(this).apply {
                    text = "$position. ${entry.name}: Score: ${entry.score}, Level: ${entry.level}, Distance: ${entry.distance.toInt()}"
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(this@LeaderboardActivity, android.R.color.white))
                }
                entryLayout.addView(entryText)

                entriesContainer.addView(entryLayout)
            }
        }
        Timber.d("Updated leaderboard with ${scores.size} entries for page $currentPage")
    }
}