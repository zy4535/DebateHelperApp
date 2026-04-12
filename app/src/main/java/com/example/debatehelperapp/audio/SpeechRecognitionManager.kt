package com.example.debatehelperapp.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * SpeechRecognitionManager.kt
 *
 * Gives SpeechRecognizer exclusive mic access so live transcript works.
 * The full transcript text is returned on stopListening() and sent to Python.
 * MediaRecorder is removed — it was blocking the audio stream.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val fullTranscriptBuilder = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Keep listening for longer pauses before cutting off
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("SpeechRecognition", "Ready for speech — speak now")
        }

        override fun onBeginningOfSpeech() {
            Log.d("SpeechRecognition", "Speech detected")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            Log.d("SpeechRecognition", "Partial: $partial")
            onPartialResult(fullTranscriptBuilder.toString() + partial)
        }

        override fun onResults(results: Bundle?) {
            val sentence = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            Log.d("SpeechRecognition", "Final sentence: $sentence")
            fullTranscriptBuilder.append(sentence).append(" ")
            onFinalResult(fullTranscriptBuilder.toString())

            // Restart immediately to keep listening
            if (isListening) {
                mainHandler.post {
                    speechRecognizer?.startListening(recognizerIntent)
                }
            }
        }

        override fun onError(error: Int) {
            Log.e("SpeechRecognition", "Error code: $error")
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                SpeechRecognizer.ERROR_NETWORK -> "Network error — check connection"
                SpeechRecognizer.ERROR_AUDIO   -> "Audio error — check microphone"
                else -> null
            }
            if (message == null) {
                // Restart silently on non-fatal errors
                if (isListening) {
                    mainHandler.postDelayed({
                        speechRecognizer?.startListening(recognizerIntent)
                    }, 100)
                }
            } else {
                onError(message)
            }
        }

        override fun onEndOfSpeech() {
            Log.d("SpeechRecognition", "End of speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("SpeechRecognition", "Recognition NOT available")
            onError("Speech recognition not available on this device")
            return
        }

        Log.d("SpeechRecognition", "Starting — mic is free for SpeechRecognizer")
        fullTranscriptBuilder.clear()
        isListening = true

        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
                startListening(recognizerIntent)
            }
            Log.d("SpeechRecognition", "SpeechRecognizer started")
        }
    }

    fun stopListening(): String {
        isListening = false
        mainHandler.post {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            Log.d("SpeechRecognition", "SpeechRecognizer destroyed")
        }
        val transcript = fullTranscriptBuilder.toString().trim()
        Log.d("SpeechRecognition", "Final transcript: $transcript")
        return transcript
    }
}