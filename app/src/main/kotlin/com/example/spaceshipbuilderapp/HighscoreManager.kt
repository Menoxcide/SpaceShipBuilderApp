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
    private val highscores: MutableList<ScoreEntry> = mutableListOf()

    data class ScoreEntry(val name: String, val score: Int) : Comparable<ScoreEntry> {
        override fun compareTo(other: ScoreEntry): Int = other.score.compareTo(this.score) // Descending order
    }

    init {
        loadHighscores()
    }

    private fun loadHighscores() {
        try {
            val jsonString = prefs.getString("highscores", null)
            if (jsonString != null) {
                val jsonArray = JSONArray(jsonString)
                val newScores = mutableListOf<ScoreEntry>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    newScores.add(ScoreEntry(jsonObject.getString("name"), jsonObject.getInt("score")))
                }
                highscores.clear()
                highscores.addAll(newScores)
                highscores.sort() // Sort descending
                if (highscores.size > MAX_SCORES) {
                    highscores.subList(MAX_SCORES, highscores.size).clear()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load highscores, keeping list empty or as-is")
        }
    }

    fun addScore(name: String, score: Int) {
        highscores.add(ScoreEntry(name, score))
        highscores.sort()
        if (highscores.size > MAX_SCORES) {
            highscores.subList(MAX_SCORES, highscores.size).clear()
        }
        saveHighscores()
    }

    private fun saveHighscores() {
        try {
            val jsonArray = JSONArray()
            highscores.forEach { entry ->
                val jsonObject = org.json.JSONObject()
                jsonObject.put("name", entry.name)
                jsonObject.put("score", entry.score)
                jsonArray.put(jsonObject)
            }
            prefs.edit().putString("highscores", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save highscores")
        }
    }

    fun getHighscore(): Int {
        return highscores.firstOrNull()?.score ?: 0
    }

    fun getHighscores(page: Int, pageSize: Int = 10): List<ScoreEntry> {
        val start = page * pageSize
        val end = minOf(start + pageSize, highscores.size)
        return if (start < highscores.size) highscores.subList(start, end) else emptyList()
    }

    fun getTotalPages(pageSize: Int = 10): Int {
        return (highscores.size + pageSize - 1) / pageSize
    }
}