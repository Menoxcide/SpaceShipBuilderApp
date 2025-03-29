package com.example.spaceshipbuilderapp

import android.graphics.Bitmap

data class Part(val type: String, val bitmap: Bitmap, var x: Float, var y: Float, var rotation: Float, var scale: Float = 1f)