package com.example.spaceshipbuilderapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceCommandHandler(context: Context, private val onCommand: (String) -> Unit) {
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("VoiceCommandHandler", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("VoiceCommandHandler", "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("VoiceCommandHandler", "Speech ended")
                startListening() // Restart listening for continuous voice commands
            }

            override fun onError(error: Int) {
                Log.e("VoiceCommandHandler", "Speech recognition error: $error")
                startListening() // Restart on error
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { command ->
                    Log.d("VoiceCommandHandler", "Recognized command: $command")
                    onCommand(command)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        speechRecognizer.startListening(recognizerIntent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        speechRecognizer.destroy()
    }
}