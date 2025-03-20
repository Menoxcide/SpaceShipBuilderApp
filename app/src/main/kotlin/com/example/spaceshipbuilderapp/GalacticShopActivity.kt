package com.example.spaceshipbuilderapp

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GalacticShopActivity : AppCompatActivity() {

    @Inject lateinit var gameEngine: GameEngine

    private lateinit var starsCollectedText: TextView
    private lateinit var shipSet2Requirements: TextView
    private lateinit var shipSet3Requirements: TextView
    private lateinit var unlockShipSet2Button: Button
    private lateinit var unlockShipSet3Button: Button
    private lateinit var backButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_galactic_shop)

        starsCollectedText = findViewById(R.id.starsCollectedText)
        shipSet2Requirements = findViewById(R.id.shipSet2Requirements)
        shipSet3Requirements = findViewById(R.id.shipSet3Requirements)
        unlockShipSet2Button = findViewById(R.id.unlockShipSet2Button)
        unlockShipSet3Button = findViewById(R.id.unlockShipSet3Button)
        backButton = findViewById(R.id.backButton)

        updateShopUI()

        unlockShipSet2Button.setOnClickListener {
            if (gameEngine.highestLevel >= 20 && gameEngine.starsCollected >= 20) {
                gameEngine.starsCollected -= 20
                gameEngine.unlockShipSet(1)
                showToast("Ship Set 2 Unlocked!")
                updateShopUI()
            } else {
                showToast("Not enough level or stars to unlock Ship Set 2!")
            }
        }

        unlockShipSet3Button.setOnClickListener {
            if (gameEngine.highestLevel >= 40 && gameEngine.starsCollected >= 40) {
                gameEngine.starsCollected -= 40
                gameEngine.unlockShipSet(2)
                showToast("Ship Set 3 Unlocked!")
                updateShopUI()
            } else {
                showToast("Not enough level or stars to unlock Ship Set 3!")
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun updateShopUI() {
        starsCollectedText.text = "Stars Collected: ${gameEngine.starsCollected}"

        // Ship Set 2
        if (gameEngine.getUnlockedShipSets().contains(1)) {
            shipSet2Requirements.text = "Unlocked!"
            unlockShipSet2Button.isEnabled = false
        } else {
            shipSet2Requirements.text = "Requires: Level 20, 20 Stars"
            unlockShipSet2Button.isEnabled = gameEngine.highestLevel >= 20 && gameEngine.starsCollected >= 20
        }

        // Ship Set 3
        if (gameEngine.getUnlockedShipSets().contains(2)) {
            shipSet3Requirements.text = "Unlocked!"
            unlockShipSet3Button.isEnabled = false
        } else {
            shipSet3Requirements.text = "Requires: Level 40, 40 Stars"
            unlockShipSet3Button.isEnabled = gameEngine.highestLevel >= 40 && gameEngine.starsCollected >= 40
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}