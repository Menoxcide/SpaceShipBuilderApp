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
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.example.spaceshipbuilderapp.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.hypot

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceHandler: VoiceCommandHandler
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
    @Inject lateinit var gameStateManager: GameStateManager
    @Inject lateinit var buildModeManager: BuildModeManager
    @Inject lateinit var achievementManager: AchievementManager
    @Inject lateinit var audioManager: AudioManager

    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            binding.buildView.renderer.updateAnimationFrame()
            binding.flightView.renderer.updateAnimationFrame()
            if (binding.buildView.isVisible && gameStateManager.gameState == GameState.BUILD) {
                binding.buildView.invalidate()
            }
            if (binding.flightView.isVisible && gameStateManager.gameState == GameState.FLIGHT) {
                binding.flightView.invalidate()
            }
            handler.postDelayed(this, 16)
        }
    }

    private val partTouchListener = View.OnTouchListener { view, event ->
        if (gameStateManager.gameState != GameState.BUILD) return@OnTouchListener false
        val (partType, bitmap) = partButtons[view as ImageButton] ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                initialX = location[0].toFloat() + (view.width / 2f)
                initialY = location[1].toFloat() + (view.height / 2f)
                isDragging = true
                draggedPartType = partType
                binding.buildView.setSelectedPart(Part(partType, bitmap, initialX, initialY, 0f, 1f))
                if (BuildConfig.DEBUG) Timber.d("$partType selected at (x=$initialX, y=$initialY)")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val x = event.rawX
                    val y = event.rawY
                    binding.buildView.setSelectedPart(Part(partType, bitmap, x, y, 0f, 1f))
                    if (BuildConfig.DEBUG) Timber.d("Dragging $partType to (x=$x, y=$y)")
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    val x = event.rawX
                    val y = event.rawY
                    val part = Part(partType, bitmap, x, y, 0f, 1f)
                    val targetPosition = inputHandler.getTargetPosition(partType)
                    if (targetPosition != null) {
                        val (targetX, targetY) = targetPosition
                        val distance = hypot(x - targetX, y - targetY)
                        if (distance < InputHandler.SNAP_RANGE && !inputHandler.checkOverlap(targetX, targetY, part)) {
                            buildModeManager.parts.removeAll { it.type == partType }
                            part.x = targetX
                            part.y = targetY
                            part.scale = buildModeManager.placeholders.find { it.type == partType }?.scale ?: 1f
                            buildModeManager.parts.add(part)
                            if (BuildConfig.DEBUG) Timber.d("Snapped $partType to (x=${part.x}, y=${part.y}) with scale=${part.scale}, parts count: ${buildModeManager.parts.size}")
                            if (buildModeManager.parts.size == 3 && gameEngine.isShipSpaceworthy(gameEngine.screenHeight)) {
                                val shipCenterX = gameEngine.screenWidth / 2f
                                val shipCenterY = (buildModeManager.cockpitY + buildModeManager.engineY) / 2f
                                binding.buildView.renderer.particleSystem.addCollectionParticles(shipCenterX, shipCenterY)
                                Timber.d("Ship fully assembled and spaceworthy! Triggering celebratory particles at (x=$shipCenterX, y=$shipCenterY)")
                            }
                        } else {
                            if (BuildConfig.DEBUG) Timber.d("Invalid placement - Distance=$distance, Overlap=${inputHandler.checkOverlap(targetX, targetY, part)}")
                        }
                    } else {
                        if (BuildConfig.DEBUG) Timber.d("No target position found for $partType")
                    }
                    buildModeManager.selectedPart = null
                    draggedPartType = null
                    view.visibility = View.VISIBLE
                    view.performClick()
                    if (BuildConfig.DEBUG) Timber.d("Dropped $partType, snapping handled")
                    gameEngine.notifyLaunchListener()
                }
                true
            }
            else -> false
        }
    }

    private val placedPartTouchListener = View.OnTouchListener { view, event ->
        if (gameStateManager.gameState != GameState.BUILD) return@OnTouchListener false
        val part = buildModeManager.parts.find { it.type == draggedPartType }
        if (part == null || !isDragging) return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                buildModeManager.parts.remove(part)
                binding.buildView.setSelectedPart(part)
                if (BuildConfig.DEBUG) Timber.d("Picked up placed $draggedPartType at (x=${part.x}, y=${part.y})")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.rawX
                val y = event.rawY
                binding.buildView.setSelectedPart(Part(part.type, part.bitmap, x, y, part.rotation, part.scale))
                if (BuildConfig.DEBUG) Timber.d("Dragging placed $draggedPartType to (x=$x, y=$y)")
                true
            }
            MotionEvent.ACTION_UP -> {
                val x = event.rawX
                val y = event.rawY
                binding.buildView.setSelectedPart(Part(part.type, part.bitmap, x, y, part.rotation, part.scale))
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

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.d("View layout initialized")

        voiceHandler = VoiceCommandHandler(this) { /* Callback set later */ }

        binding.buildView.setOnTouchListener(placedPartTouchListener)
        binding.playerNameInput.isVisible = false

        // Initialize AdMob SDK
        MobileAds.initialize(this) {
            Timber.d("AdMob SDK initialized")
            loadRewardedAd()
        }

        handler.post(animationRunnable)
        requestAudioPermission()

        // Initialize the destroyAllButton synchronously to avoid uninitialized access
        binding.flightView.setDestroyAllButton(binding.destroyAllButton)

        // Set up a listener to set screen dimensions as soon as the view is laid out
        val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val width = binding.flightView.width.toFloat()
                val height = binding.flightView.height.toFloat()
                if (width > 0f && height > 0f) {
                    // Set dimensions in GameEngine
                    gameEngine.setScreenDimensions(width, height)
                    Timber.d("Set screen dimensions in MainActivity: width=$width, height=$height")
                    // Remove the listener to avoid repeated calls
                    binding.flightView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        }
        binding.flightView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        // Handle window insets for status bar height
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top.toFloat()
            binding.buildView.setStatusBarHeight(statusBarHeight)
            binding.flightView.setStatusBarHeight(statusBarHeight)
            binding.flightView.setUserId(userId ?: "default_user")
            // Update dimensions with status bar height
            val width = binding.flightView.width.toFloat()
            val height = binding.flightView.height.toFloat()
            if (width > 0f && height > 0f) {
                gameEngine.setScreenDimensions(width, height, statusBarHeight)
            }
            binding.buildView.renderer.setShipSet(gameEngine.selectedShipSet)
            buildModeManager.parts.clear()
            Timber.d("Initialized with no parts after insets: cockpitY=${buildModeManager.cockpitY}, fuelTankY=${buildModeManager.fuelTankY}, engineY=${buildModeManager.engineY}")
            setLaunchButtonVisibility(false)
            WindowInsetsCompat.CONSUMED
        }

        initializeGame()
    }

    private fun initializeGame() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                userId = "default_user"
                Timber.d("Initializing game with default userId: $userId")
                gameEngine.loadUserData(userId!!)
                highscoreManager.initialize(userId!!)
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize user data")
                showToast(R.string.error_userdata, e.message ?: "Unknown error")
                userId = "default_user"
                gameEngine.playerName = "Player"
            }

            binding.buildView.post {
                binding.buildView.renderer.setShipSet(gameEngine.selectedShipSet)
                partButtons = mapOf(
                    binding.cockpitImage to Pair("cockpit", binding.buildView.renderer.cockpitBitmap),
                    binding.fuelTankImage to Pair("fuel_tank", binding.buildView.renderer.fuelTankBitmap),
                    binding.engineImage to Pair("engine", binding.buildView.renderer.engineBitmap)
                )
                binding.cockpitImage.setImageBitmap(binding.buildView.renderer.cockpitBitmap)
                binding.fuelTankImage.setImageBitmap(binding.buildView.renderer.fuelTankBitmap)
                binding.engineImage.setImageBitmap(binding.buildView.renderer.engineBitmap)
                binding.cockpitImage.visibility = View.VISIBLE
                binding.fuelTankImage.visibility = View.VISIBLE
                binding.engineImage.visibility = View.VISIBLE
                binding.selectionPanel.visibility = View.VISIBLE
                Timber.d("Selection panel visibility: ${binding.selectionPanel.isVisible}, height: ${binding.selectionPanel.height}")
                Timber.d("Cockpit image visibility: ${binding.cockpitImage.isVisible}, width: ${binding.cockpitImage.width}, height: ${binding.cockpitImage.height}")
                Timber.d("Fuel tank image visibility: ${binding.fuelTankImage.isVisible}, width: ${binding.fuelTankImage.width}, height: ${binding.fuelTankImage.height}")
                Timber.d("Engine image visibility: ${binding.engineImage.isVisible}, width: ${binding.engineImage.width}, height: ${binding.engineImage.height}")
                setupListeners()
                setupShipSpinner()
            }

            gameEngine.setLaunchListener { isLaunching ->
                binding.selectionPanel.isVisible = !isLaunching
                binding.buildView.isVisible = !isLaunching
                binding.buildView.isEnabled = !isLaunching
                binding.flightView.isVisible = isLaunching
                binding.flightView.isEnabled = isLaunching
                binding.playerNameInput.isVisible = false
                binding.navigationButtons.isVisible = !isLaunching // Control visibility of all navigation buttons
                if (isLaunching) {
                    binding.flightView.requestFocus()
                    binding.flightView.setGameMode(GameState.FLIGHT)
                    binding.flightView.postInvalidate()
                    if (BuildConfig.DEBUG) Timber.d("FlightView focused: ${binding.flightView.isFocused}, invalidated")
                } else {
                    binding.buildView.setOnTouchListener(placedPartTouchListener)
                    binding.flightView.setGameMode(GameState.BUILD)
                    binding.buildView.renderer.setShipSet(gameEngine.selectedShipSet)
                    if (BuildConfig.DEBUG) Timber.d("BuildView focused: ${binding.buildView.isFocused}")
                    setupShipSpinner()
                }
                val isLaunchReady = gameEngine.isShipSpaceworthy(gameEngine.screenHeight) && buildModeManager.parts.size == 3 && !isLaunching
                setLaunchButtonVisibility(isLaunchReady)
                if (BuildConfig.DEBUG) {
                    Timber.d("Launch button visibility set to $isLaunchReady, isSpaceworthy=${gameEngine.isShipSpaceworthy(gameEngine.screenHeight)}, partsSize=${buildModeManager.parts.size}, isLaunching=$isLaunching")
                    if (!isLaunchReady) {
                        Timber.d("Spaceworthiness failure: ${gameEngine.getSpaceworthinessFailureReason(gameEngine.screenHeight)}")
                    }
                }
            }

            gameEngine.setGameOverListener { canContinue, canUseRevive, onContinueWithAd, onContinueWithRevive, onReturnToBuild ->
                if (canContinue || canUseRevive) {
                    showContinueDialog(canContinue, canUseRevive, onContinueWithAd, onContinueWithRevive, onReturnToBuild)
                } else {
                    onReturnToBuild()
                }
            }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Timber.d("Rewarded ad loaded successfully")
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Timber.d("Rewarded ad dismissed")
                        rewardedAd = null
                        loadRewardedAd()
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

    private fun setupShipSpinner() {
        val unlockedShipSets = gameEngine.getUnlockedShipSets()
        val shipNames = mutableListOf<String>()
        for (i in 0..2) {
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

        binding.shipSpinner.setSelection(gameEngine.selectedShipSet)

        binding.shipSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position in gameEngine.getUnlockedShipSets()) {
                    gameEngine.selectedShipSet = position
                    binding.buildView.renderer.setShipSet(position)
                    partButtons = mapOf(
                        binding.cockpitImage to Pair("cockpit", binding.buildView.renderer.cockpitBitmap),
                        binding.fuelTankImage to Pair("fuel_tank", binding.buildView.renderer.fuelTankBitmap),
                        binding.engineImage to Pair("engine", binding.buildView.renderer.engineBitmap)
                    )
                    binding.cockpitImage.setImageBitmap(binding.buildView.renderer.cockpitBitmap)
                    binding.fuelTankImage.setImageBitmap(binding.buildView.renderer.fuelTankBitmap)
                    binding.engineImage.setImageBitmap(binding.buildView.renderer.engineBitmap)
                    binding.buildView.invalidate()
                } else {
                    binding.shipSpinner.setSelection(gameEngine.selectedShipSet)
                    val requirements = when (position) {
                        1 -> "Level 20 and 20 Stars"
                        2 -> "Level 40 and 40 Stars"
                        else -> "Unknown requirements"
                    }
                    showToast("This ship set is locked! Reach $requirements to unlock it.")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.shipSpinner.isEnabled = gameStateManager.gameState == GameState.BUILD
    }

    private fun showContinueDialog(
        canContinueWithAd: Boolean,
        canUseRevive: Boolean,
        onContinueWithAd: () -> Unit,
        onContinueWithRevive: () -> Unit,
        onReturnToBuild: () -> Unit
    ) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_continue)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val messageText = dialog.findViewById<TextView>(R.id.continueMessage)
        val watchAdButton = dialog.findViewById<Button>(R.id.watchAdButton)
        val useReviveButton = dialog.findViewById<Button>(R.id.useReviveButton)
        val returnButton = dialog.findViewById<Button>(R.id.returnButton)

        messageText.text = "Game Over! Continue?"
        watchAdButton.text = "Watch Ad"
        useReviveButton.text = "Use Revive (${gameEngine.reviveCount}/3)"
        returnButton.text = "Return to Build"

        watchAdButton.visibility = if (canContinueWithAd) View.VISIBLE else View.GONE
        useReviveButton.visibility = if (canUseRevive) View.VISIBLE else View.GONE

        watchAdButton.setOnClickListener {
            if (rewardedAd != null) {
                rewardedAd?.show(this) { rewardItem ->
                    Timber.d("User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                    onContinueWithAd()
                }
                dialog.dismiss()
            } else {
                showToast("Ad not ready, please try again later", Toast.LENGTH_LONG)
                onReturnToBuild()
                dialog.dismiss()
            }
        }

        useReviveButton.setOnClickListener {
            onContinueWithRevive()
            dialog.dismiss()
        }

        returnButton.setOnClickListener {
            onReturnToBuild()
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
                showToast(binding.buildView.gameEngine.getSpaceworthinessFailureReason(gameEngine.screenHeight), Toast.LENGTH_LONG)
            }
        }

        binding.leaderboardButton.setOnClickListener {
            if (BuildConfig.DEBUG) Timber.d("Leaderboard button clicked")
            val intent = Intent(this, LeaderboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        binding.shopButton.setOnClickListener {
            if (BuildConfig.DEBUG) Timber.d("Galactic Shop button clicked")
            val intent = Intent(this, GalacticShopActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        binding.achievementsButton.setOnClickListener {
            if (BuildConfig.DEBUG) Timber.d("Achievements button clicked")
            val intent = Intent(this, AchievementsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        binding.skillTreeButton.setOnClickListener {
            if (BuildConfig.DEBUG) Timber.d("Skill Tree button clicked")
            val intent = Intent(this, SkillTreeActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        partButtons.forEach { (button, _) ->
            button.setOnTouchListener(partTouchListener)
        }

        binding.buildView.setOnTouchListener(placedPartTouchListener)
    }

    fun setLaunchButtonVisibility(isVisible: Boolean) {
        binding.launchButton.isVisible = isVisible
        if (BuildConfig.DEBUG) Timber.d("Launch button visibility set to $isVisible")

        if (isVisible) {
            // Position the launch button on the fuel tank
            binding.launchButton.post {
                // Get the fuel tank's Y position from BuildModeManager
                val fuelTankY: Float = buildModeManager.fuelTankY
                val screenWidth: Float = gameEngine.screenWidth

                // Center the button horizontally
                val buttonWidth: Float = binding.launchButton.width.toFloat()
                val widthDifference: Float = screenWidth - buttonWidth
                val buttonX: Float = widthDifference / 2.0f

                // Set the Y position to the fuel tank's Y coordinate
                // Adjust for the button's height to center it vertically on the fuel tank
                val buttonHeight: Float = binding.launchButton.height.toFloat()
                val halfButtonHeight: Float = buttonHeight / 2.0f
                val buttonY: Float = fuelTankY - halfButtonHeight

                // Apply the position
                binding.launchButton.x = buttonX
                binding.launchButton.y = buttonY

                Timber.d("Positioned launch button at (x=$buttonX, y=$buttonY) on fuel tank (fuelTankY=$fuelTankY)")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(animationRunnable)
        gameEngine.onDestroy()
        voiceHandler.stopListening()
        audioManager.onDestroy()
        if (BuildConfig.DEBUG) Timber.d("Activity destroyed")
    }
}