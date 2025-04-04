package com.example.spaceshipbuilderapp

sealed class WeaponType(val projectileDrawableId: Int) {
    object Default : WeaponType(R.drawable.projectile_default) // Replace with actual default drawable if different
    object Plasma : WeaponType(R.drawable.ship_plasma)
    object Missile : WeaponType(R.drawable.ship_missile)
    object HomingMissile : WeaponType(R.drawable.homing_missile)
    object Laser : WeaponType(R.drawable.ship_laser)
}