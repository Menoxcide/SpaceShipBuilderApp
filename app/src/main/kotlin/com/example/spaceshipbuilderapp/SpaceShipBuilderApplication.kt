package com.example.spaceshipbuilderapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SpaceShipBuilderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Initialize Firebase and log the result
        try {
            FirebaseApp.initializeApp(this)
            val firestore = FirebaseFirestore.getInstance()
            Timber.d("Firebase initialized successfully. Firestore instance: $firestore")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Firebase")
        }
    }
}