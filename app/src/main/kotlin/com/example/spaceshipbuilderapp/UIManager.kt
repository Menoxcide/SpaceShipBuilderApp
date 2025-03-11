package com.example.spaceshipbuilderapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class UIManager(private val context: Context) {
    companion object {
        const val BUTTON_WIDTH = 200f
        const val BUTTON_HEIGHT = 50f
        const val TEXT_SIZE = 24f
        const val PADDING = 20f
    }

    private val shopButtonPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val shopTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = TEXT_SIZE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

}