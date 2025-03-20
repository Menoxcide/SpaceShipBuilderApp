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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.spaceshipbuilderapp.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import timber.log.Timber
import android.app.Dialog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var buildView: BuildView
    private lateinit var flightView: FlightView
    private lateinit var voiceHandler: VoiceCommandHandler
    private lateinit var credentialManager: CredentialManager
    private lateinit var partButtons: Map<ImageButton, Pair<String, Bitmap>>
    private lateinit var googleSignInClient: GoogleSignInClient

    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false
    private var draggedPartType: String? = null
    private val audioPermissionCode = 100
    private var userId: String? = null

    @Inject lateinit var highscoreManager: HighscoreManager
    @Inject lateinit var gameEngine: GameEngine

    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            buildView.renderer.updateAnimationFrame()
            flightView.renderer.updateAnimationFrame()
            if (buildView.isVisible) buildView.invalidate()
            if (flightView.isVisible) flightView.invalidate()
            handler.postDelayed(this, 16)
        }
    }

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

        // Initialize Credential Manager
        credentialManager = CredentialManager.create(this)

        buildView = binding.buildView
        flightView = binding.flightView
        voiceHandler = VoiceCommandHandler(this) { /* Callback set later */ }

        binding.buildView.setOnTouchListener(placedPartTouchListener)
        binding.launchButton.isVisible = false
        binding.playerNameInput.isVisible = false

        handler.post(animationRunnable)
        requestAudioPermission()

        // Initialize game without sign-in
        initializeGame()
    }

    private fun initializeGame() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Use a default userId since sign-in is disabled
                userId = "default_user"
                Timber.d("Initializing game with default userId: $userId")
                gameEngine.loadUserData(userId!!)
                highscoreManager.initialize(userId!!)
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize user data")
                showToast(R.string.error_userdata, e.message ?: "Unknown error")
                // Continue with default values instead of retrying sign-in
                userId = "default_user"
                gameEngine.playerName = "Player"
            }

            buildView.post {
                partButtons = mapOf(
                    binding.cockpitImage to Pair("cockpit", buildView.renderer.cockpitBitmap),
                    binding.fuelTankImage to Pair("fuel_tank", buildView.renderer.fuelTankBitmap),
                    binding.engineImage to Pair("engine", buildView.renderer.engineBitmap)
                )
                setupListeners()
            }

            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            val screenHeight = resources.displayMetrics.heightPixels.toFloat()
            gameEngine.setScreenDimensions(screenWidth, screenHeight)

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top.toFloat()
                binding.buildView.setStatusBarHeight(statusBarHeight)
                binding.flightView.setStatusBarHeight(statusBarHeight)
                binding.flightView.setUserId(userId!!)
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
                binding.selectionPanel.isVisible = !isLaunching
                binding.buildView.isVisible = !isLaunching
                binding.buildView.isEnabled = !isLaunching
                binding.flightView.isVisible = isLaunching
                binding.flightView.isEnabled = isLaunching
                binding.playerNameInput.isVisible = false
                binding.leaderboardButton.isVisible = !isLaunching // Hide in flight mode
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
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioPermissionCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        } else {
            showToast(R.string.error_audio_permission)
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    private fun showToast(messageResId: Int, vararg args: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, getString(messageResId, *args), duration).show()
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

    fun setLaunchButtonVisibility(isVisible: Boolean) {
        binding.launchButton.isVisible = isVisible
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
            text = getString(R.string.prev_button)
            dialog.findViewById<android.widget.LinearLayout>(android.R.id.content)?.addView(this)
        }
        val nextButton = dialog.findViewById<Button>(R.id.nextButton) ?: Button(this).apply {
            id = R.id.nextButton
            text = getString(R.string.next_button)
            dialog.findViewById<android.widget.LinearLayout>(android.R.id.content)?.addView(this)
        }

        var currentPage = 0
        val pageSize = 10
        var totalPages = highscoreManager.getTotalPages(pageSize)

        fun updateLeaderboardText() {
            val scores = highscoreManager.getHighscores(currentPage, pageSize)
            val sb = StringBuilder(getString(R.string.leaderboard_title, currentPage + 1, totalPages))
            scores.forEachIndexed { index, entry ->
                sb.append("${currentPage * pageSize + index + 1}. ${entry.name}: ${getString(R.string.score_entry, entry.score, entry.level, entry.distance.toInt())}\n")
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
        voiceHandler.stopListening()
        if (BuildConfig.DEBUG) Timber.d("Activity destroyed")
    }

    private fun setGameMode(mode: GameState) {
        gameEngine.gameState = mode
        if (mode == GameState.FLIGHT) {
            binding.flightView.setGameMode(GameState.FLIGHT)
            binding.flightView.postInvalidate()
            Timber.d("Set game mode to FLIGHT, flightView visibility=${binding.flightView.isVisible}")
        } else {
            binding.flightView.setGameMode(GameState.BUILD)
            binding.buildView.invalidate()
            Timber.d("Set game mode to BUILD, flightView visibility=${binding.flightView.isVisible}")
        }
    }

    private val gameState: GameState
        get() = gameEngine.gameState
}