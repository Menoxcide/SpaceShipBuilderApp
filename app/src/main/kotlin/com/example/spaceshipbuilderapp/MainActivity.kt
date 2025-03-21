package com.example.spaceshipbuilderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.example.spaceshipbuilderapp.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import timber.log.Timber
import android.app.Dialog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.hypot

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceHandler: VoiceCommandHandler
    private lateinit var credentialManager: CredentialManager
    private var partButtons: Map<ImageButton, Pair<String, Bitmap>> = emptyMap()
    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false
    private var draggedPartType: String? = null
    private val audioPermissionCode = 100
    private var userId: String? = null

    // AdMob Rewarded Ad variables
    private var rewardedAd: RewardedAd? = null
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917" // Test Rewarded Ad Unit ID

    @Inject lateinit var highscoreManager: HighscoreManager
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var inputHandler: InputHandler

    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            binding.buildView.renderer.updateAnimationFrame()
            binding.flightView.renderer.updateAnimationFrame()
            if (binding.buildView.isVisible) binding.buildView.invalidate()
            if (binding.flightView.isVisible) binding.flightView.invalidate()
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
                binding.buildView.setSelectedPart(GameEngine.Part(partType, bitmap, initialX, initialY, 0f, 1f))
                if (BuildConfig.DEBUG) Timber.d("$partType selected at (x=$initialX, y=$initialY)")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val x = event.rawX
                    val y = event.rawY
                    binding.buildView.setSelectedPart(GameEngine.Part(partType, bitmap, x, y, 0f, 1f))
                    if (BuildConfig.DEBUG) Timber.d("Dragging $partType to (x=$x, y=$y)")
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    val x = event.rawX
                    val y = event.rawY
                    val part = GameEngine.Part(partType, bitmap, x, y, 0f, 1f)
                    // Check if the part can snap to a placeholder
                    val targetPosition = inputHandler.getTargetPosition(partType)
                    if (targetPosition != null) {
                        val (targetX, targetY) = targetPosition
                        val distance = hypot(x - targetX, y - targetY)
                        if (distance < InputHandler.SNAP_RANGE && !inputHandler.checkOverlap(targetX, targetY, part)) {
                            // Snap the part to the placeholder position
                            gameEngine.parts.removeAll { it.type == partType }
                            part.x = targetX
                            part.y = targetY
                            part.scale = gameEngine.placeholders.find { it.type == partType }?.scale ?: 1f
                            gameEngine.parts.add(part)
                            if (BuildConfig.DEBUG) Timber.d("Snapped $partType to (x=${part.x}, y=${part.y}) with scale=${part.scale}, parts count: ${gameEngine.parts.size}")
                            // Check if ship is spaceworthy and trigger celebratory particles
                            if (gameEngine.parts.size == 3 && gameEngine.isShipSpaceworthy(gameEngine.screenHeight)) {
                                val shipCenterX = gameEngine.screenWidth / 2f
                                val shipCenterY = (gameEngine.cockpitY + gameEngine.engineY) / 2f
                                binding.buildView.renderer.particleSystem.addCollectionParticles(shipCenterX, shipCenterY)
                                Timber.d("Ship fully assembled and spaceworthy! Triggering celebratory particles at (x=$shipCenterX, y=$shipCenterY)")
                            }
                        } else {
                            if (BuildConfig.DEBUG) Timber.d("Invalid placement - Distance=$distance, Overlap=${inputHandler.checkOverlap(targetX, targetY, part)}")
                        }
                    } else {
                        if (BuildConfig.DEBUG) Timber.d("No target position found for $partType")
                    }
                    gameEngine.selectedPart = null // Clear the selected part in GameEngine
                    draggedPartType = null
                    view.visibility = View.VISIBLE
                    view.performClick()
                    if (BuildConfig.DEBUG) Timber.d("Dropped $partType, snapping handled")
                    // Force recheck of launch button visibility
                    gameEngine.notifyLaunchListener()
                }
                true
            }
            else -> false
        }
    }

    private val placedPartTouchListener = View.OnTouchListener { view, event ->
        if (gameState != GameState.BUILD) return@OnTouchListener false
        val part = binding.buildView.gameEngine.parts.find { it.type == draggedPartType }
        if (part == null || !isDragging) return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                binding.buildView.gameEngine.parts.remove(part)
                binding.buildView.setSelectedPart(part)
                if (BuildConfig.DEBUG) Timber.d("Picked up placed $draggedPartType at (x=${part.x}, y=${part.y})")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.rawX
                val y = event.rawY
                binding.buildView.setSelectedPart(GameEngine.Part(part.type, part.bitmap, x, y, part.rotation, part.scale))
                if (BuildConfig.DEBUG) Timber.d("Dragging placed $draggedPartType to (x=$x, y=$y)")
                true
            }
            MotionEvent.ACTION_UP -> {
                val x = event.rawX
                val y = event.rawY
                binding.buildView.setSelectedPart(GameEngine.Part(part.type, part.bitmap, x, y, part.rotation, part.scale))
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

        voiceHandler = VoiceCommandHandler(this) { /* Callback set later */ }

        binding.buildView.setOnTouchListener(placedPartTouchListener)
        binding.launchButton.isVisible = false
        binding.playerNameInput.isVisible = false

        // Initialize AdMob SDK
        MobileAds.initialize(this) {
            Timber.d("AdMob SDK initialized")
            loadRewardedAd() // Load the first ad after initialization
        }

        handler.post(animationRunnable)
        requestAudioPermission()

        // Initialize game without sign-in
        initializeGame()
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Timber.d("Rewarded ad loaded successfully")
                // Set up full-screen content callback
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Timber.d("Rewarded ad dismissed")
                        rewardedAd = null
                        loadRewardedAd() // Load a new ad for the next use
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Timber.e("Rewarded ad failed to show: ${adError.message}")
                        rewardedAd = null
                        loadRewardedAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Timber.d("Rewarded ad shown")
                    }
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Timber.e("Rewarded ad failed to load: ${loadAdError.message}")
                rewardedAd = null
            }
        })
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

            binding.buildView.post {
                // Ensure Renderer's selectedShipSet matches GameEngine's selectedShipSet
                binding.buildView.renderer.setShipSet(gameEngine.selectedShipSet)
                partButtons = mapOf(
                    binding.cockpitImage to Pair("cockpit", binding.buildView.renderer.cockpitBitmap),
                    binding.fuelTankImage to Pair("fuel_tank", binding.buildView.renderer.fuelTankBitmap),
                    binding.engineImage to Pair("engine", binding.buildView.renderer.engineBitmap)
                )
                // Explicitly set the bitmaps for the default ship set
                binding.cockpitImage.setImageBitmap(binding.buildView.renderer.cockpitBitmap)
                binding.fuelTankImage.setImageBitmap(binding.buildView.renderer.fuelTankBitmap)
                binding.engineImage.setImageBitmap(binding.buildView.renderer.engineBitmap)
                // Ensure the ImageButton widgets are visible
                binding.cockpitImage.visibility = View.VISIBLE
                binding.fuelTankImage.visibility = View.VISIBLE
                binding.engineImage.visibility = View.VISIBLE
                // Ensure the selection panel is visible
                binding.selectionPanel.visibility = View.VISIBLE
                // Debug visibility and dimensions
                Timber.d("Selection panel visibility: ${binding.selectionPanel.isVisible}, height: ${binding.selectionPanel.height}")
                Timber.d("Cockpit image visibility: ${binding.cockpitImage.isVisible}, width: ${binding.cockpitImage.width}, height: ${binding.cockpitImage.height}")
                Timber.d("Fuel tank image visibility: ${binding.fuelTankImage.isVisible}, width: ${binding.fuelTankImage.width}, height: ${binding.fuelTankImage.height}")
                Timber.d("Engine image visibility: ${binding.engineImage.isVisible}, width: ${binding.engineImage.width}, height: ${binding.engineImage.height}")
                setupListeners()
                setupShipSpinner()
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
                // Ensure Renderer's selectedShipSet is set before initializing placeholders
                binding.buildView.renderer.setShipSet(gameEngine.selectedShipSet)
                gameEngine.initializePlaceholders(
                    screenWidth, screenHeight,
                    binding.buildView.renderer.cockpitPlaceholderBitmap,
                    binding.buildView.renderer.fuelTankPlaceholderBitmap,
                    binding.buildView.renderer.enginePlaceholderBitmap,
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
                binding.leaderboardButton.isVisible = !isLaunching
                binding.shopButton.isVisible = !isLaunching
                if (isLaunching) {
                    binding.flightView.requestFocus()
                    binding.flightView.setGameMode(GameState.FLIGHT)
                    binding.flightView.postInvalidate()
                    if (BuildConfig.DEBUG) Timber.d("FlightView focused: ${binding.flightView.isFocused}, invalidated")
                } else {
                    binding.buildView.setOnTouchListener(placedPartTouchListener)
                    binding.flightView.setGameMode(GameState.BUILD)
                    // Refresh placeholders when returning to build mode
                    binding.buildView.renderer.setShipSet(gameEngine.selectedShipSet)
                    gameEngine.initializePlaceholders(
                        gameEngine.screenWidth,
                        gameEngine.screenHeight,
                        binding.buildView.renderer.cockpitPlaceholderBitmap,
                        binding.buildView.renderer.fuelTankPlaceholderBitmap,
                        binding.buildView.renderer.enginePlaceholderBitmap,
                        gameEngine.statusBarHeight
                    )
                    if (BuildConfig.DEBUG) Timber.d("BuildView focused: ${binding.buildView.isFocused}")
                    setupShipSpinner() // Update spinner when returning to build mode
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

            // Set up game over listener for ad-based continue
            gameEngine.setGameOverListener { canContinue, onContinue, onDecline ->
                if (canContinue) {
                    showContinueDialog(onContinue, onDecline)
                } else {
                    onDecline()
                }
            }
        }
    }

    private fun setupShipSpinner() {
        val unlockedShipSets = gameEngine.getUnlockedShipSets()
        val shipNames = mutableListOf<String>()
        for (i in 0..2) { // We have 3 ship sets (0, 1, 2)
            if (i in unlockedShipSets) {
                shipNames.add("Ship Set ${i + 1}")
            } else {
                val requirements = when (i) {
                    1 -> "Level 20 + 20 Stars"
                    2 -> "Level 40 + 40 Stars"
                    else -> "Locked"
                }
                shipNames.add("Ship Set ${i + 1} (Locked - $requirements)")
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, shipNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.shipSpinner.adapter = adapter

        // Set the current selection
        binding.shipSpinner.setSelection(gameEngine.selectedShipSet)

        // Handle selection
        binding.shipSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position in gameEngine.getUnlockedShipSets()) {
                    gameEngine.selectedShipSet = position
                    // Update the Renderer's selectedShipSet
                    binding.buildView.renderer.setShipSet(position)
                    // Update the part buttons to show the selected ship set
                    partButtons = mapOf(
                        binding.cockpitImage to Pair("cockpit", binding.buildView.renderer.cockpitBitmap),
                        binding.fuelTankImage to Pair("fuel_tank", binding.buildView.renderer.fuelTankBitmap),
                        binding.engineImage to Pair("engine", binding.buildView.renderer.engineBitmap)
                    )
                    binding.cockpitImage.setImageBitmap(binding.buildView.renderer.cockpitBitmap)
                    binding.fuelTankImage.setImageBitmap(binding.buildView.renderer.fuelTankBitmap)
                    binding.engineImage.setImageBitmap(binding.buildView.renderer.engineBitmap)
                    // Refresh placeholders with the new ship set
                    gameEngine.initializePlaceholders(
                        gameEngine.screenWidth,
                        gameEngine.screenHeight,
                        binding.buildView.renderer.cockpitPlaceholderBitmap,
                        binding.buildView.renderer.fuelTankPlaceholderBitmap,
                        binding.buildView.renderer.enginePlaceholderBitmap,
                        gameEngine.statusBarHeight
                    )
                    // Redraw the build view to reflect the new ship set
                    binding.buildView.invalidate()
                } else {
                    // Revert to the previous selection if the selected ship is locked
                    binding.shipSpinner.setSelection(gameEngine.selectedShipSet)
                    val requirements = when (position) {
                        1 -> "Level 20 and 20 Stars"
                        2 -> "Level 40 and 40 Stars"
                        else -> "Unknown requirements"
                    }
                    showToast("This ship set is locked! Reach $requirements to unlock it.")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        // Enable/disable spinner items based on unlocked status
        binding.shipSpinner.isEnabled = gameState == GameState.BUILD
    }

    private fun showContinueDialog(onContinue: () -> Unit, onDecline: () -> Unit) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_continue)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val messageText = dialog.findViewById<TextView>(R.id.continueMessage)
        val watchAdButton = dialog.findViewById<Button>(R.id.watchAdButton)
        val returnButton = dialog.findViewById<Button>(R.id.returnButton)

        messageText.text = "Game Over! Watch an ad to continue?"
        watchAdButton.text = "Watch Ad"
        returnButton.text = "Return to Build"

        watchAdButton.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(this) { rewardItem ->
                    // Reward granted, continue the game
                    Timber.d("User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                    onContinue()
                }
                dialog.dismiss()
            } else {
                showToast("Ad not ready, please try again later", Toast.LENGTH_LONG)
                onDecline()
                dialog.dismiss()
            }
        }

        returnButton.setOnClickListener {
            onDecline()
            dialog.dismiss()
        }

        dialog.setCancelable(false)
        dialog.show()
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

        // Add Galactic Shop button listener
        binding.shopButton.setOnClickListener {
            if (BuildConfig.DEBUG) Timber.d("Galactic Shop button clicked")
            val intent = Intent(this, GalacticShopActivity::class.java)
            startActivity(intent)
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