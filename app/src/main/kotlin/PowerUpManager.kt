package com.example.spaceshipbuilderapp

import timber.log.Timber
import javax.inject.Inject

class PowerUpManager @Inject constructor() {
    var shieldActive = false
    private var shieldEndTime = 0L
    var speedBoostActive = false
    private var speedBoostEndTime = 0L
    var stealthActive = false
    private var stealthEndTime = 0L
    var invincibilityActive = false
    private var invincibilityEndTime = 0L

    // Make effectDuration a variable with a default base value, accessible publicly
    var effectDuration = 10000L // Base duration of 10 seconds

    fun applyPowerUpEffect(powerUpType: String, shipManager: ShipManager) {
        val currentTime = System.currentTimeMillis()
        when (powerUpType) {
            "shield" -> {
                shieldActive = true
                shieldEndTime = currentTime + effectDuration
                shipManager.currentFuelConsumption = shipManager.currentFuelConsumption / 2f
                Timber.d("Collected shield power-up, duration: $effectDuration ms")
            }
            "speed" -> {
                speedBoostActive = true
                speedBoostEndTime = currentTime + effectDuration
                shipManager.currentSpeed = shipManager.currentSpeed * 5f
                Timber.d("Collected speed boost power-up, duration: $effectDuration ms")
            }
            "stealth" -> {
                stealthActive = true
                stealthEndTime = currentTime + effectDuration
                Timber.d("Collected stealth power-up, duration: $effectDuration ms")
            }
            "invincibility" -> {
                invincibilityActive = true
                invincibilityEndTime = currentTime + effectDuration
                Timber.d("Collected invincibility power-up, duration: $effectDuration ms")
            }
        }
    }

    fun updatePowerUpEffects(shipManager: ShipManager) {
        val currentTime = System.currentTimeMillis()
        if (shieldActive && currentTime > shieldEndTime) {
            shieldActive = false
            shipManager.currentFuelConsumption = shipManager.baseFuelConsumption
            Timber.d("Shield effect ended")
        }
        if (speedBoostActive && currentTime > speedBoostEndTime) {
            speedBoostActive = false
            shipManager.currentSpeed = shipManager.baseSpeed
            Timber.d("Speed boost ended")
        }
        if (stealthActive && currentTime > stealthEndTime) {
            stealthActive = false
            Timber.d("Stealth effect ended")
        }
        if (invincibilityActive && currentTime > invincibilityEndTime) {
            invincibilityActive = false
            Timber.d("Invincibility ended")
        }
    }

    fun resetPowerUpEffects() {
        shieldActive = false
        speedBoostActive = false
        stealthActive = false
        invincibilityActive = false
        Timber.d("Power-up effects reset")
    }

    // Methods to get and set end times for state restoration
    fun getShieldEndTime(): Long = shieldEndTime
    fun setShieldEndTime(time: Long) {
        shieldEndTime = time
    }

    fun getSpeedBoostEndTime(): Long = speedBoostEndTime
    fun setSpeedBoostEndTime(time: Long) {
        speedBoostEndTime = time
    }

    fun getStealthEndTime(): Long = stealthEndTime
    fun setStealthEndTime(time: Long) {
        stealthEndTime = time
    }

    fun getInvincibilityEndTime(): Long = invincibilityEndTime
    fun setInvincibilityEndTime(time: Long) {
        invincibilityEndTime = time
    }
}