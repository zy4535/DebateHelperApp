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
import com.example.debatehelperapp.network.RetrofitClient
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

    // The strict Policy Debate sequence and time limits
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

    // --- STATE FLOWS ---
    private val _currentSpeech = MutableStateFlow(debateSequence[currentPhaseIndex].name)
    val currentSpeech: StateFlow<String> = _currentSpeech.asStateFlow()

    private val _timeRemaining = MutableStateFlow(debateSequence[currentPhaseIndex].minutes * 60)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Controls the dark loading spinner overlay in DebateScreen.kt
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _flowBoardData = MutableStateFlow<List<FlowColumn>>(emptyList())
    val flowBoardData: StateFlow<List<FlowColumn>> = _flowBoardData.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // --- MICROPHONE MANAGER ---
    private val speechManager = SpeechRecognitionManager(
        context = application.applicationContext,
        onPartialResult = { liveText -> _liveTranscript.value = liveText },
        onFinalResult = { fullText -> _liveTranscript.value = fullText },
        onError = { errorMsg -> _statusMessage.value = errorMsg }
    )

    private var timerJob: Job? = null

    init {
        loadDummyData()
    }

    // --- TIMER & RECORDING LOGIC ---
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

        speechManager.startListening()

        timerJob = viewModelScope.launch {
            while (_timeRemaining.value > 0) {
                delay(1000L)
                _timeRemaining.value -= 1
            }
            stopRecordingAndSubmit()
        }
    }

    private fun stopRecordingAndSubmit() {
        timerJob?.cancel()
        _isRecording.value = false

        // Grab the final transcribed text from Android's SpeechRecognizer
        val finalTranscript = speechManager.stopListening()

        if (finalTranscript.isNotBlank()) {
            submitToServer(finalTranscript)
        } else {
            _statusMessage.value = "No speech detected."
            advanceToNextSpeech()
        }
    }

    // --- DIRECT GEMINI API INTEGRATION ---
    private fun submitToServer(transcript: String) {
        _statusMessage.value = "Sending transcript to Gemini 1.5 Flash..."
        _isProcessing.value = true // Show the loading spinner

        viewModelScope.launch {
            try {
                // 1. Build the strict JSON instructions for Gemini
                val systemPrompt = """
                    You are a policy debate expert parsing a speech transcript.
                    Extract each discrete argument or evidence card read in the speech.
                    
                    Return ONLY a raw JSON array matching this exact format. Do not include markdown formatting, backticks, or explanations:
                    [
                      {
                        "argument_tag":  "short label/claim",
                        "content_text":  "the argument or evidence",
                        "source":        "author name and year if cited, else 'Analytic'",
                        "is_dropped":    false
                      }
                    ]
                """.trimIndent()

                val userMessage = "Speech phase: ${_currentSpeech.value}\n\nTranscript:\n$transcript"

                // 2. Package the request in Gemini's specific shape
                val request = GeminiRequest(
                    systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(text = systemPrompt))),
                    contents = listOf(
                        GeminiContent(role = "user", parts = listOf(GeminiPart(text = userMessage)))
                    )
                )

                // 3. Send to Google
                val response = RetrofitClient.apiService.analyzeSpeech(request)

                if (response.isSuccessful) {
                    val body = response.body()

                    // Navigate through Gemini's nested response to get the actual text
                    val responseText = body?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                    if (!responseText.isNullOrBlank()) {

                        // 4. Clean the response (strip formatting if it disobeyed instructions)
                        var rawJson = responseText.trim()
                        rawJson = rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

                        // 5. Convert JSON string back into Kotlin ArgumentCard objects
                        val listType = object : TypeToken<List<ArgumentCard>>() {}.type
                        val newCards: List<ArgumentCard> = Gson().fromJson(rawJson, listType)

                        // 6. Push directly to the UI
                        addAiArguments(_currentSpeech.value, newCards)
                        _statusMessage.value = "Flow successfully updated!"
                    } else {
                        _statusMessage.value = "AI returned an empty response."
                    }
                } else {
                    _statusMessage.value = "API Error: ${response.code()}"
                    android.util.Log.e("DebateApp", "Error Body: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                _statusMessage.value = "Network error: ${e.localizedMessage}"
                android.util.Log.e("DebateApp", "Exception", e)
            } finally {
                // Always turn off the loading spinner and advance the round, even if it fails
                _isProcessing.value = false
                advanceToNextSpeech()
            }
        }
    }

    // --- DEBATE PROGRESSION LOGIC ---
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

    // --- UI UPDATER ---
    private fun addAiArguments(speechName: String, newCards: List<ArgumentCard>) {
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

    // Dummy data so you aren't staring at a blank screen while testing
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