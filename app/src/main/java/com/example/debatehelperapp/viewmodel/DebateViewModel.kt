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

class DebateViewModel : ViewModel() {

    // --- STATE FLOWS (The UI listens to these) ---
    private val _timeRemaining = MutableStateFlow(8 * 60) // 8 minutes in seconds
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _currentSpeech = MutableStateFlow("1AC")
    val currentSpeech: StateFlow<String> = _currentSpeech.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _flowBoardData = MutableStateFlow<List<FlowColumn>>(emptyList())
    val flowBoardData: StateFlow<List<FlowColumn>> = _flowBoardData.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Load dummy data immediately so you can see your UI working!
        loadDummyData()
    }

    // --- TIMER LOGIC ---
    fun toggleTimer() {
        if (_isRecording.value) {
            timerJob?.cancel()
            _isRecording.value = false
        } else {
            _isRecording.value = true
            timerJob = viewModelScope.launch {
                while (_timeRemaining.value > 0) {
                    delay(1000L) // Wait 1 second
                    _timeRemaining.value -= 1
                }
                _isRecording.value = false // Stop when hit 0
            }
        }
    }

    // --- DUI TEST DATA ---
    private fun loadDummyData() {
        val dummyCards = listOf(
            ArgumentCard("Advantage", "Economic Growth: Plan stimulates economy", "Smith '23", false),
            ArgumentCard("Impact", "Prevents recession - creates 2M jobs", "Jones '22", true)
        )
        val dummyColumn = FlowColumn("1AC", dummyCards)

        val negativeCards = listOf(
            ArgumentCard("Turn", "Spending DA: Increases inflation", "Brown '23", false)
        )
        val negColumn = FlowColumn("1NC", negativeCards)

        _flowBoardData.value = listOf(dummyColumn, negColumn)
    }
}