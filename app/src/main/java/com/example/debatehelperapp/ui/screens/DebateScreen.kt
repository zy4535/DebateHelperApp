package com.example.debatehelperapp.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debatehelperapp.R
import com.example.debatehelperapp.models.FlowColumn
import com.example.debatehelperapp.ui.components.ArgumentCardView
import com.example.debatehelperapp.viewmodel.DebateViewModel
import kotlinx.coroutines.delay

@Composable
fun DebateScreen(viewModel: DebateViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    when (currentScreen) {
        "SPLASH" -> SplashScreen(viewModel)
        "START" -> StartScreen(viewModel)
        "LOBBY" -> LobbyScreen(viewModel)
        "ROLE_SELECT" -> RoleSelectionScreen(viewModel)
        "SETUP" -> SetupScreen(viewModel)
        "DEBATE" -> ActiveDebateScreen(viewModel)
    }
}

// --- KEEP START, LOBBY, AND SPLASH SCREENS EXACTLY THE SAME ---
@Composable
fun StartScreen(viewModel: DebateViewModel) {
    var joinCodeInput by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(text = "Create a Room", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.hostNewDebate() }, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))) { Text("Host New Debate", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.weight(1f))
                Text(" OR ", color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(48.dp))
            Text(text = "Join an Existing Room", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = joinCodeInput, onValueChange = { joinCodeInput = it.uppercase().take(6) }, label = { Text("Enter 6-Digit Join Code", color = Color.Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF1E293B), unfocusedContainerColor = Color(0xFF1E293B)))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.joinDebateRoom(joinCodeInput) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), enabled = joinCodeInput.length == 6) { Text("Join Debate", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun LobbyScreen(viewModel: DebateViewModel) {
    val joinCode by viewModel.activeJoinCode.collectAsState()
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Waiting for Opponent...", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            Text("Your Room Code:", color = Color.Gray, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = joinCode, color = Color(0xFF3B82F6), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp)
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = Color(0xFF10B981))
        }
    }
}

@Composable
fun SplashScreen(viewModel: DebateViewModel) {
    LaunchedEffect(Unit) { delay(2000L); viewModel.navigateTo("START") }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = R.drawable.flow_logo), contentDescription = "Flow Form Logo", modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Flow Form", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
    }
}


// --- THE NEW 4-ROLE SELECTOR ---

