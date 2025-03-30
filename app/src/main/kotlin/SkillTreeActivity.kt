package com.example.spaceshipbuilderapp

import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class SkillTreeActivity : AppCompatActivity() {
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var skillManager: SkillManager
    @Inject lateinit var audioManager: AudioManager

    private lateinit var entriesContainer: LinearLayout
    private lateinit var skillPointsText: TextView
    private lateinit var experienceText: TextView
    private lateinit var buySkillPointButton: Button
    private lateinit var bonusesText: TextView
    private val skillViews = mutableMapOf<String, SkillViewHolder>()

    data class SkillViewHolder(
        val descriptionText: TextView,
        val progressBar: ProgressBar,
        val upgradeButton: Button
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skill_tree)

        entriesContainer = findViewById(R.id.skillTreeEntries)
        skillPointsText = findViewById(R.id.skillPointsText)
        experienceText = findViewById(R.id.experienceText)
        buySkillPointButton = findViewById(R.id.buySkillPointButton)
        bonusesText = findViewById(R.id.bonusesText)

        // Fetch latest experience from Firebase
        CoroutineScope(Dispatchers.Main).launch {
            try {
                gameEngine.loadUserData(gameEngine.getUserId())
                updateExperienceDisplay()
                Timber.d("Experience refreshed from Firebase: ${gameEngine.experience}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh experience: ${e.message}")
                Toast.makeText(this@SkillTreeActivity, "Failed to load experience", Toast.LENGTH_SHORT).show()
            }
        }

        populateSkillTree()
        updateSkillPointsDisplay()
        updateBonusesDisplay()

        buySkillPointButton.setOnClickListener {
            if (skillManager.buySkillPoint(gameEngine.experience)) {
                gameEngine.experience -= 20000L
                updateSkillPointsDisplay()
                updateExperienceDisplay()
                audioManager.playPowerUpSound()
                Toast.makeText(this, "Skill Point Purchased!", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.Main).launch {
                    gameEngine.savePersistentData(gameEngine.getUserId())
                }
                // Refresh skill tree to update button states
                populateSkillTree()
            } else {
                Toast.makeText(this, "Not enough experience! Need 20,000 XP.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        audioManager.playDialogOpenSound()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun updateSkillPointsDisplay() {
        skillPointsText.text = "Skill Points: ${skillManager.skillPoints}"
    }

    private fun updateExperienceDisplay() {
        experienceText.text = "Experience: ${gameEngine.experience}"
    }

    private fun updateBonusesDisplay() {
        val bonuses = mutableListOf<String>()
        val skills = skillManager.skills

        val projectileDamageLevel = skills["projectile_damage"] ?: 0
        if (projectileDamageLevel > 0) {
            val bonus = projectileDamageLevel * 10
            bonuses.add("Projectile Damage: +$bonus%")
        }

        val firingRateLevel = skills["firing_rate"] ?: 0
        if (firingRateLevel > 0) {
            val bonus = firingRateLevel * 5
            bonuses.add("Firing Rate: +$bonus%")
        }

        val homingMissilesLevel = skills["homing_missiles"] ?: 0
        if (homingMissilesLevel > 0) {
            bonuses.add("Homing Missiles: +$homingMissilesLevel")
        }

        val maxHpLevel = skills["max_hp"] ?: 0
        if (maxHpLevel > 0) {
            val bonus = maxHpLevel * 10
            bonuses.add("Max HP: +$bonus%")
        }

        val hpRegenerationLevel = skills["hp_regeneration"] ?: 0
        if (hpRegenerationLevel > 0) {
            bonuses.add("HP Regeneration: +$hpRegenerationLevel HP/s")
        }

        val shieldStrengthLevel = skills["shield_strength"] ?: 0
        if (shieldStrengthLevel > 0) {
            val bonus = shieldStrengthLevel * 20
            bonuses.add("Shield Strength: +$bonus%")
        }

        val speedBoostLevel = skills["speed_boost"] ?: 0
        if (speedBoostLevel > 0) {
            val bonus = speedBoostLevel * 5
            bonuses.add("Speed: +$bonus%")
        }

        val fuelEfficiencyLevel = skills["fuel_efficiency"] ?: 0
        if (fuelEfficiencyLevel > 0) {
            val bonus = fuelEfficiencyLevel * 5
            bonuses.add("Fuel Efficiency: -$bonus%")
        }

        val powerUpDurationLevel = skills["power_up_duration"] ?: 0
        if (powerUpDurationLevel > 0) {
            val bonus = powerUpDurationLevel * 10
            bonuses.add("Power-Up Duration: +$bonus%")
        }

        bonusesText.text = if (bonuses.isEmpty()) "No Bonuses Unlocked" else "Current Bonuses:\n${bonuses.joinToString("\n")}"
    }

    private fun populateSkillTree() {
        entriesContainer.removeAllViews()
        skillViews.clear()

        val categories = mapOf(
            "Combat" to listOf("projectile_damage", "firing_rate", "homing_missiles"),
            "Engineering" to listOf("max_hp", "hp_regeneration", "shield_strength"),
            "Exploration" to listOf("speed_boost", "fuel_efficiency", "power_up_duration")
        )

        val descriptions = mapOf(
            "projectile_damage" to "Increases projectile damage by 10% per level",
            "firing_rate" to "Increases firing rate by 5% per level",
            "homing_missiles" to "Adds 1 homing missile per level",
            "speed_boost" to "Increases speed by 5% per level",
            "fuel_efficiency" to "Reduces fuel consumption by 5% per level",
            "power_up_duration" to "Increases power-up duration by 10% per level",
            "max_hp" to "Increases max HP by 10% per level",
            "hp_regeneration" to "Regenerates 1 HP per second per level",
            "shield_strength" to "Increases shield effectiveness by 20% per level"
        )

        categories.entries.forEachIndexed { index, (category, skillIds) ->
            val categoryLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(if (index % 2 == 0) Color.parseColor("#2A2A2A") else Color.parseColor("#1A1A1A"))
            }

            val categoryText = TextView(this).apply {
                text = category.uppercase()
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 8)
                setBackgroundColor(Color.argb(50, 255, 255, 255))
            }
            categoryLayout.addView(categoryText)

            skillIds.forEach { skillId ->
                val skillLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 8, 16, 8)
                }

                val currentLevel = skillManager.skills[skillId] ?: 0
                val maxLevel = skillManager.skillMaxLevels[skillId] ?: 3
                val cost = if (currentLevel < maxLevel) skillManager.getUpgradeCost(skillId) else 0

                val descriptionLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, 0)
                }

                val descriptionText = TextView(this).apply {
                    val fullText = "${descriptions[skillId]}\nLevel: $currentLevel/$maxLevel\nCost: ${if (currentLevel < maxLevel) "$cost Skill Point${if (cost > 1) "s" else ""}" else "Maxed"}"
                    text = fullText
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@SkillTreeActivity, android.R.color.white))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        weight = 1f
                        setMargins(0, 0, 8, 0) // Add margin to separate from button
                    }
                    setMinLines(3) // Ensure space for 3 lines
                    maxLines = 3
                    ellipsize = null
                    Timber.d("Setting description for $skillId: $fullText")
                }

                val upgradeButton = Button(this).apply {
                    text = if (currentLevel >= maxLevel) "MAX" else "UPGRADE"
                    isEnabled = currentLevel < maxLevel && skillManager.skillPoints >= cost
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        width = 150 // Fixed width to prevent squeezing descriptionText
                    }
                    setBackgroundTintList(ContextCompat.getColorStateList(
                        this@SkillTreeActivity,
                        if (currentLevel >= maxLevel) android.R.color.holo_green_dark
                        else if (skillManager.skillPoints >= cost) android.R.color.holo_blue_light
                        else android.R.color.darker_gray
                    ))
                    setTextColor(ContextCompat.getColor(this@SkillTreeActivity, android.R.color.white))
                    setOnClickListener {
                        if (gameEngine.upgradeSkill(skillId)) {
                            updateSkillPointsDisplay()
                            updateSkillView(skillId)
                            updateBonusesDisplay()
                            animateSkillUpgrade(skillLayout)
                            audioManager.playPowerUpSound()
                            CoroutineScope(Dispatchers.Main).launch {
                                gameEngine.savePersistentData(gameEngine.getUserId())
                            }
                        } else {
                            Toast.makeText(this@SkillTreeActivity, "Not enough skill points!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 4)
                    }
                    max = maxLevel * 100
                    progress = currentLevel * 100
                    progressTintList = ContextCompat.getColorStateList(this@SkillTreeActivity, android.R.color.holo_blue_light)
                }

                descriptionLayout.addView(descriptionText)
                descriptionLayout.addView(upgradeButton)
                skillLayout.addView(descriptionLayout)
                skillLayout.addView(progressBar)
                categoryLayout.addView(skillLayout)

                skillViews[skillId] = SkillViewHolder(descriptionText, progressBar, upgradeButton)
            }

            entriesContainer.addView(categoryLayout)
        }
    }

    private fun updateSkillView(skillId: String) {
        val viewHolder = skillViews[skillId] ?: return
        val currentLevel = skillManager.skills[skillId] ?: 0
        val maxLevel = skillManager.skillMaxLevels[skillId] ?: 3
        val cost = if (currentLevel < maxLevel) skillManager.getUpgradeCost(skillId) else 0

        val fullText = "${viewHolder.descriptionText.text.split("\n")[0]}\nLevel: $currentLevel/$maxLevel\nCost: ${if (currentLevel < maxLevel) "$cost Skill Point${if (cost > 1) "s" else ""}" else "Maxed"}"
        viewHolder.descriptionText.text = fullText
        Timber.d("Updating description for $skillId: $fullText")
        viewHolder.progressBar.progress = currentLevel * 100
        viewHolder.upgradeButton.text = if (currentLevel >= maxLevel) "MAX" else "UPGRADE"
        viewHolder.upgradeButton.isEnabled = currentLevel < maxLevel && skillManager.skillPoints >= cost
        viewHolder.upgradeButton.setBackgroundTintList(ContextCompat.getColorStateList(
            this,
            if (currentLevel >= maxLevel) android.R.color.holo_green_dark
            else if (skillManager.skillPoints >= cost) android.R.color.holo_blue_light
            else android.R.color.darker_gray
        ))
    }

    private fun animateSkillUpgrade(view: View) {
        val scaleAnimation = ScaleAnimation(
            1f, 1.1f, 1f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
            repeatCount = 1
            repeatMode = Animation.REVERSE
        }
        view.startAnimation(scaleAnimation)
    }
}