package com.example.spaceshipbuilderapp

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import android.os.Handler
import android.os.Looper

class AudioManager @Inject constructor(
    @ApplicationContext internal val context: Context
) {
    private var backgroundMusicPlayer: MediaPlayer? = null
    private var currentBackgroundMusicResId: Int? = null
    private var isFadingOut: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val fadeDuration = 1000L // 1 second fade duration
    private val fadeStep = 50L // Update every 50ms
    private var currentMusicVolume: Float = 1.0f

    private val soundPool: SoundPool
    private val soundEffectIds = mutableMapOf<Int, Int>() // Resource ID to SoundPool ID
    private var soundEffectVolume: Float = 1.0f

    init {
        // Initialize background music player
        backgroundMusicPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnErrorListener { mp, what, extra ->
                Timber.e("Background music error: what=$what, extra=$extra")
                true
            }
            setOnCompletionListener {
                it.seekTo(0)
                it.start()
            }
        }

        // Initialize SoundPool for sound effects
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_GAME)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(10) // Allow up to 10 simultaneous sound effects
            .setAudioAttributes(audioAttributes)
            .build()

        // Preload sound effects
        preloadSoundEffects()
    }

    private fun preloadSoundEffects() {
        val soundResources = listOf(
            R.raw.power_up_sound,
            R.raw.asteroid_hit,
            R.raw.boss_shoot,
            R.raw.dialog_open,
            R.raw.missile_launch,
            R.raw.shoot,
            R.raw.level_up,
            R.raw.environment_change,
            R.raw.rotate_sound,
            R.raw.snap_sound,
            R.raw.spaceworthy_sound,
            R.raw.launch_sound
        )
        soundResources.forEach { resId ->
            try {
                soundEffectIds[resId] = soundPool.load(context, resId, 1)
                Timber.d("Preloaded sound effect: $resId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to preload sound effect $resId")
            }
        }
    }

    // Play a background music track with fade transition
    fun playBackgroundMusic(rawResId: Int) {
        if (currentBackgroundMusicResId == rawResId && backgroundMusicPlayer?.isPlaying == true) {
            Timber.d("Background music $rawResId is already playing")
            return
        }

        if (backgroundMusicPlayer?.isPlaying == true) {
            fadeOutBackgroundMusic {
                startNewBackgroundMusic(rawResId)
            }
        } else {
            startNewBackgroundMusic(rawResId)
        }
    }

    private fun startNewBackgroundMusic(rawResId: Int) {
        try {
            backgroundMusicPlayer?.reset()
            backgroundMusicPlayer?.setDataSource(
                context.resources.openRawResourceFd(rawResId).fileDescriptor,
                context.resources.openRawResourceFd(rawResId).startOffset,
                context.resources.openRawResourceFd(rawResId).length
            )
            backgroundMusicPlayer?.prepare()
            backgroundMusicPlayer?.isLooping = true
            currentBackgroundMusicResId = rawResId
            currentMusicVolume = 1.0f
            backgroundMusicPlayer?.setVolume(currentMusicVolume, currentMusicVolume)
            fadeInBackgroundMusic()
            Timber.d("Started background music: $rawResId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to play background music $rawResId")
        }
    }

    private fun fadeInBackgroundMusic() {
        currentMusicVolume = 0f
        backgroundMusicPlayer?.setVolume(currentMusicVolume, currentMusicVolume)
        backgroundMusicPlayer?.start()
        handler.post(object : Runnable {
            override fun run() {
                currentMusicVolume += (fadeStep.toFloat() / fadeDuration)
                if (currentMusicVolume >= 1f) {
                    currentMusicVolume = 1f
                    backgroundMusicPlayer?.setVolume(currentMusicVolume, currentMusicVolume)
                } else {
                    backgroundMusicPlayer?.setVolume(currentMusicVolume, currentMusicVolume)
                    handler.postDelayed(this, fadeStep)
                }
            }
        })
    }

    private fun fadeOutBackgroundMusic(onComplete: () -> Unit) {
        if (isFadingOut) return
        isFadingOut = true
        handler.post(object : Runnable {
            override fun run() {
                currentMusicVolume -= (fadeStep.toFloat() / fadeDuration)
                if (currentMusicVolume <= 0f) {
                    backgroundMusicPlayer?.pause()
                    currentMusicVolume = 0f
                    isFadingOut = false
                    onComplete()
                } else {
                    backgroundMusicPlayer?.setVolume(currentMusicVolume, currentMusicVolume)
                    handler.postDelayed(this, fadeStep)
                }
            }
        })
    }

    fun stopBackgroundMusic() {
        if (backgroundMusicPlayer?.isPlaying == true) {
            fadeOutBackgroundMusic {
                backgroundMusicPlayer?.reset()
                currentBackgroundMusicResId = null
                Timber.d("Stopped background music")
            }
        }
    }

    fun setBackgroundMusicVolume(volume: Float) {
        currentMusicVolume = volume.coerceIn(0f, 1f)
        backgroundMusicPlayer?.setVolume(currentMusicVolume, currentMusicVolume)
        Timber.d("Background music volume set to $currentMusicVolume")
    }

    fun setSoundEffectVolume(volume: Float) {
        soundEffectVolume = volume.coerceIn(0f, 1f)
        Timber.d("Sound effect volume set to $soundEffectVolume")
    }

    private fun playSoundEffect(rawResId: Int) {
        soundEffectIds[rawResId]?.let { soundId ->
            try {
                soundPool.play(soundId, soundEffectVolume, soundEffectVolume, 1, 0, 1.0f)
                Timber.d("Played sound effect: $rawResId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to play sound effect $rawResId")
            }
        } ?: Timber.w("Sound effect $rawResId not loaded")
    }

    fun playPowerUpSound() = playSoundEffect(R.raw.power_up_sound)

    fun playCollisionSound() = playSoundEffect(R.raw.asteroid_hit)

    fun playBossShootSound() = playSoundEffect(R.raw.boss_shoot)

    fun playDialogOpenSound() = playSoundEffect(R.raw.dialog_open)

    fun playMissileLaunchSound() = playSoundEffect(R.raw.missile_launch)

    fun playShootSound() = playSoundEffect(R.raw.shoot)

    fun playLevelUpSound() = playSoundEffect(R.raw.level_up)

    fun playEnvironmentChangeSound() = playSoundEffect(R.raw.environment_change)

    fun playRotateSound() = playSoundEffect(R.raw.rotate_sound)

    fun playSnapSound() = playSoundEffect(R.raw.snap_sound)

    fun playSpaceworthySound() = playSoundEffect(R.raw.spaceworthy_sound)

    fun playLaunchSound() = playSoundEffect(R.raw.launch_sound)

    fun onDestroy() {
        soundPool.release()
        soundEffectIds.clear()
        stopBackgroundMusic()
        backgroundMusicPlayer?.release()
        backgroundMusicPlayer = null
        handler.removeCallbacksAndMessages(null)
        Timber.d("AudioManager onDestroy called")
    }
}