@Composable
fun RoleSelectionScreen(viewModel: DebateViewModel) {
    val roomState by viewModel.roomState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Choose Your Role", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))

            // DEBATERS
            Button(onClick = { viewModel.claimRole("AFF") }, enabled = !roomState.affClaimed, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA), disabledContainerColor = Color(0xFF334155))) {
                Text(if (roomState.affClaimed) "Affirmative (Taken)" else "Affirmative", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.claimRole("NEG") }, enabled = !roomState.negClaimed, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171), disabledContainerColor = Color(0xFF334155))) {
                Text(if (roomState.negClaimed) "Negative (Taken)" else "Negative", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(modifier = Modifier.height(32.dp))

            // VIEWERS
            Button(onClick = { viewModel.claimRole("JUDGE") }, enabled = !roomState.judgeClaimed, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6), disabledContainerColor = Color(0xFF334155))) {
                Text(if (roomState.judgeClaimed) "Judge (Taken)" else "Judge Round", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.claimRole("SPECTATOR") }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563))) {
                Text("Spectate Live", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SetupScreen(viewModel: DebateViewModel) {
    val role by viewModel.localPlayerRole.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val docText by viewModel.documentText.collectAsState()

    val isReady = if (role == "AFF") roomState.affReady else roomState.negReady

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(16.dp)) {
        Text("Pre-Round Setup ($role)", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = docText,
            onValueChange = { viewModel.documentText.value = it },
            label = { Text(if (role == "AFF") "Paste Affirmative (1AC) Document" else "Paste Negative (1NC) Document", color = Color.Gray) },
            enabled = !isReady,
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF1E293B), unfocusedContainerColor = Color(0xFF1E293B))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.submitSetupReady() },
            enabled = !isReady,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), disabledContainerColor = Color(0xFF334155))
        ) {
            Text(if (isReady) "Waiting on opponent..." else "Ready", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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

    val role by viewModel.localPlayerRole.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val joinCode by viewModel.activeJoinCode.collectAsState()

    var selectedColumnForDialog by remember { mutableStateOf<FlowColumn?>(null) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    // Lock controls for non-debaters
    val isMyTurn = remember(currentSpeech, role) {
        if (role == "SPECTATOR" || role == "JUDGE") false
        else if (currentSpeech.contains("Cross-Ex") || currentSpeech == "Debate Over") true
        else if (role == "AFF") currentSpeech.contains("A")
        else if (role == "NEG") currentSpeech.contains("N")
        else false
    }

    val isWaitingForNext = (role == "AFF" && roomState.affWantsNext) || (role == "NEG" && roomState.negWantsNext)

    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) viewModel.toggleTimer() else Toast.makeText(context, "Microphone permission is required!", Toast.LENGTH_LONG).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {

            // --- TOP BAR ---
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "Current Speech ($role)", color = Color.Gray, fontSize = 12.sp)
                    Text(text = currentSpeech, color = Color(0xFFFBBF24), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                // JOIN CODE DISPLAYED HERE
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Code: $joinCode", color = Color.Gray, fontSize = 12.sp)
                    Text(text = timerText, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            // --- LIVE TRANSCRIPT ---
            if (isRecording || liveTranscript.isNotBlank() || statusMessage.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).background(Color(0xFF1E293B), shape = RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecording) { Box(modifier = Modifier.size(10.dp).background(Color.Red, shape = RoundedCornerShape(50))); Spacer(modifier = Modifier.width(8.dp)) }
                        Text(text = if (isRecording) "Listening…" else "System Status", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isRecording || liveTranscript.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 120.dp).verticalScroll(rememberScrollState())) {
                            Text(text = if (liveTranscript.isBlank() && isRecording) "Start speaking…" else liveTranscript, color = if (liveTranscript.isBlank()) Color.Gray else Color.White, fontSize = 14.sp, fontStyle = if (liveTranscript.isBlank()) FontStyle.Italic else FontStyle.Normal, lineHeight = 20.sp)
                        }
                    }
                    if (statusMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = statusMessage, color = if (statusMessage.startsWith("Error") || statusMessage.startsWith("Network")) Color(0xFFEF4444) else Color(0xFF34D399), fontSize = 12.sp)
                    }
                }
            }

            // --- FLOW BOARD ---
            val affFlows = flows.filter { it.speechName.contains("A", ignoreCase = true) }
            val negFlows = flows.filter { it.speechName.contains("N", ignoreCase = true) }

            Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1E293B), shape = RoundedCornerShape(8.dp)).padding(8.dp)) {
                    item { Text(text = "AFFIRMATIVE", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)) }
                    affFlows.forEach { flowColumn ->
                        item {
                            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF334155), shape = RoundedCornerShape(4.dp)).clickable { selectedColumnForDialog = flowColumn }.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = flowColumn.speechName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(text = "Source 📄", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(flowColumn.arguments) { card -> ArgumentCardView(card = card) }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1E293B), shape = RoundedCornerShape(8.dp)).padding(8.dp)) {
                    item { Text(text = "NEGATIVE", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)) }
                    negFlows.forEach { flowColumn ->
                        item {
                            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF334155), shape = RoundedCornerShape(4.dp)).clickable { selectedColumnForDialog = flowColumn }.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {

                if (currentSpeech == "Debate Over") {
                    if (role == "JUDGE" && roomState.judgeSummary.isBlank()) {
                        // THE JUDGE'S FINAL RFD INPUT
                        var judgeInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = judgeInput, onValueChange = { judgeInput = it },
                            label = { Text("Write your RFD here...", color = Color.Gray) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF1E293B), unfocusedContainerColor = Color(0xFF1E293B))
                        )
                        Button(onClick = { viewModel.submitJudgeSummary(judgeInput) }, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))) {
                            Text("Submit RFD", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // EVERYONE ELSE VIEWING THE END
                        Button(
                            onClick = {
                                if (roomState.judgeSummary.isNotBlank() || summaryText.isNotBlank()) showSummaryDialog = true
                                else viewModel.generateSummary()
                            },
                            modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)), enabled = !isProcessing
                        ) {
                            Text(text = if (roomState.judgeSummary.isNotBlank()) "View Judge's RFD" else "View AI Summary", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        if (role == "AFF" || role == "NEG") {
                            Button(onClick = { viewModel.resetDebate() }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                                Text("Reset App", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // LIVE DEBATE CONTROLS
                    if (role == "AFF" || role == "NEG") {
                        Button(
                            onClick = { if (isRecording) viewModel.toggleTimer() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else Color(0xFF3B82F6), disabledContainerColor = Color(0xFF334155)),
                            enabled = !isProcessing && isMyTurn
                        ) {
                            Text(text = if (isRecording) "Stop" else "Record", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (!isMyTurn) Color.DarkGray else Color.White)
                        }
                        Button(
                            onClick = { viewModel.advanceToNextSpeech() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563), disabledContainerColor = Color(0xFF1E293B)),
                            enabled = !isWaitingForNext
                        ) {
                            Text(text = if (isWaitingForNext) "Waiting for opponent..." else "Next Speech ⏭", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isWaitingForNext) Color.Gray else Color.White)
                        }
                    } else {
                        // SPECTATORS/JUDGES SEE THIS INSTEAD OF BUTTONS
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("You are viewing live as $role.", color = Color.Gray, fontStyle = FontStyle.Italic)
                        }
                    }
                }
            }
        }

        // --- WAITING FOR DEBATERS OVERLAY ---
        if ((role == "SPECTATOR" || role == "JUDGE") && (!roomState.affReady || !roomState.negReady)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF60A5FA))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Waiting for Debaters to Setup...", color = Color.White, fontSize = 18.sp)
                }
            }
        }

        if (isProcessing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF60A5FA), strokeWidth = 6.dp, modifier = Modifier.size(64.dp))
            }
        }

        if (selectedColumnForDialog != null) {
            AlertDialog(onDismissRequest = { selectedColumnForDialog = null }, containerColor = Color(0xFF1E293B), title = { Text(text = "${selectedColumnForDialog!!.speechName} Source Text", color = Color.White, fontWeight = FontWeight.Bold) }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(text = selectedColumnForDialog!!.rawText.ifBlank { "No text available." }, color = Color.LightGray, lineHeight = 22.sp) } }, confirmButton = { TextButton(onClick = { selectedColumnForDialog = null }) { Text("Close", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold) } })
        }

        if (showSummaryDialog) {
            val dialogText = if (roomState.judgeSummary.isNotBlank()) roomState.judgeSummary else summaryText
            AlertDialog(onDismissRequest = { showSummaryDialog = false }, containerColor = Color(0xFF1E293B), title = { Text(text = if (roomState.judgeSummary.isNotBlank()) "Judge's RFD" else "AI Summary", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold) }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(text = dialogText, color = Color.White, lineHeight = 22.sp) } }, confirmButton = { TextButton(onClick = { showSummaryDialog = false }) { Text("Close", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold) } })
        }
    }
}