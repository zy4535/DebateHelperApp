package com.example.debatehelperapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.debatehelperapp.models.ArgumentCard
import com.example.debatehelperapp.models.FlowColumn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Defines a single phase in the debate
data class DebatePhase(val name: String, val minutes: Int)

class DebateViewModel : ViewModel() {

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

    private val _flowBoardData = MutableStateFlow<List<FlowColumn>>(emptyList())
    val flowBoardData: StateFlow<List<FlowColumn>> = _flowBoardData.asStateFlow()

    private var timerJob: Job? = null

    init {
        // We still load dummy data so your UI isn't empty while testing
        loadDummyData()
    }

    // --- TIMER & PROGRESSION LOGIC ---
    fun toggleTimer() {
        if (_isRecording.value) {
            timerJob?.cancel()
            _isRecording.value = false
        } else {
            _isRecording.value = true
            timerJob = viewModelScope.launch {
                while (_timeRemaining.value > 0) {
                    delay(1000L)
                    _timeRemaining.value -= 1
                }
                _isRecording.value = false
                // Auto-advance when timer hits 0
                advanceToNextSpeech()
            }
        }
    }

    fun advanceToNextSpeech() {
        timerJob?.cancel()
        _isRecording.value = false

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

    // --- Branden here is where you come in on this file ---
    // when you get the JSON from Python, they will call this function to update the UI
    fun addAiArguments(speechName: String, newCards: List<ArgumentCard>) {
        val currentFlows = _flowBoardData.value.toMutableList()

        // Find if we already have a column for this speech
        val existingColIndex = currentFlows.indexOfFirst { it.speechName == speechName }

        if (existingColIndex != -1) {
            // Add new cards to existing column
            val oldColumn = currentFlows[existingColIndex]
            val updatedCards = oldColumn.arguments + newCards
            currentFlows[existingColIndex] = oldColumn.copy(arguments = updatedCards)
        } else {
            // Create a brand new column
            currentFlows.add(FlowColumn(speechName, newCards))
        }

        // Push the update to the UI
        _flowBoardData.value = currentFlows
    }

    // Dummy data just to keep the UI looking nice while you build
    private fun loadDummyData() {
        val dummyCards = listOf(
            ArgumentCard("Advantage", "Economic Growth: Plan stimulates economy", "Smith '23", false),
            ArgumentCard("Impact", "Prevents recession - creates 2M jobs", "Jones '22", true)
        )
        _flowBoardData.value = listOf(FlowColumn("1AC", dummyCards))
    }
}