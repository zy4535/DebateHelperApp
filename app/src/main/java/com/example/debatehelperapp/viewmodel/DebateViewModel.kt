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

    private val _currentScreen = MutableStateFlow("START")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    val affDocumentText = MutableStateFlow("")
    val negDocumentText = MutableStateFlow("")

    fun navigateTo(screen: String) { _currentScreen.value = screen }

    fun startDebateFromSetup() {
        navigateTo("DEBATE")
        _flowBoardData.value = emptyList()
        _summaryText.value = ""
        currentSpeechAccumulatedText = ""

        if (affDocumentText.value.isNotBlank()) {
            saveRawTextToColumn("1AC Doc", affDocumentText.value)
            submitToServer(affDocumentText.value, "1AC Doc")
        }
        if (negDocumentText.value.isNotBlank()) {
            saveRawTextToColumn("1NC Doc", negDocumentText.value)
            submitToServer(negDocumentText.value, "1NC Doc")
        }
    }

    private val debateSequence = listOf(
        DebatePhase("1AC", 8), DebatePhase("1AC Cross-Ex", 3),
        DebatePhase("1NC", 8), DebatePhase("1NC Cross-Ex", 3),
        DebatePhase("2AC", 8), DebatePhase("2AC Cross-Ex", 3),
        DebatePhase("2NC", 8), DebatePhase("2NC Cross-Ex", 3),
        DebatePhase("1NR", 5), DebatePhase("1AR", 5),
        DebatePhase("2NR", 5), DebatePhase("2AR", 5)
    )

    private var currentPhaseIndex = 0

    private val _currentSpeech = MutableStateFlow(debateSequence[currentPhaseIndex].name)
    val currentSpeech: StateFlow<String> = _currentSpeech.asStateFlow()

    private val _timeRemaining = MutableStateFlow(debateSequence[currentPhaseIndex].minutes * 60)
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
        onPartialResult = { liveText ->
            _liveTranscript.value = (currentSpeechAccumulatedText + " " + liveText).trim()
        },
        onFinalResult = { fullText ->
            _liveTranscript.value = (currentSpeechAccumulatedText + " " + fullText).trim()
        },
        onError = { errorMsg -> _statusMessage.value = errorMsg }
    )

    private var timerJob: Job? = null

    fun toggleTimer() {
        if (_isRecording.value) stopRecording()
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
            advanceToNextSpeech()
        }
    }

    private fun stopRecording() {
        timerJob?.cancel()
        _isRecording.value = false
        speechManager.stopListening() // Fire and forget

        // BUG FIX: Grab text directly from the screen's memory!
        currentSpeechAccumulatedText = _liveTranscript.value.trim()

        if (currentSpeechAccumulatedText.isNotBlank()) {
            saveRawTextToColumn(_currentSpeech.value, currentSpeechAccumulatedText)
        }
        _statusMessage.value = "Paused."
    }

    fun advanceToNextSpeech() {
        if (_isRecording.value) {
            timerJob?.cancel()
            _isRecording.value = false
            speechManager.stopListening()
            currentSpeechAccumulatedText = _liveTranscript.value.trim()
        }

        val finalSpeechText = currentSpeechAccumulatedText

        if (finalSpeechText.isNotBlank()) {
            saveRawTextToColumn(_currentSpeech.value, finalSpeechText)
            submitToServer(finalSpeechText, _currentSpeech.value)
        }

        // Reset for the next speech phase
        currentSpeechAccumulatedText = ""
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

    private fun saveRawTextToColumn(speechName: String, text: String) {
        if (text.isBlank()) return
        val currentFlows = _flowBoardData.value.toMutableList()
        val index = currentFlows.indexOfFirst { it.speechName == speechName }
        if (index != -1) {
            val old = currentFlows[index]
            currentFlows[index] = old.copy(rawText = text)
        } else {
            currentFlows.add(FlowColumn(speechName, emptyList(), text))
        }
        _flowBoardData.value = currentFlows
    }

    private fun addAiArguments(speechName: String, newCards: List<ArgumentCard>) {
        if (newCards.isEmpty()) return
        val currentFlows = _flowBoardData.value.toMutableList()
        val index = currentFlows.indexOfFirst { it.speechName == speechName }
        if (index != -1) {
            val old = currentFlows[index]
            currentFlows[index] = old.copy(arguments = old.arguments + newCards)
        } else {
            currentFlows.add(FlowColumn(speechName, newCards, ""))
        }
        _flowBoardData.value = currentFlows
    }

    private fun submitToServer(transcript: String, targetSpeech: String) {
        _statusMessage.value = "Processing cards via AI..."
        _isProcessing.value = true

        viewModelScope.launch {
            try {
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

                val userMessage = "Speech phase: $targetSpeech\n\nTranscript:\n$transcript"
                val request = GeminiRequest(
                    systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(text = systemPrompt))),
                    contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = userMessage))))
                )

                val response = RetrofitClient.apiService.analyzeSpeech(RetrofitClient.GEMINI_API_KEY, request)
                if (response.isSuccessful) {
                    val responseText = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!responseText.isNullOrBlank()) {
                        val rawJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val listType = object : TypeToken<List<ArgumentCard>>() {}.type
                        val newCards: List<ArgumentCard> = Gson().fromJson(rawJson, listType)
                        addAiArguments(targetSpeech, newCards)
                        _statusMessage.value = "Cards generated!"
                    } else {
                        _statusMessage.value = "AI found no distinct cards."
                    }
                } else {
                    _statusMessage.value = "API Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Network error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun generateSummary() {
        _isProcessing.value = true
        _statusMessage.value = "Judging the round..."

        viewModelScope.launch {
            try {
                val flowDataString = _flowBoardData.value.joinToString("\n\n") {
                    "Speech: ${it.speechName}\nRaw Text: ${it.rawText}\nCards Generated: ${it.arguments.size}"
                }

                val prompt = "You are an expert debate judge. Based on the following record of the round, provide a concise RFD (Reason for Decision). Tell me who won, the main clash points, and why.\n\n$flowDataString"

                val request = GeminiRequest(
                    systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(text = "You are an expert debate judge providing clear, concise feedback."))),
                    contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt))))
                )

                val response = RetrofitClient.apiService.analyzeSpeech(RetrofitClient.GEMINI_API_KEY, request)
                if (response.isSuccessful) {
                    val text = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    _summaryText.value = text ?: "Could not generate summary."
                } else {
                    _statusMessage.value = "Error generating summary: ${response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Network error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun resetDebate() {
        currentPhaseIndex = 0
        _currentSpeech.value = debateSequence[0].name
        _timeRemaining.value = debateSequence[0].minutes * 60
        _flowBoardData.value = emptyList()
        _summaryText.value = ""
        affDocumentText.value = ""
        negDocumentText.value = ""
        currentSpeechAccumulatedText = ""
        _liveTranscript.value = ""
        _statusMessage.value = ""
        navigateTo("START")
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.stopListening()
    }
}