package com.example.spaceshipbuilderapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import com.example.spaceshipbuilderapp.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import timber.log.Timber

class VoiceCommandHandler(context: Context, private val onCommand: (String) -> Unit) {
    private val context: Context = context
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Explicitly set language
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1) // Reduced to 1 for better accuracy
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000) // Ensure longer input
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
    }
    private val scope = CoroutineScope(Dispatchers.Main)
    private var retryCount = 0
    private val MAX_RETRIES = 3 // Reduced retries
    private val RETRY_DELAY = 2000L // Simplified to a single delay

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (BuildConfig.DEBUG) Timber.d("Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                if (BuildConfig.DEBUG) Timber.d("Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (BuildConfig.DEBUG) Timber.d("Speech ended")
                scope.launch { startListening() }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio hardware issue"
                    else -> "Unknown error: $error"
                }
                if (BuildConfig.DEBUG) Timber.e("Speech recognition error: $errorMessage (code: $error)")
                if (retryCount < MAX_RETRIES) {
                    scope.launch {
                        delay(RETRY_DELAY)
                        startListening()
                    }
                    retryCount++
                } else {
                    if (BuildConfig.DEBUG) Timber.w("Max retries ($MAX_RETRIES) reached. Stopping.")
                    retryCount = 0
                    onCommand("speech_error: $errorMessage")
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { command ->
                    if (BuildConfig.DEBUG) Timber.d("Recognized command: $command")
                    onCommand(command)
                    retryCount = 0
                } ?: run {
                    if (BuildConfig.DEBUG) Timber.w("No valid results, retrying...")
                    if (retryCount < MAX_RETRIES) {
                        scope.launch {
                            delay(RETRY_DELAY)
                            startListening()
                        }
                        retryCount++
                    } else {
                        onCommand("speech_error: No valid results")
                        retryCount = 0
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speechRecognizer.startListening(recognizerIntent)
        } else {
            if (BuildConfig.DEBUG) Timber.w("Audio permission denied, cannot start listening")
            onCommand("speech_error: Permission denied")
        }
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        speechRecognizer.destroy()
        scope.cancel()
    }

    fun resetPermissionState() {
        retryCount = 0
        startListening()
    }
}