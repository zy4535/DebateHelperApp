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
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

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

    // File Saving Variables
    private var currentOutputFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    private val sampleRate = 16000 // Google's default STT sample rate

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // CRITICAL: This flag tells Google to send audio buffers back to our app
        putExtra("android.speech.extra.GET_AUDIO_PCM_DATA", true)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onBufferReceived(buffer: ByteArray?) {
            // This is the "Bridge" magic: we save the bytes Google is hearing
            if (buffer != null && isListening) {
                try {
                    fileOutputStream?.write(buffer)
                } catch (e: Exception) {
                    Log.e("SpeechRecognition", "Error writing buffer", e)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            onPartialResult(fullTranscriptBuilder.toString() + partial)
        }

        override fun onResults(results: Bundle?) {
            val sentence = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            fullTranscriptBuilder.append(sentence).append(" ")
            onFinalResult(fullTranscriptBuilder.toString())

            if (isListening) {
                mainHandler.post { speechRecognizer?.startListening(recognizerIntent) }
            }
        }

        override fun onError(error: Int) {
            // (Keep your existing error handling here)
            if (isListening) {
                mainHandler.post { speechRecognizer?.startListening(recognizerIntent) }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) { Log.d("SpeechRecognition", "Ready") }
        override fun onBeginningOfSpeech() { Log.d("SpeechRecognition", "User is talking") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        fullTranscriptBuilder.clear()

        // 1. Prepare the WAV file
        currentOutputFile = File(context.cacheDir, "debate_capture_${System.currentTimeMillis()}.wav")
        fileOutputStream = FileOutputStream(currentOutputFile)
        fileOutputStream?.write(ByteArray(44)) // Placeholder for WAV header

        // 2. Start the native Speech Recognizer
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
                startListening(recognizerIntent)
            }
        }
    }

    fun stopListening(): String {
        isListening = false
        speechRecognizer?.stopListening()

        // 3. Close the file and fix the header
        fileOutputStream?.close()
        fileOutputStream = null
        currentOutputFile?.let { updateWavHeader(it) }

        return fullTranscriptBuilder.toString().trim()
    }

    fun getAudioFile(): File? = currentOutputFile

    // (Include the updateWavHeader function from my previous message here)
    private fun updateWavHeader(file: File) {
        // ... (The code I provided in the previous message to fix the 44-byte header) ...
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8
        val header = ByteArray(44)
        header[0] = 'R'.toByte(); header[1] = 'I'.toByte(); header[2] = 'F'.toByte(); header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte(); header[9] = 'A'.toByte(); header[10] = 'V'.toByte(); header[11] = 'E'.toByte()
        header[12] = 'f'.toByte(); header[13] = 'm'.toByte(); header[14] = 't'.toByte(); header[15] = ' '.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte(); header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.toByte(); header[37] = 'a'.toByte(); header[38] = 't'.toByte(); header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte(); header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte(); header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(header)
        raf.close()
    }
}