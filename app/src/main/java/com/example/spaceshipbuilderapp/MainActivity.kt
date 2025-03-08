package com.example.spaceshipbuilderapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private lateinit var voiceCommandHandler: VoiceCommandHandler
    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false
    private lateinit var selectionPanel: LinearLayout
    private val AUDIO_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request audio permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            gameView.setStatusBarHeight(statusBarHeight)
            WindowInsetsCompat.CONSUMED
        }

        gameView = findViewById(R.id.gameView)
        selectionPanel = findViewById(R.id.selectionPanel)
        val launchButton = findViewById<Button>(R.id.launchButton)
        val leaderboardButton = findViewById<Button>(R.id.leaderboardButton)
        val cockpitButton = findViewById<ImageButton>(R.id.cockpitImage)
        val fuelTankButton = findViewById<ImageButton>(R.id.fuelTankImage)
        val engineButton = findViewById<ImageButton>(R.id.engineImage)

        voiceCommandHandler = VoiceCommandHandler(this) { command ->
            when (command.lowercase()) {
                "launch ship" -> gameView.launchShip()
                "rotate engine" -> gameView.rotatePart("engine")
                "rotate cockpit" -> gameView.rotatePart("cockpit")
                "rotate fuel tank" -> gameView.rotatePart("fuel_tank")
            }
        }

        // Start listening only if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceCommandHandler.startListening()
        }

        gameView.setLaunchListener { isLaunching ->
            if (isLaunching) {
                selectionPanel.visibility = View.GONE
            } else {
                selectionPanel.visibility = View.VISIBLE
            }
        }

        launchButton.setOnClickListener {
            Log.d("MainActivity", "Launch button clicked")
            gameView.launchShip()
        }

        leaderboardButton.setOnClickListener {
            gameView.showLeaderboard()
        }

        cockpitButton.setOnTouchListener { view, event ->
            handlePartTouch(event, view, "cockpit", gameView.cockpitBitmap)
        }
        fuelTankButton.setOnTouchListener { view, event ->
            handlePartTouch(event, view, "fuel_tank", gameView.fuelTankBitmap)
        }
        engineButton.setOnTouchListener { view, event ->
            handlePartTouch(event, view, "engine", gameView.engineBitmap)
        }

        Log.d("MainActivity", "View layout initialized")
    }

    private fun handlePartTouch(event: MotionEvent, view: View, partType: String, bitmap: Bitmap): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                initialX = location[0].toFloat() + (view.width / 2f)
                initialY = location[1].toFloat() + (view.height / 2f)
                isDragging = true
                gameView.setSelectedPart(GameView.Part(partType, bitmap, initialX, initialY, 0f))
                view.visibility = View.INVISIBLE
                Log.d("MainActivity", "$partType selected at (x=$initialX, y=$initialY)")
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val x = event.rawX
                    val y = event.rawY
                    gameView.setSelectedPart(GameView.Part(partType, bitmap, x, y, 0f))
                    Log.d("MainActivity", "Dragging $partType to (x=$x, y=$y)")
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val x = event.rawX
                    val y = event.rawY
                    gameView.setSelectedPart(GameView.Part(partType, bitmap, x, y, 0f))
                    isDragging = false
                    view.visibility = View.VISIBLE
                    Log.d("MainActivity", "Dropped $partType at (x=$x, y=$y)")
                }
                return true
            }
        }
        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        Log.d("MainActivity", "Touch event dispatched to GameView: ${event.action}")
        gameView.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voiceCommandHandler.startListening()
                Log.d("MainActivity", "Audio permission granted, starting voice recognition")
            } else {
                Log.w("MainActivity", "Audio permission denied. Voice commands disabled.")
                // Optionally notify user via Toast or UI
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceCommandHandler.stopListening()
    }
}