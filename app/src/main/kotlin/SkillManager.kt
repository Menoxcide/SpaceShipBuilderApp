package com.example.spaceshipbuilderapp

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillManager @Inject constructor() {
    var skillPoints: Int = 0
        set(value) {
            field = value
            Timber.d("Skill points updated to $value")
        }

    val skills: MutableMap<String, Int> = mutableMapOf(
        "projectile_damage" to 0, "firing_rate" to 0, "homing_missiles" to 0,
        "speed_boost" to 0, "fuel_efficiency" to 0, "power_up_duration" to 0,
        "max_hp" to 0, "hp_regeneration" to 0, "shield_strength" to 0
    )

    val skillMaxLevels: Map<String, Int> = mapOf(
        "projectile_damage" to 3, "firing_rate" to 3, "homing_missiles" to 3,
        "speed_boost" to 3, "fuel_efficiency" to 3, "power_up_duration" to 3,
        "max_hp" to 3, "hp_regeneration" to 3, "shield_strength" to 3
    )

    // Base costs for level 1; cost increases by 1 per level
    private val skillBaseCosts: Map<String, Int> = mapOf(
        "projectile_damage" to 1, "firing_rate" to 1, "homing_missiles" to 2,
        "speed_boost" to 1, "fuel_efficiency" to 1, "power_up_duration" to 2,
        "max_hp" to 1, "hp_regeneration" to 2, "shield_strength" to 2
    )

    fun getUpgradeCost(skillId: String): Int {
        val currentLevel = skills[skillId] ?: 0
        val baseCost = skillBaseCosts[skillId] ?: 1
        return baseCost + currentLevel // Cost increases with each level
    }

    fun upgradeSkill(skillId: String): Boolean {
        val currentLevel = skills[skillId] ?: 0
        val maxLevel = skillMaxLevels[skillId] ?: return false
        val cost = getUpgradeCost(skillId)
        if (currentLevel < maxLevel && skillPoints >= cost) {
            skills[skillId] = currentLevel + 1
            skillPoints -= cost
            Timber.d("Upgraded skill $skillId to level ${skills[skillId]} for $cost skill points")
            return true
        }
        Timber.d("Cannot upgrade $skillId: level=$currentLevel/$maxLevel, points=$skillPoints, cost=$cost")
        return false
    }

    fun buySkillPoint(experience: Long): Boolean {
        val skillPointCost = 20000L // 20,000 XP per skill point
        if (experience >= skillPointCost) {
            skillPoints++
            Timber.d("Bought skill point, new total: $skillPoints")
            return true
        }
        Timber.d("Not enough experience to buy skill point: $experience < $skillPointCost")
        return false
    }
}