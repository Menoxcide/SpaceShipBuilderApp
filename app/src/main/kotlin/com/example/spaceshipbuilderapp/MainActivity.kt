package com.example.spaceshipbuilderapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.spaceshipbuilderapp.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import android.app.Dialog
import android.widget.Button
import android.widget.TextView
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceCommandHandler: VoiceCommandHandler
    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false
    private var draggedPartType: String? = null
    private val audioPermissionCode = 100
    private lateinit var highscoreManager: HighscoreManager
    private lateinit var buildView: BuildView
    private lateinit var flightView: FlightView
    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            buildView.renderer.updateAnimationFrame()
            flightView.renderer.updateAnimationFrame()
            if (buildView.visibility == View.VISIBLE) buildView.invalidate()
            if (flightView.visibility == View.VISIBLE) flightView.invalidate()
            handler.postDelayed(this, 16) // Approximately 60 FPS
        }
    }

    @Inject lateinit var gameEngine: GameEngine

    private lateinit var partButtons: Map<ImageButton, Pair<String, Bitmap>>

    private val partTouchListener = View.OnTouchListener { view, event ->
        if (gameState != GameState.BUILD) return@OnTouchListener false
        val (partType, bitmap) = partButtons[view as ImageButton] ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                initialX = location[0].toFloat() + (view.width / 2f)
                initialY = location[1].toFloat() + (view.height / 2f)
                isDragging = true
                draggedPartType = partType
                buildView.setSelectedPart(GameEngine.Part(partType, bitmap, initialX, initialY, 0f, 1f))
                if (BuildConfig.DEBUG) Timber.d("$partType selected at (x=$initialX, y=$initialY)")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val x = event.rawX
                    val y = event.rawY
                    buildView.setSelectedPart(GameEngine.Part(partType, bitmap, x, y, 0f, 1f))
                    if (BuildConfig.DEBUG) Timber.d("Dragging $partType to (x=$x, y=$y)")
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    draggedPartType = null
                    view.visibility = View.VISIBLE
                    view.performClick()
                    if (BuildConfig.DEBUG) Timber.d("Dropped $partType handling delegated to BuildView")
                }
                true
            }
            else -> false
        }
    }

    private val placedPartTouchListener = View.OnTouchListener { view, event ->
        if (gameState != GameState.BUILD) return@OnTouchListener false
        val part = buildView.gameEngine.parts.find { it.type == draggedPartType }
        if (part == null || !isDragging) return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                buildView.gameEngine.parts.remove(part)
                buildView.setSelectedPart(part)
                if (BuildConfig.DEBUG) Timber.d("Picked up placed $draggedPartType at (x=${part.x}, y=${part.y})")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.rawX
                val y = event.rawY
                buildView.setSelectedPart(GameEngine.Part(part.type, part.bitmap, x, y, part.rotation, part.scale))
                if (BuildConfig.DEBUG) Timber.d("Dragging placed $draggedPartType to (x=$x, y=$y)")
                true
            }
            MotionEvent.ACTION_UP -> {
                val x = event.rawX
                val y = event.rawY
                buildView.setSelectedPart(GameEngine.Part(part.type, part.bitmap, x, y, part.rotation, part.scale))
                isDragging = false
                draggedPartType = null
                view.performClick()
                if (BuildConfig.DEBUG) Timber.d("Dropped placed $part.type at (x=$x, y=$y)")
                true
            }
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG) Timber.d("View layout initialized")

        buildView = binding.buildView
        flightView = binding.flightView

        // Delay initialization of partButtons until after Hilt injection
        buildView.post {
            partButtons = mapOf(
                binding.cockpitImage to Pair("cockpit", buildView.renderer.cockpitBitmap),
                binding.fuelTankImage to Pair("fuel_tank", buildView.renderer.fuelTankBitmap),
                binding.engineImage to Pair("engine", buildView.renderer.engineBitmap)
            )
            setupListeners()
        }

        highscoreManager = HighscoreManager(this)
        setupVoiceCommandHandler()
        requestAudioPermission()

        buildView.setOnTouchListener(placedPartTouchListener)

        binding.launchButton.visibility = View.GONE

        // Set screen dimensions initially, but wait for statusBarHeight
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        gameEngine.setScreenDimensions(screenWidth, screenHeight)

        // Delay placeholder initialization until after statusBarHeight is set
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top.toFloat()
            binding.buildView.setStatusBarHeight(statusBarHeight)
            binding.flightView.setStatusBarHeight(statusBarHeight)
            gameEngine.setScreenDimensions(screenWidth, screenHeight, statusBarHeight)
            gameEngine.initializePlaceholders(
                screenWidth, screenHeight,
                gameEngine.renderer.cockpitPlaceholderBitmap,
                gameEngine.renderer.fuelTankPlaceholderBitmap,
                gameEngine.renderer.enginePlaceholderBitmap,
                statusBarHeight
            )
            gameEngine.parts.clear()
            Timber.d("Initialized with no parts after insets: cockpitY=${gameEngine.cockpitY}, fuelTankY=${gameEngine.fuelTankY}, engineY=${gameEngine.engineY}")
            setLaunchButtonVisibility(false)
            WindowInsetsCompat.CONSUMED
        }

        gameEngine.setLaunchListener { isLaunching ->
            setGameMode(if (isLaunching) GameState.FLIGHT else GameState.BUILD)
            binding.selectionPanel.visibility = if (isLaunching) View.GONE else View.VISIBLE
            binding.buildView.visibility = if (!isLaunching) View.VISIBLE else View.GONE
            binding.buildView.isEnabled = !isLaunching
            binding.flightView.visibility = if (isLaunching) View.VISIBLE else View.GONE
            binding.flightView.isEnabled = isLaunching
            if (isLaunching) {
                binding.flightView.requestFocus()
                binding.flightView.setGameMode(GameState.FLIGHT)
                binding.flightView.postInvalidate()
                if (BuildConfig.DEBUG) Timber.d("FlightView focused: ${binding.flightView.isFocused}, invalidated")
            } else {
                binding.buildView.setOnTouchListener(placedPartTouchListener)
                binding.flightView.setGameMode(GameState.BUILD)
                if (BuildConfig.DEBUG) Timber.d("BuildView focused: ${binding.buildView.isFocused}")
            }
            val isLaunchReady = gameEngine.isShipSpaceworthy(gameEngine.screenHeight) && gameEngine.parts.size == 3 && !isLaunching
            setLaunchButtonVisibility(isLaunchReady)
            if (BuildConfig.DEBUG) {
                Timber.d("Launch button visibility set to $isLaunchReady, isSpaceworthy=${gameEngine.isShipSpaceworthy(gameEngine.screenHeight)}, partsSize=${gameEngine.parts.size}, isLaunching=$isLaunching")
                if (!isLaunchReady) {
                    Timber.d("Spaceworthiness failure: ${gameEngine.getSpaceworthinessFailureReason()}")
                }
            }
        }

        // Start continuous star animation
        handler.post(animationRunnable)
    }

    private fun setupVoiceCommandHandler() {
        voiceCommandHandler = VoiceCommandHandler(this) { command ->
            if (command.startsWith("speech_error:")) {
                val errorMessage = command.substringAfter("speech_error:").trim()
                showToast(errorMessage, Toast.LENGTH_LONG)
            } else {
                processVoiceCommand(command.lowercase())
            }
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    private fun processVoiceCommand(command: String) {
        when (command) {
            "launch ship" -> {
                val success = binding.buildView.launchShip()
                if (!success) {
                    showToast(binding.buildView.gameEngine.getSpaceworthinessFailureReason(), Toast.LENGTH_LONG)
                }
            }
            "rotate engine" -> binding.buildView.rotatePart("engine")
            "rotate cockpit" -> binding.buildView.rotatePart("cockpit")
            "rotate fuel tank" -> binding.buildView.rotatePart("fuel_tank")
            else -> {
                if (BuildConfig.DEBUG) Timber.d("Unknown command: $command")
                showToast("Command '$command' not recognized. Try 'launch ship' or 'rotate [part]'.", Toast.LENGTH_SHORT)
            }
        }
    }

    private fun setupListeners() {
        binding.launchButton.setOnClickListener {
            if (BuildConfig.DEBUG) Timber.d("Launch button clicked")
            val success = binding.buildView.launchShip()
            if (!success) {
                showToast(binding.buildView.gameEngine.getSpaceworthinessFailureReason(), Toast.LENGTH_LONG)
            }
        }

        binding.leaderboardButton.setOnClickListener {
            if (BuildConfig.DEBUG) Timber.d("Leaderboard button clicked")
            showLeaderboard()
        }

        partButtons.forEach { (button, _) ->
            button.setOnTouchListener(partTouchListener)
        }

        binding.buildView.setOnTouchListener(placedPartTouchListener)
    }

    fun setSelectionPanelVisibility(isVisible: Boolean) {
        binding.selectionPanel.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    fun setLaunchButtonVisibility(isVisible: Boolean) {
        binding.launchButton.visibility = if (isVisible) View.VISIBLE else View.GONE
        if (BuildConfig.DEBUG) Timber.d("Launch button visibility explicitly set to $isVisible")
    }

    fun showLeaderboard() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_leaderboard)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val highscoreText = dialog.findViewById<TextView>(R.id.highscoreText)
        val closeButton = dialog.findViewById<Button>(R.id.closeButton)

        highscoreText.text = getString(
            R.string.leaderboard_text,
            highscoreManager.getHighscore()
        )

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun requestAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                voiceCommandHandler.startListening()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) -> {
                showToast("Audio permission is needed for voice commands.", Toast.LENGTH_LONG)
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionCode)
            }
            else -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionCode)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioPermissionCode && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voiceCommandHandler.startListening()
                showToast("Audio permission granted! Voice commands enabled.", Toast.LENGTH_SHORT)
                if (BuildConfig.DEBUG) Timber.d("Audio permission granted, voice recognition started")
            } else {
                showToast("Audio permission denied. Voice commands disabled.", Toast.LENGTH_LONG)
                if (BuildConfig.DEBUG) Timber.w("Audio permission denied")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceCommandHandler.isInitialized) voiceCommandHandler.stopListening()
        handler.removeCallbacks(animationRunnable)
        gameEngine.onDestroy()
        if (BuildConfig.DEBUG) Timber.d("Activity destroyed, voice command handler stopped")
    }

    private fun setGameMode(mode: GameState) {
        gameEngine.gameState = mode
        if (mode == GameState.FLIGHT) {
            binding.flightView.setGameMode(GameState.FLIGHT)
            binding.flightView.postInvalidate()
            Timber.d("Set game mode to FLIGHT, flightView visibility=${binding.flightView.visibility}")
        } else {
            binding.flightView.setGameMode(GameState.BUILD)
            binding.buildView.invalidate()
            Timber.d("Set game mode to BUILD, flightView visibility=${binding.flightView.visibility}")
        }
    }

    private val gameState: GameState
        get() = gameEngine.gameState
}