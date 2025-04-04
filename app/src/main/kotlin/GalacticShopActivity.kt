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
    @Inject lateinit var audioManager: AudioManager

    private lateinit var starsCollectedText: TextView
    private lateinit var unlockShipSet2Button: Button
    private lateinit var unlockShipSet3Button: Button
    private lateinit var buyReviveButton: Button
    private lateinit var buyDestroyAllButton: Button
    private lateinit var buySpeedBoostExtenderButton: Button
    private lateinit var buyMissileSlotButton: Button
    private lateinit var buyFuelTankUpgradeButton: Button
    private lateinit var reviveCountText: TextView
    private lateinit var destroyAllChargesText: TextView
    private lateinit var speedBoostExtenderStatus: TextView
    private lateinit var missileSlotCountText: TextView
    private lateinit var fuelTankUpgradeStatus: TextView
    private lateinit var shipSet2Image: ImageView
    private lateinit var shipSet3Image: ImageView
    private lateinit var shipSet2Tooltip: TextView
    private lateinit var shipSet3Tooltip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_galactic_shop)

        // Initialize views
        starsCollectedText = findViewById(R.id.starsCollectedText)
        unlockShipSet2Button = findViewById(R.id.unlockShipSet2Button)
        unlockShipSet3Button = findViewById(R.id.unlockShipSet3Button)
        buyReviveButton = findViewById(R.id.buyReviveButton)
        buyDestroyAllButton = findViewById(R.id.buyDestroyAllButton)
        buySpeedBoostExtenderButton = findViewById(R.id.buySpeedBoostExtenderButton)
        buyMissileSlotButton = findViewById(R.id.buyMissileSlotButton)
        buyFuelTankUpgradeButton = findViewById(R.id.buyFuelTankUpgradeButton)
        reviveCountText = findViewById(R.id.reviveCountText)
        destroyAllChargesText = findViewById(R.id.destroyAllChargesText)
        speedBoostExtenderStatus = findViewById(R.id.speedBoostExtenderStatus)
        missileSlotCountText = findViewById(R.id.missileSlotCountText)
        fuelTankUpgradeStatus = findViewById(R.id.fuelTankUpgradeStatus)
        shipSet2Image = findViewById(R.id.shipSet2Image)
        shipSet3Image = findViewById(R.id.shipSet3Image)
        shipSet2Tooltip = findViewById(R.id.shipSet2Tooltip)
        shipSet3Tooltip = findViewById(R.id.shipSet3Tooltip)

        updateUI()

        // Ship Set 2 Tooltip
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

        // Ship Set 3 Tooltip
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

        // Unlock Ship Set 2
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

        // Unlock Ship Set 3
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

        // Buy Revive
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

        // Buy Destroy All Charge
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

        // Buy Speed Boost Extender
        buySpeedBoostExtenderButton.setOnClickListener {
            if (gameEngine.starsCollected >= 30 && gameEngine.level >= 25 && !gameEngine.speedBoostExtended) {
                gameEngine.starsCollected -= 30
                gameEngine.speedBoostExtended = true
                updateUI()
                Timber.d("Purchased Speed Boost Extender for 30 stars. New duration: ${gameEngine.flightModeManager.powerUpManager.effectDuration}")
            } else {
                Timber.d("Cannot buy Speed Boost Extender: stars=${gameEngine.starsCollected}, level=${gameEngine.level}, alreadyPurchased=${gameEngine.speedBoostExtended}")
            }
        }

        // Buy Extra Missile Slot
        buyMissileSlotButton.setOnClickListener {
            if (gameEngine.starsCollected >= 35 && gameEngine.level >= 30 && gameEngine.extraMissileSlots < 2) {
                gameEngine.starsCollected -= 35
                gameEngine.extraMissileSlots += 1
                updateUI()
                Timber.d("Purchased Extra Missile Slot for 35 stars. Total extra slots: ${gameEngine.extraMissileSlots}, maxMissiles=${gameEngine.flightModeManager.shipManager.maxMissiles}")
            } else {
                Timber.d("Cannot buy Extra Missile Slot: stars=${gameEngine.starsCollected}, level=${gameEngine.level}, extraSlots=${gameEngine.extraMissileSlots}")
            }
        }

        // Buy Fuel Tank Upgrade
        buyFuelTankUpgradeButton.setOnClickListener {
            if (gameEngine.starsCollected >= 20 && gameEngine.level >= 15 && !gameEngine.fuelTankUpgraded) {
                gameEngine.starsCollected -= 20
                gameEngine.fuelTankUpgraded = true
                updateUI()
                Timber.d("Purchased Fuel Tank Upgrade for 20 stars. New fuel capacity: ${gameEngine.flightModeManager.shipManager.fuelCapacity}")
            } else {
                Timber.d("Cannot buy Fuel Tank Upgrade: stars=${gameEngine.starsCollected}, level=${gameEngine.level}, alreadyPurchased=${gameEngine.fuelTankUpgraded}")
            }
        }

        // Back Button
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        audioManager.playDialogOpenSound()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun updateUI() {
        starsCollectedText.text = "Stars Collected: ${gameEngine.starsCollected}"
        reviveCountText.text = "Revives: ${gameEngine.reviveCount}/3"
        destroyAllChargesText.text = "Destroy All Charges: ${gameEngine.destroyAllCharges}/3"
        speedBoostExtenderStatus.text = if (gameEngine.speedBoostExtended) "Purchased" else "Not Purchased"
        missileSlotCountText.text = "Extra Slots: ${gameEngine.extraMissileSlots}/2"
        fuelTankUpgradeStatus.text = if (gameEngine.fuelTankUpgraded) "Purchased" else "Not Purchased"

        val unlockedSets = gameEngine.getUnlockedShipSets()
        unlockShipSet2Button.isEnabled = gameEngine.level >= 20 && gameEngine.starsCollected >= 20 && 1 !in unlockedSets
        unlockShipSet3Button.isEnabled = gameEngine.level >= 40 && gameEngine.starsCollected >= 40 && 2 !in unlockedSets
        buyReviveButton.isEnabled = gameEngine.starsCollected >= 10 && gameEngine.reviveCount < 3
        buyDestroyAllButton.isEnabled = gameEngine.starsCollected >= 25 && gameEngine.destroyAllCharges < 3
        buySpeedBoostExtenderButton.isEnabled = gameEngine.level >= 25 && gameEngine.starsCollected >= 30 && !gameEngine.speedBoostExtended
        buyMissileSlotButton.isEnabled = gameEngine.level >= 30 && gameEngine.starsCollected >= 35 && gameEngine.extraMissileSlots < 2
        buyFuelTankUpgradeButton.isEnabled = gameEngine.level >= 15 && gameEngine.starsCollected >= 20 && !gameEngine.fuelTankUpgraded
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}