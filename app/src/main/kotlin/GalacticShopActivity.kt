package com.example.spaceshipbuilderapp

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GalacticShopActivity : AppCompatActivity() {
    @Inject lateinit var gameEngine: GameEngine

    private lateinit var starsCollectedText: TextView
    private lateinit var unlockShipSet2Button: Button
    private lateinit var unlockShipSet3Button: Button
    private lateinit var buyReviveButton: Button
    private lateinit var buyDestroyAllButton: Button
    private lateinit var reviveCountText: TextView
    private lateinit var destroyAllChargesText: TextView
    private lateinit var shipSet2Image: ImageView
    private lateinit var shipSet3Image: ImageView
    private lateinit var shipSet2Tooltip: TextView
    private lateinit var shipSet3Tooltip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_galactic_shop)

        starsCollectedText = findViewById(R.id.starsCollectedText)
        unlockShipSet2Button = findViewById(R.id.unlockShipSet2Button)
        unlockShipSet3Button = findViewById(R.id.unlockShipSet3Button)
        buyReviveButton = findViewById(R.id.buyReviveButton)
        buyDestroyAllButton = findViewById(R.id.buyDestroyAllButton)
        reviveCountText = findViewById(R.id.reviveCountText)
        destroyAllChargesText = findViewById(R.id.destroyAllChargesText)
        shipSet2Image = findViewById(R.id.shipSet2Image)
        shipSet3Image = findViewById(R.id.shipSet3Image)
        shipSet2Tooltip = findViewById(R.id.shipSet2Tooltip)
        shipSet3Tooltip = findViewById(R.id.shipSet3Tooltip)

        updateUI()

        // Set up tooltips for ship sets
        shipSet2Image.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    shipSet2Tooltip.visibility = View.VISIBLE
                    shipSet2Tooltip.text = "Ship Set 2 Upgrades:\n" +
                            "+10% Speed\n" +
                            "+10% Bullet Speed\n" +
                            "+1 Homing Missile\n" +
                            "+50 HP"
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    shipSet2Tooltip.visibility = View.GONE
                }
            }
            true
        }

        shipSet3Image.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    shipSet3Tooltip.visibility = View.VISIBLE
                    shipSet3Tooltip.text = "Ship Set 3 Upgrades:\n" +
                            "+20% Speed\n" +
                            "+20% Bullet Speed\n" +
                            "+2 Homing Missiles\n" +
                            "+100 HP"
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    shipSet3Tooltip.visibility = View.GONE
                }
            }
            true
        }

        unlockShipSet2Button.setOnClickListener {
            if (gameEngine.starsCollected >= 20 && gameEngine.level >= 20) {
                gameEngine.starsCollected -= 20
                gameEngine.unlockShipSet(1)
                updateUI()
                Timber.d("Purchased Ship Set 2 for 20 stars")
            } else {
                Timber.d("Cannot purchase Ship Set 2: stars=${gameEngine.starsCollected}, level=${gameEngine.level}")
            }
        }

        unlockShipSet3Button.setOnClickListener {
            if (gameEngine.starsCollected >= 40 && gameEngine.level >= 40) {
                gameEngine.starsCollected -= 40
                gameEngine.unlockShipSet(2)
                updateUI()
                Timber.d("Purchased Ship Set 3 for 40 stars")
            } else {
                Timber.d("Cannot purchase Ship Set 3: stars=${gameEngine.starsCollected}, level=${gameEngine.level}")
            }
        }

        buyReviveButton.setOnClickListener {
            if (gameEngine.starsCollected >= 10 && gameEngine.reviveCount < 3) {
                gameEngine.starsCollected -= 10
                gameEngine.reviveCount += 1
                updateUI()
                Timber.d("Purchased a revive for 10 stars. Total revives: ${gameEngine.reviveCount}")
            } else {
                Timber.d("Cannot buy revive: stars=${gameEngine.starsCollected}, reviveCount=${gameEngine.reviveCount}")
            }
        }

        buyDestroyAllButton.setOnClickListener {
            if (gameEngine.starsCollected >= 25 && gameEngine.destroyAllCharges < 3) {
                gameEngine.starsCollected -= 25
                gameEngine.destroyAllCharges += 1
                updateUI()
                Timber.d("Purchased a Destroy All charge for 25 stars. Total charges: ${gameEngine.destroyAllCharges}")
            } else {
                Timber.d("Cannot buy Destroy All charge: stars=${gameEngine.starsCollected}, charges=${gameEngine.destroyAllCharges}")
            }
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun updateUI() {
        starsCollectedText.text = "Stars Collected: ${gameEngine.starsCollected}"
        reviveCountText.text = "Revives: ${gameEngine.reviveCount}/3"
        destroyAllChargesText.text = "Destroy All Charges: ${gameEngine.destroyAllCharges}/3"

        val unlockedSets = gameEngine.getUnlockedShipSets()
        unlockShipSet2Button.isEnabled = gameEngine.level >= 20 && gameEngine.starsCollected >= 20 && 1 !in unlockedSets
        unlockShipSet3Button.isEnabled = gameEngine.level >= 40 && gameEngine.starsCollected >= 40 && 2 !in unlockedSets
        buyReviveButton.isEnabled = gameEngine.starsCollected >= 10 && gameEngine.reviveCount < 3
        buyDestroyAllButton.isEnabled = gameEngine.starsCollected >= 25 && gameEngine.destroyAllCharges < 3
    }
}