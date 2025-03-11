package com.example.spaceshipbuilderapp

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class HighscoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SpaceshipBuilder", Context.MODE_PRIVATE)
    private var highscore: Int = 0

    init {
        loadHighscore()
    }

    private fun loadHighscore() {
        try {
            highscore = prefs.getInt("highScore", 0)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load highscore")
            highscore = 0
        }
    }

    fun getHighscore(): Int {
        return highscore
    }

    fun setHighscore(newHighscore: Int) {
        highscore = newHighscore
        prefs.edit().putInt("highScore", highscore).apply()
    }
}