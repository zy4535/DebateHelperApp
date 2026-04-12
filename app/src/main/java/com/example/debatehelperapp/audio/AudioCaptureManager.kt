package com.example.debatehelperapp.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioCaptureManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    // This starts the microphone and saves the audio to a temporary file
    fun startRecording(): File? {
        // Create a temporary file in the app's cache directory
        currentOutputFile = File(context.cacheDir, "debate_speech_${System.currentTimeMillis()}.m4a")

        // Handle modern Android versions (API 31+) vs older versions
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentOutputFile?.absolutePath)

            try {
                prepare()
                start()
                Log.d("AudioCapture", "Recording started: ${currentOutputFile?.absolutePath}")
            } catch (e: IOException) {
                Log.e("AudioCapture", "Recording failed to start", e)
                return null
            }
        }
        return currentOutputFile
    }

    // This stops the microphone and returns the file so you can send it to Python
    fun stopRecording(): File? {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d("AudioCapture", "Recording stopped.")
        } catch (e: RuntimeException) {
            // MediaRecorder can throw an error if stopped too quickly
            Log.e("AudioCapture", "Failed to stop properly", e)
        } finally {
            mediaRecorder = null
        }
        return currentOutputFile
    }
}