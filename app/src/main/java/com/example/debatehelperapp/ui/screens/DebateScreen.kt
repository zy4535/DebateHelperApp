package com.example.debatehelperapp.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debatehelperapp.models.FlowColumn
import com.example.debatehelperapp.ui.components.ArgumentCardView
import com.example.debatehelperapp.viewmodel.DebateViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import com.example.debatehelperapp.R
import androidx.compose.foundation.Image
// --- THE ROUTER ---
@Composable
fun DebateScreen(viewModel: DebateViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    // Added the "SPLASH" route to the top of the stack
    when (currentScreen) {
        "SPLASH" -> SplashScreen(viewModel)
        "START" -> StartScreen(viewModel)
        "SETUP" -> SetupScreen(viewModel)
        "DEBATE" -> ActiveDebateScreen(viewModel)
    }
}

@Composable
fun StartScreen(viewModel: DebateViewModel) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { viewModel.navigateTo("SETUP") },
            modifier = Modifier.height(64.dp).padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
        ) {
            Text("Start New Debate", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SetupScreen(viewModel: DebateViewModel) {
    val affText by viewModel.affDocumentText.collectAsState()
    val negText by viewModel.negDocumentText.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(16.dp)
    ) {
        Text("Pre-Round Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = affText,
            onValueChange = { viewModel.affDocumentText.value = it },
            label = { Text("Paste Affirmative (1AC) Document", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = negText,
            onValueChange = { viewModel.negDocumentText.value = it },
            label = { Text("Paste Negative (1NC) Document", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.startDebateFromSetup() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text("Launch Debate Board", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActiveDebateScreen(viewModel: DebateViewModel) {
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val currentSpeech by viewModel.currentSpeech.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val flows by viewModel.flowBoardData.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val summaryText by viewModel.summaryText.collectAsState()

    var selectedColumnForDialog by remember { mutableStateOf<FlowColumn?>(null) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    // Auto-show summary when it finishes generating
    LaunchedEffect(summaryText) {
        if (summaryText.isNotBlank()) {
            showSummaryDialog = true
        }
    }

    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) viewModel.toggleTimer()
        else Toast.makeText(context, "Microphone permission is required!", Toast.LENGTH_LONG).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {

            // --- TOP BAR ---
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Current Speech", color = Color.Gray, fontSize = 12.sp)
                    Text(text = currentSpeech, color = Color(0xFFFBBF24), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text(text = timerText, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }

            // --- LIVE TRANSCRIPT BOX ---
            if (isRecording || liveTranscript.isNotBlank() || statusMessage.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(Color(0xFF1E293B), shape = RoundedCornerShape(8.dp)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecording) {
                            Box(modifier = Modifier.size(10.dp).background(Color.Red, shape = RoundedCornerShape(50)))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = if (isRecording) "Listening…" else "System Status", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isRecording || liveTranscript.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 120.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                text = if (liveTranscript.isBlank() && isRecording) "Start speaking…" else liveTranscript,
                                color = if (liveTranscript.isBlank()) Color.Gray else Color.White,
                                fontSize = 14.sp,
                                fontStyle = if (liveTranscript.isBlank()) FontStyle.Italic else FontStyle.Normal,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    if (statusMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage,
                            color = if (statusMessage.startsWith("Error") || statusMessage.startsWith("Network")) Color(0xFFEF4444) else Color(0xFF34D399),
                            fontSize = 12.sp
                        )
                    }
                }
            }

// --- 2-COLUMN FLOW BOARD ---
            // Automatically sort speeches by Affirmative ("A") and Negative ("N")
            val affFlows = flows.filter { it.speechName.contains("A", ignoreCase = true) }
            val negFlows = flows.filter { it.speechName.contains("N", ignoreCase = true) }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- AFFIRMATIVE COLUMN (Left Half) ---
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF1E293B), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    item {
                        Text(
                            text = "AFFIRMATIVE",
                            color = Color(0xFF60A5FA), // Blue
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                    }
                    affFlows.forEach { flowColumn ->
                        item {
                            // Sub-header for 1AC, 2AC, etc.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF334155), shape = RoundedCornerShape(4.dp))
                                    .clickable { selectedColumnForDialog = flowColumn }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = flowColumn.speechName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(text = "Source 📄", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(flowColumn.arguments) { card -> ArgumentCardView(card = card) }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                // --- NEGATIVE COLUMN (Right Half) ---
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF1E293B), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    item {
                        Text(
                            text = "NEGATIVE",
                            color = Color(0xFFF87171), // Red
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                    }
                    negFlows.forEach { flowColumn ->
                        item {
                            // Sub-header for 1NC, 2NC, etc.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF334155), shape = RoundedCornerShape(4.dp))
                                    .clickable { selectedColumnForDialog = flowColumn }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = flowColumn.speechName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(text = "Source 📄", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(flowColumn.arguments) { card -> ArgumentCardView(card = card) }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }

            // --- BOTTOM CONTROLS ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Determine layout based on whether the debate is over
                if (currentSpeech == "Debate Over") {
                    Button(
                        onClick = {
                            if (summaryText.isNotBlank()) showSummaryDialog = true
                            else viewModel.generateSummary()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)), // Purple
                        enabled = !isProcessing
                    ) {
                        Text(text = if (summaryText.isNotBlank()) "View RFD Summary" else "Judge Round", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.resetDebate() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)) // Red
                    ) {
                        Text(text = "Reset App", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (isRecording) viewModel.toggleTimer()
                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else Color(0xFF3B82F6)),
                        enabled = !isProcessing
                    ) {
                        Text(text = if (isRecording) "Stop" else "Record", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.advanceToNextSpeech() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563))
                    ) {
                        Text(text = "Next Speech ⏭", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- PROCESSING SPINNER ---
        if (isProcessing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF60A5FA), strokeWidth = 6.dp, modifier = Modifier.size(64.dp))
            }
        }

        // --- DIALOG POPUPS ---

        // 1. Source Text Dialog
        if (selectedColumnForDialog != null) {
            AlertDialog(
                onDismissRequest = { selectedColumnForDialog = null },
                containerColor = Color(0xFF1E293B),
                title = { Text(text = "${selectedColumnForDialog!!.speechName} Source Text", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = selectedColumnForDialog!!.rawText.ifBlank { "No text available." }, color = Color.LightGray, lineHeight = 22.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedColumnForDialog = null }) {
                        Text("Close", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 2. Summary/RFD Dialog
        if (showSummaryDialog && summaryText.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { showSummaryDialog = false },
                containerColor = Color(0xFF1E293B),
                title = { Text(text = "Round Summary & RFD", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = summaryText, color = Color.White, lineHeight = 22.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSummaryDialog = false }) {
                        Text("Close", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }

}
@Composable
fun SplashScreen(viewModel: DebateViewModel) {

    LaunchedEffect(Unit) {
        delay(2000L)
        viewModel.navigateTo("START")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- SWAP OUT ONLY THIS SECTION ---
            // We removed the Box { Text("F") } and put the Image here instead
            Image(
                painter = painterResource(id = R.drawable.flow_logo), // Your actual logo file
                contentDescription = "Flow Form Logo",
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Keep your app name underneath the logo
            Text(
                text = "Flow Form",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}