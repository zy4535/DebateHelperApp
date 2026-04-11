package com.example.debatehelperapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debatehelperapp.ui.components.ArgumentCardView
import com.example.debatehelperapp.viewmodel.DebateViewModel

@Composable
fun DebateScreen(viewModel: DebateViewModel) {
    // Collect state from the ViewModel
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val currentSpeech by viewModel.currentSpeech.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val flows by viewModel.flowBoardData.collectAsState()

    // Format timer (e.g., "08:00")
    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Dark background
    ) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Current Speech", color = Color.Gray, fontSize = 12.sp)
                Text(text = currentSpeech, color = Color(0xFFFBBF24), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Text(text = timerText, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }

        // --- FLOW BOARD (Horizontal Scroll) ---
        LazyRow(
            modifier = Modifier
                .weight(1f) // Takes up remaining middle space
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            items(flows) { flowColumn ->
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                        .background(Color(0xFF1E293B))
                        .padding(8.dp)
                ) {
                    Text(
                        text = flowColumn.speechName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Cards inside the column (Vertical Scroll)
                    LazyColumn {
                        items(flowColumn.arguments) { card ->
                            ArgumentCardView(card = card)
                        }
                    }
                }
            }
        }

        // --- BOTTOM CONTROLS ---
        Button(
            onClick = { viewModel.toggleTimer() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else Color(0xFF3B82F6)
            )
        ) {
            Text(
                text = if (isRecording) "Stop Recording & Speech" else "Start Speech & Record",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}