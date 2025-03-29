package com.example.spaceshipbuilderapp

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

class HighscoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val db = FirebaseFirestore.getInstance()
    private val MAX_SCORES = 100
    private val highscores: MutableList<ScoreEntry> = mutableListOf()

    data class ScoreEntry(val userId: String, val name: String, val score: Int, val level: Int, val distance: Float) : Comparable<ScoreEntry> {
        override fun compareTo(other: ScoreEntry): Int = other.score.compareTo(this.score)
    }

    suspend fun initialize(userId: String) {
        try {
            Timber.d("Attempting to load highscores for userId: $userId")
            val snapshot = db.collection("highscores")
                .whereEqualTo("userId", userId)
                .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_SCORES.toLong())
                .get()
                .await()

            highscores.clear()
            for (doc in snapshot.documents) {
                highscores.add(
                    ScoreEntry(
                        doc.getString("userId") ?: userId,
                        doc.getString("name") ?: "Player",
                        doc.getLong("score")?.toInt() ?: 0,
                        doc.getLong("level")?.toInt() ?: 1,
                        doc.getDouble("distance")?.toFloat() ?: 0f
                    )
                )
            }
            Timber.d("Loaded ${highscores.size} highscores for user $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load highscores: ${e.message}")
            // Instead of throwing, clear highscores and continue
            highscores.clear()
            Timber.w("Highscores cleared due to Firestore error")
        }
    }

    fun addScore(userId: String, name: String, score: Int, level: Int, distance: Float) {
        val scoreEntry = hashMapOf(
            "userId" to userId,
            "name" to name,
            "score" to score,
            "level" to level,
            "distance" to distance
        )

        db.collection("highscores").add(scoreEntry)
            .addOnSuccessListener {
                highscores.add(ScoreEntry(userId, name, score, level, distance))
                highscores.sort()
                if (highscores.size > MAX_SCORES) {
                    highscores.subList(MAX_SCORES, highscores.size).clear()
                }
                Timber.d("Added highscore for $userId: score=$score, level=$level, distance=$distance")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to add highscore for $userId")
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