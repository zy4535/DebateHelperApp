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
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@Composable
fun DebateScreen(viewModel: DebateViewModel) {
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val currentSpeech by viewModel.currentSpeech.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val flows by viewModel.flowBoardData.collectAsState()

    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted! Start the timer and recording logic in the ViewModel
            viewModel.toggleTimer()
        } else {
            Toast.makeText(context, "Microphone permission is required to analyze speeches!", Toast.LENGTH_LONG).show()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
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

        // --- FLOW BOARD ---
        LazyRow(
            modifier = Modifier
                .weight(1f)
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

                    LazyColumn {
                        items(flowColumn.arguments) { card ->
                            ArgumentCardView(card = card)
                        }
                    }
                }
            }
        }

        // --- BOTTOM CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isRecording) {
                        // If already recording, just stop it
                        viewModel.toggleTimer()
                    } else {
                        // If NOT recording, ask for permission first!
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else Color(0xFF3B82F6)
                )
            ) {
                Text(
                    text = if (isRecording) "Stop" else "Record",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { viewModel.advanceToNextSpeech() },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563))
            ) {
                Text(
                    text = "Next Speech ⏭",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}