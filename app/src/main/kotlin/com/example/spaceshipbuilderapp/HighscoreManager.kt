package com.example.spaceshipbuilderapp

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject

class HighscoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SpaceshipBuilder", Context.MODE_PRIVATE)
    private val MAX_SCORES = 100
    private val highscores: MutableList<Int> = mutableListOf() // Initialized here

    init {
        loadHighscores()
    }

    private fun loadHighscores() {
        try {
            val jsonString = prefs.getString("highscores", null)
            if (jsonString != null) {
                val jsonArray = JSONArray(jsonString)
                val newScores = mutableListOf<Int>() // Temporary list to avoid modifying highscores during parsing
                for (i in 0 until jsonArray.length()) {
                    newScores.add(jsonArray.getInt(i))
                }
                highscores.clear() // Clear only after successful parsing
                highscores.addAll(newScores)
                highscores.sortDescending()
                if (highscores.size > MAX_SCORES) {
                    highscores.subList(MAX_SCORES, highscores.size).clear()
                }
            }
            // If jsonString is null, highscores remains empty, which is valid
        } catch (e: Exception) {
            Timber.e(e, "Failed to load highscores, keeping list empty or as-is")
            // Do not clear highscores here; keep it in its initial state or previous valid state
        }
    }

    fun addScore(newScore: Int) {
        highscores.add(newScore)
        highscores.sortDescending()
        if (highscores.size > MAX_SCORES) {
            highscores.subList(MAX_SCORES, highscores.size).clear()
        }
        saveHighscores()
    }

    private fun saveHighscores() {
        try {
            val jsonArray = JSONArray()
            highscores.forEach { jsonArray.put(it) }
            prefs.edit().putString("highscores", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save highscores")
        }
    }

    fun getHighscore(): Int {
        return highscores.firstOrNull() ?: 0
    }

    fun getHighscores(page: Int, pageSize: Int = 10): List<Int> {
        val start = page * pageSize
        val end = minOf(start + pageSize, highscores.size)
        return if (start < highscores.size) highscores.subList(start, end) else emptyList()
    }

    fun getTotalPages(pageSize: Int = 10): Int {
        return (highscores.size + pageSize - 1) / pageSize
    }
}