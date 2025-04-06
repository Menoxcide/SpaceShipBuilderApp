package com.example.spaceshipbuilderapp

import android.content.Context
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class AudioManager @Inject constructor(
    @ApplicationContext internal val context: Context
) {
    private var soundEffectPlayer: MediaPlayer? = null
    private var backgroundMusicPlayer: MediaPlayer? = null
    private var currentBackgroundMusicResId: Int? = null
    private var isFadingOut: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val fadeDuration = 1000L // 1 second fade duration
    private val fadeStep = 50L // Update every 50ms
    private var currentVolume: Float = 1.0f

    init {
        // Initialize background music player with audio attributes for music stream
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
    }

    // Play a background music track, fading out the current one if playing
    fun playBackgroundMusic(rawResId: Int) {
        if (currentBackgroundMusicResId == rawResId && backgroundMusicPlayer?.isPlaying == true) {
            Timber.d("Background music $rawResId is already playing")
            return
        }

        // Fade out current music if playing
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
            currentVolume = 1.0f
            backgroundMusicPlayer?.setVolume(currentVolume, currentVolume)
            backgroundMusicPlayer?.start()
            Timber.d("Started background music: $rawResId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to play background music $rawResId")
        }
    }

    private fun fadeOutBackgroundMusic(onComplete: () -> Unit) {
        if (isFadingOut) return
        isFadingOut = true
        currentVolume = 1.0f
        handler.post(object : Runnable {
            override fun run() {
                currentVolume -= (fadeStep.toFloat() / fadeDuration)
                if (currentVolume <= 0f) {
                    backgroundMusicPlayer?.pause()
                    currentVolume = 0f
                    isFadingOut = false
                    onComplete()
                } else {
                    backgroundMusicPlayer?.setVolume(currentVolume, currentVolume)
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

    fun playPowerUpSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.power_up_sound)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play power-up sound")
        }
    }

    fun playCollisionSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.asteroid_hit)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play collision sound")
        }
    }

    fun playBossShootSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.boss_shoot)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play boss shoot sound")
        }
    }

    fun playDialogOpenSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.dialog_open)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play dialog open sound")
        }
    }

    fun playMissileLaunchSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.missile_launch)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play missile launch sound")
        }
    }

    fun playShootSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.shoot)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play shoot sound")
        }
    }

    fun playLevelUpSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.level_up)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play level up sound")
        }
    }

    fun playEnvironmentChangeSound() {
        try {
            soundEffectPlayer?.release()
            soundEffectPlayer = MediaPlayer.create(context, R.raw.environment_change)
            soundEffectPlayer?.start()
            soundEffectPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to play environment change sound")
        }
    }

    fun onDestroy() {
        soundEffectPlayer?.release()
        soundEffectPlayer = null
        stopBackgroundMusic()
        backgroundMusicPlayer?.release()
        backgroundMusicPlayer = null
        handler.removeCallbacksAndMessages(null)
        Timber.d("AudioManager onDestroy called")
    }
}