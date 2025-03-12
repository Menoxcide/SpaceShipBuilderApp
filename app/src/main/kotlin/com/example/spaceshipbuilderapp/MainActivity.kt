package com.example.spaceshipbuilderapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false
    private var draggedPartType: String? = null
    private val audioPermissionCode = 100
    @Inject lateinit var highscoreManager: HighscoreManager
    private lateinit var buildView: BuildView
    private lateinit var flightView: FlightView
    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            buildView.renderer.updateAnimationFrame()
            flightView.renderer.updateAnimationFrame()
            if (buildView.visibility == View.VISIBLE) buildView.invalidate()
            if (flightView.visibility == View.VISIBLE) flightView.invalidate()
            handler.postDelayed(this, 16)
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

        buildView.post {
            partButtons = mapOf(
                binding.cockpitImage to Pair("cockpit", buildView.renderer.cockpitBitmap),
                binding.fuelTankImage to Pair("fuel_tank", buildView.renderer.fuelTankBitmap),
                binding.engineImage to Pair("engine", buildView.renderer.engineBitmap)
            )
            setupListeners()
        }

        buildView.setOnTouchListener(placedPartTouchListener)

        binding.launchButton.visibility = View.GONE
        binding.playerNameInput.visibility = View.VISIBLE // Show name input in BUILD mode

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        gameEngine.setScreenDimensions(screenWidth, screenHeight)

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
            binding.playerNameInput.visibility = if (isLaunching) View.GONE else View.VISIBLE // Toggle name input
            if (isLaunching) {
                gameEngine.playerName = binding.playerNameInput.text.toString().trim().ifEmpty { "Player" } // Set name before flight
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

        handler.post(animationRunnable)
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
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
        val prevButton = dialog.findViewById<Button>(R.id.prevButton) ?: Button(this).apply {
            id = R.id.prevButton
            text = "Previous"
            dialog.findViewById<android.widget.LinearLayout>(android.R.id.content)?.addView(this)
        }
        val nextButton = dialog.findViewById<Button>(R.id.nextButton) ?: Button(this).apply {
            id = R.id.nextButton
            text = "Next"
            dialog.findViewById<android.widget.LinearLayout>(android.R.id.content)?.addView(this)
        }

        var currentPage = 0
        val pageSize = 10
        val totalPages = highscoreManager.getTotalPages(pageSize)

        fun updateLeaderboardText() {
            val scores = highscoreManager.getHighscores(currentPage, pageSize)
            val sb = StringBuilder("Leaderboard (Page ${currentPage + 1}/$totalPages):\n")
            scores.forEachIndexed { index, entry ->
                sb.append("${currentPage * pageSize + index + 1}. ${entry.name}: ${entry.score}\n")
            }
            highscoreText.text = sb.toString()
            prevButton.isEnabled = currentPage > 0
            nextButton.isEnabled = currentPage < totalPages - 1
        }

        updateLeaderboardText()

        prevButton.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updateLeaderboardText()
            }
        }

        nextButton.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updateLeaderboardText()
            }
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(animationRunnable)
        gameEngine.onDestroy()
        if (BuildConfig.DEBUG) Timber.d("Activity destroyed")
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