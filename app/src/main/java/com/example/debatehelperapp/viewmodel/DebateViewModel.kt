package com.example.debatehelperapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.debatehelperapp.audio.SpeechRecognitionManager
import com.example.debatehelperapp.models.ArgumentCard
import com.example.debatehelperapp.models.FlowColumn
import com.example.debatehelperapp.models.SpeechUploadRequest
import com.example.debatehelperapp.network.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DebatePhase(val name: String, val minutes: Int)

class DebateViewModel(application: Application) : AndroidViewModel(application) {

    private val debateSequence = listOf(
        DebatePhase("1AC", 8),
        DebatePhase("1AC Cross-Ex", 3),
        DebatePhase("1NC", 8),
        DebatePhase("1NC Cross-Ex", 3),
        DebatePhase("2AC", 8),
        DebatePhase("2AC Cross-Ex", 3),
        DebatePhase("2NC", 8),
        DebatePhase("2NC Cross-Ex", 3),
        DebatePhase("1NR", 5),
        DebatePhase("1AR", 5),
        DebatePhase("2NR", 5),
        DebatePhase("2AR", 5)
    )

    private var currentPhaseIndex = 0

    // ── EXISTING STATE FLOWS (unchanged) ─────────────────────────────────────
    private val _currentSpeech = MutableStateFlow(debateSequence[currentPhaseIndex].name)
    val currentSpeech: StateFlow<String> = _currentSpeech.asStateFlow()

    private val _timeRemaining = MutableStateFlow(debateSequence[currentPhaseIndex].minutes * 60)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _flowBoardData = MutableStateFlow<List<FlowColumn>>(emptyList())
    val flowBoardData: StateFlow<List<FlowColumn>> = _flowBoardData.asStateFlow()

    // ── NEW STATE FLOWS ───────────────────────────────────────────────────────
    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ── SINGLE MANAGER — handles mic, file saving, and live transcript ────────
    // AudioCaptureManager is no longer needed — SpeechRecognitionManager
    // now does both jobs in one mic session to avoid the RECORD_AUDIO conflict
    private val speechManager = SpeechRecognitionManager(
        context = application.applicationContext,
        onPartialResult = { liveText ->
            _liveTranscript.value = liveText
        },
        onFinalResult = { fullText ->
            _liveTranscript.value = fullText
        },
        onError = { errorMsg ->
            _statusMessage.value = errorMsg
        }
    )

    private var timerJob: Job? = null

    init {
        loadDummyData()
    }

    // ── TIMER & RECORDING ─────────────────────────────────────────────────────

    fun toggleTimer() {
        if (_isRecording.value) {
            stopRecordingAndSubmit()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        _liveTranscript.value = ""
        _statusMessage.value = ""

        // One call — starts MediaRecorder (file) + SpeechRecognizer (live text)
        speechManager.startListening()

        timerJob = viewModelScope.launch {
            while (_timeRemaining.value > 0) {
                delay(1000L)
                _timeRemaining.value -= 1
            }
            stopRecordingAndSubmit()
            advanceToNextSpeech()
        }
    }

    private fun stopRecordingAndSubmit() {
        timerJob?.cancel()
        _isRecording.value = false

        // Returns final transcript AND stops MediaRecorder saving the file
        val finalTranscript = speechManager.stopListening()

        // Audio file is also available if you need to send it to the server:
        // val audioFile = speechManager.getAudioFile()

        if (finalTranscript.isNotBlank()) {
            submitToServer(finalTranscript)
        }
    }

    // ── SERVER SUBMISSION ─────────────────────────────────────────────────────

    private fun submitToServer(transcript: String) {
        _statusMessage.value = "Sending to AI…"
        viewModelScope.launch {
            try {
                val request = SpeechUploadRequest(
                    text = transcript,
                    speech_phase = _currentSpeech.value
                )
                val response = RetrofitClient.apiService.analyzeSpeech(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        _flowBoardData.value = body.flows
                        _statusMessage.value = "Flow updated"
                    }
                } else {
                    _statusMessage.value = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Network error: ${e.message}"
            }
        }
    }

    // ── SPEECH PROGRESSION (unchanged) ───────────────────────────────────────

    fun advanceToNextSpeech() {
        timerJob?.cancel()
        _isRecording.value = false
        _liveTranscript.value = ""
        _statusMessage.value = ""

        if (currentPhaseIndex < debateSequence.size - 1) {
            currentPhaseIndex++
            val nextPhase = debateSequence[currentPhaseIndex]
            _currentSpeech.value = nextPhase.name
            _timeRemaining.value = nextPhase.minutes * 60
        } else {
            _currentSpeech.value = "Debate Over"
            _timeRemaining.value = 0
        }
    }

    // ── CALLED BY DEVELOPER 3 (unchanged) ────────────────────────────────────
    fun addAiArguments(speechName: String, newCards: List<ArgumentCard>) {
        val currentFlows = _flowBoardData.value.toMutableList()
        val existingColIndex = currentFlows.indexOfFirst { it.speechName == speechName }
        if (existingColIndex != -1) {
            val oldColumn = currentFlows[existingColIndex]
            currentFlows[existingColIndex] = oldColumn.copy(
                arguments = oldColumn.arguments + newCards
            )
        } else {
            currentFlows.add(FlowColumn(speechName, newCards))
        }
        _flowBoardData.value = currentFlows
    }

    private fun loadDummyData() {
        val dummyCards = listOf(
            ArgumentCard("Advantage", "Economic Growth: Plan stimulates economy", "Smith '23", false),
            ArgumentCard("Impact", "Prevents recession - creates 2M jobs", "Jones '22", true)
        )
        _flowBoardData.value = listOf(FlowColumn("1AC", dummyCards))
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.stopListening()
    }
}