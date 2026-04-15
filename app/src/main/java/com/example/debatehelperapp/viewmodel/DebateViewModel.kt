package com.example.debatehelperapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.debatehelperapp.audio.SpeechRecognitionManager
import com.example.debatehelperapp.models.ArgumentCard
import com.example.debatehelperapp.models.FlowColumn
import com.example.debatehelperapp.models.GeminiContent
import com.example.debatehelperapp.models.GeminiPart
import com.example.debatehelperapp.models.GeminiRequest
import com.example.debatehelperapp.models.GeminiSystemInstruction
import com.example.debatehelperapp.models.RoomState
import com.example.debatehelperapp.network.RetrofitClient
import com.example.debatehelperapp.repository.FirebaseRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DebatePhase(val name: String, val minutes: Int)

class DebateViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepository by lazy { FirebaseRepository() }

    private val _activeJoinCode = MutableStateFlow("")
    val activeJoinCode: StateFlow<String> = _activeJoinCode.asStateFlow()

    private val _currentScreen = MutableStateFlow("SPLASH")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _localPlayerRole = MutableStateFlow<String?>(null)
    val localPlayerRole: StateFlow<String?> = _localPlayerRole.asStateFlow()

    private val _roomState = MutableStateFlow(RoomState())
    val roomState: StateFlow<RoomState> = _roomState.asStateFlow()

    val documentText = MutableStateFlow("") // Replaces aff/neg text fields with a single personal one

    fun navigateTo(screen: String) { _currentScreen.value = screen }

    // --- MULTIPLAYER LOBBY LOGIC ---

    fun hostNewDebate() {
        _isProcessing.value = true
        _statusMessage.value = "Generating Room Code..."

        viewModelScope.launch {
            try {
                val code = firebaseRepository.createDebateRoom()
                _activeJoinCode.value = code
                _isProcessing.value = false
                navigateTo("LOBBY")
                startRoomStateListener(code)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to create room: ${e.localizedMessage}"
                _isProcessing.value = false
            }
        }
    }

    fun joinDebateRoom(code: String) {
        _statusMessage.value = "Joining room $code..."
        _isProcessing.value = true
        _activeJoinCode.value = code

        viewModelScope.launch {
            try {
                firebaseRepository.markRoomActive(code)
                _isProcessing.value = false
                startRoomStateListener(code)
                navigateTo("ROLE_SELECT")
            } catch (e: Exception) {
                _statusMessage.value = "Error joining: ${e.localizedMessage}"
                _isProcessing.value = false
            }
        }
    }

    fun claimRole(role: String) {
        _localPlayerRole.value = role
        viewModelScope.launch {
            if (role != "SPECTATOR") {
                firebaseRepository.claimRole(_activeJoinCode.value, role)
            }

            // IF NOT A DEBATER: Skip setup, go straight to the viewing board
            if (role == "JUDGE" || role == "SPECTATOR") {
                firebaseRepository.listenToRoomFlows(_activeJoinCode.value) { updatedFlows ->
                    _flowBoardData.value = updatedFlows
                }
                navigateTo("DEBATE")
            } else {
                navigateTo("SETUP")
            }
        }
    }

    // --- THE SYNCHRONIZATION BRAIN ---
    private fun startRoomStateListener(code: String) {
        firebaseRepository.listenToRoomState(code) { state ->
            _roomState.value = state

            if (state.status == "ACTIVE" && _currentScreen.value == "LOBBY") {
                navigateTo("ROLE_SELECT")
            }

            if (state.affReady && state.negReady && _currentScreen.value == "SETUP") {
                _statusMessage.value = "Launching Board..."
                firebaseRepository.listenToRoomFlows(code) { updatedFlows ->
                    _flowBoardData.value = updatedFlows
                }
                navigateTo("DEBATE")
                _statusMessage.value = ""
            }

            if (state.affWantsNext && state.negWantsNext) {
                if (_localPlayerRole.value == "AFF") {
                    viewModelScope.launch {
                        val nextIndex = state.currentPhaseIndex + 1
                        firebaseRepository.advancePhase(code, nextIndex)
                    }
                }
            }

            if (state.currentPhaseIndex != currentPhaseIndex) {
                currentPhaseIndex = state.currentPhaseIndex

                if (currentPhaseIndex < debateSequence.size) {
                    val nextPhase = debateSequence[currentPhaseIndex]
                    _currentSpeech.value = nextPhase.name
                    _timeRemaining.value = nextPhase.minutes * 60
                } else {
                    _currentSpeech.value = "Debate Over"
                    _timeRemaining.value = 0
                }

                if (_isRecording.value) stopRecordingLocally()
                currentSpeechAccumulatedText = ""
                _liveTranscript.value = ""
                _statusMessage.value = ""
            }
        }
    }

    fun submitSetupReady() {
        val role = _localPlayerRole.value ?: return
        viewModelScope.launch {
            if (documentText.value.isNotBlank()) {
                val targetSpeech = if (role == "AFF") "1AC Doc" else "1NC Doc"
                submitToServer(documentText.value, targetSpeech)
            }
            firebaseRepository.setSetupReady(_activeJoinCode.value, role, documentText.value)
        }
    }

    fun submitJudgeSummary(text: String) {
        viewModelScope.launch {
            firebaseRepository.submitJudgeSummary(_activeJoinCode.value, text)
        }
    }

    // --- KEEP THE REST OF YOUR DEBATE LOGIC EXACTLY THE SAME BELOW HERE! ---

    // --- DEBATE LOGIC ---

    private val debateSequence = listOf(
        DebatePhase("1AC", 8), DebatePhase("1AC Cross-Ex", 3),
        DebatePhase("1NC", 8), DebatePhase("1NC Cross-Ex", 3),
        DebatePhase("2AC", 8), DebatePhase("2AC Cross-Ex", 3),
        DebatePhase("2NC", 8), DebatePhase("2NC Cross-Ex", 3),
        DebatePhase("1NR", 5), DebatePhase("1AR", 5),
        DebatePhase("2NR", 5), DebatePhase("2AR", 5)
    )

    private var currentPhaseIndex = 0

    private val _currentSpeech = MutableStateFlow(debateSequence[0].name)
    val currentSpeech: StateFlow<String> = _currentSpeech.asStateFlow()

    private val _timeRemaining = MutableStateFlow(debateSequence[0].minutes * 60)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _flowBoardData = MutableStateFlow<List<FlowColumn>>(emptyList())
    val flowBoardData: StateFlow<List<FlowColumn>> = _flowBoardData.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _summaryText = MutableStateFlow("")
    val summaryText: StateFlow<String> = _summaryText.asStateFlow()

    private var currentSpeechAccumulatedText = ""

    private val speechManager = SpeechRecognitionManager(
        context = application.applicationContext,
        onPartialResult = { liveText -> _liveTranscript.value = (currentSpeechAccumulatedText + " " + liveText).trim() },
        onFinalResult = { fullText -> _liveTranscript.value = (currentSpeechAccumulatedText + " " + fullText).trim() },
        onError = { errorMsg -> _statusMessage.value = errorMsg }
    )

    private var timerJob: Job? = null

    fun toggleTimer() {
        if (_isRecording.value) stopRecordingLocally()
        else startRecording()
    }

    private fun startRecording() {
        _isRecording.value = true
        _statusMessage.value = "Listening..."
        speechManager.startListening()

        timerJob = viewModelScope.launch {
            while (_timeRemaining.value > 0) {
                delay(1000L)
                _timeRemaining.value -= 1
            }
            advanceToNextSpeech() // Auto-vote next when timer dies
        }
    }

    private fun stopRecordingLocally() {
        timerJob?.cancel()
        _isRecording.value = false
        speechManager.stopListening()

        currentSpeechAccumulatedText = _liveTranscript.value.trim()
        if (currentSpeechAccumulatedText.isNotBlank()) {
            val currentFlows = _flowBoardData.value.toMutableList()
            val index = currentFlows.indexOfFirst { it.speechName == _currentSpeech.value }
            if (index != -1) currentFlows[index] = currentFlows[index].copy(rawText = currentSpeechAccumulatedText)
            else currentFlows.add(FlowColumn(_currentSpeech.value, emptyList(), currentSpeechAccumulatedText))
            _flowBoardData.value = currentFlows
        }
        _statusMessage.value = "Paused."
    }

    fun advanceToNextSpeech() {
        if (_isRecording.value) stopRecordingLocally()

        if (currentSpeechAccumulatedText.isNotBlank()) {
            submitToServer(currentSpeechAccumulatedText, _currentSpeech.value)
        }

        val role = _localPlayerRole.value ?: return
        viewModelScope.launch {
            firebaseRepository.voteNextPhase(_activeJoinCode.value, role)
        }
    }

    private fun submitToServer(transcript: String, targetSpeech: String) {
        _statusMessage.value = "Processing cards via AI..."
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val systemPrompt = """
                    You are a policy debate expert parsing a speech transcript. Extract each discrete argument or evidence card read in the speech. Return ONLY a raw JSON array matching this exact format:
                    [{"argument_tag":  "short label", "content_text":  "the argument", "source": "author", "is_dropped": false}]
                """.trimIndent()

                val request = GeminiRequest(
                    systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(text = systemPrompt))),
                    contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = "Speech: $targetSpeech\n\nTranscript:\n$transcript"))))
                )

                val response = RetrofitClient.apiService.analyzeSpeech(RetrofitClient.GEMINI_API_KEY, request)
                if (response.isSuccessful) {
                    val rawJson = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim()
                    if (!rawJson.isNullOrBlank()) {
                        val newCards: List<ArgumentCard> = Gson().fromJson(rawJson, object : TypeToken<List<ArgumentCard>>() {}.type)
                        firebaseRepository.pushCardsToFirebase(_activeJoinCode.value, targetSpeech, transcript, newCards)
                        _statusMessage.value = "Cards generated!"
                    } else _statusMessage.value = "AI found no distinct cards."
                } else _statusMessage.value = "API Error: ${response.code()}"
            } catch (e: Exception) {
                _statusMessage.value = "Network error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun generateSummary() { /* Keep existing summary code exactly as is */ }
    fun resetDebate() { /* Keep existing reset code exactly as is */ }
    override fun onCleared() { super.onCleared(); speechManager.stopListening() }
}