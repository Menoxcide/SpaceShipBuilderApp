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

    var effectDuration = 10000L // Base duration of 10 seconds

    fun applyPowerUpEffect(powerUpType: String, shipManager: ShipManager) {
        val currentTime = System.currentTimeMillis()
        when (powerUpType) {
            "shield" -> {
                if (!shieldActive) {
                    // Apply effect only if not already active
                    shieldActive = true
                    shipManager.currentFuelConsumption = shipManager.baseFuelConsumption / 2f
                    Timber.d("Applied shield power-up effect, fuel consumption halved to ${shipManager.currentFuelConsumption}")
                }
                // Refresh or set duration
                shieldEndTime = currentTime + effectDuration
                Timber.d("Collected shield power-up, duration refreshed to $effectDuration ms, endTime=$shieldEndTime")
            }
            "speed" -> {
                if (!speedBoostActive) {
                    // Apply effect only if not already active
                    speedBoostActive = true
                    shipManager.currentSpeed = shipManager.baseSpeed * 5f
                    Timber.d("Applied speed boost power-up effect, speed increased to ${shipManager.currentSpeed}")
                }
                // Refresh or set duration
                speedBoostEndTime = currentTime + effectDuration
                Timber.d("Collected speed boost power-up, duration refreshed to $effectDuration ms, endTime=$speedBoostEndTime")
            }
            "stealth" -> {
                if (!stealthActive) {
                    // Apply effect only if not already active
                    stealthActive = true
                    Timber.d("Applied stealth power-up effect")
                }
                // Refresh or set duration
                stealthEndTime = currentTime + effectDuration
                Timber.d("Collected stealth power-up, duration refreshed to $effectDuration ms, endTime=$stealthEndTime")
            }
            "invincibility" -> {
                if (!invincibilityActive) {
                    // Apply effect only if not already active
                    invincibilityActive = true
                    Timber.d("Applied invincibility power-up effect")
                }
                // Refresh or set duration
                invincibilityEndTime = currentTime + effectDuration
                Timber.d("Collected invincibility power-up, duration refreshed to $effectDuration ms, endTime=$invincibilityEndTime")
            }
        }
    }

    fun updatePowerUpEffects(shipManager: ShipManager) {
        val currentTime = System.currentTimeMillis()
        if (shieldActive && currentTime > shieldEndTime) {
            shieldActive = false
            shipManager.currentFuelConsumption = shipManager.baseFuelConsumption
            Timber.d("Shield effect ended, fuel consumption reset to ${shipManager.currentFuelConsumption}")
        }
        if (speedBoostActive && currentTime > speedBoostEndTime) {
            speedBoostActive = false
            shipManager.currentSpeed = shipManager.baseSpeed
            Timber.d("Speed boost ended, speed reset to ${shipManager.currentSpeed}")
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

    // Methods to get remaining duration
    fun getShieldRemainingDuration(currentTime: Long): Long {
        return if (shieldActive) (shieldEndTime - currentTime).coerceAtLeast(0L) else 0L
    }

    fun getSpeedBoostRemainingDuration(currentTime: Long): Long {
        return if (speedBoostActive) (speedBoostEndTime - currentTime).coerceAtLeast(0L) else 0L
    }

    fun getStealthRemainingDuration(currentTime: Long): Long {
        return if (stealthActive) (stealthEndTime - currentTime).coerceAtLeast(0L) else 0L
    }

    fun getInvincibilityRemainingDuration(currentTime: Long): Long {
        return if (invincibilityActive) (invincibilityEndTime - currentTime).coerceAtLeast(0L) else 0L
    }
}