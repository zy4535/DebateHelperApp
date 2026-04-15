package com.example.debatehelperapp.repository

import com.example.debatehelperapp.models.ArgumentCard
import com.example.debatehelperapp.models.FlowColumn
import com.example.debatehelperapp.models.RoomState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun generateJoinCode(): String {
        val allowedChars = ('A'..'Z') + ('0'..'9')
        return (1..6).map { allowedChars.random() }.joinToString("")
    }

    // --- LOBBY & ROLE LOGIC ---

    suspend fun createDebateRoom(): String {
        val joinCode = generateJoinCode()
        db.collection("DebateRooms").document(joinCode).set(RoomState()).await()
        return joinCode
    }

    suspend fun markRoomActive(joinCode: String) {
        db.collection("DebateRooms").document(joinCode).update("status", "ACTIVE").await()
    }

    suspend fun claimRole(joinCode: String, role: String) {
        // Spectators don't claim a unique database slot, so we skip them
        val field = when (role) {
            "AFF" -> "affClaimed"
            "NEG" -> "negClaimed"
            "JUDGE" -> "judgeClaimed"
            else -> return
        }
        db.collection("DebateRooms").document(joinCode).update(field, true).await()
    }

    suspend fun setSetupReady(joinCode: String, role: String, docText: String) {
        val readyField = if (role == "AFF") "affReady" else "negReady"
        val textField = if (role == "AFF") "affDocumentText" else "negDocumentText"

        db.collection("DebateRooms").document(joinCode).update(
            mapOf(readyField to true, textField to docText, "status" to "DEBATING")
        ).await()
    }

    // --- SYNC & PHASE ADVANCEMENT ---

    fun listenToRoomState(joinCode: String, onUpdate: (RoomState) -> Unit) {
        db.collection("DebateRooms").document(joinCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val state = snapshot.toObject(RoomState::class.java) ?: RoomState()
                onUpdate(state)
            }
    }

    suspend fun voteNextPhase(joinCode: String, role: String) {
        val field = if (role == "AFF") "affWantsNext" else "negWantsNext"
        db.collection("DebateRooms").document(joinCode).update(field, true).await()
    }

    suspend fun advancePhase(joinCode: String, newIndex: Int) {
        db.collection("DebateRooms").document(joinCode).update(
            mapOf(
                "currentPhaseIndex" to newIndex,
                "affWantsNext" to false,
                "negWantsNext" to false
            )
        ).await()
    }

    suspend fun submitJudgeSummary(joinCode: String, summary: String) {
        db.collection("DebateRooms").document(joinCode).update("judgeSummary", summary).await()
    }

    // --- DEBATE SYNC LOGIC ---

    fun listenToRoomFlows(joinCode: String, onUpdate: (List<FlowColumn>) -> Unit) {
        db.collection("DebateRooms").document(joinCode).collection("Flows")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val updatedFlows = snapshot.documents.mapNotNull { it.toObject(FlowColumn::class.java) }
                onUpdate(updatedFlows)
            }
    }

    fun pushCardsToFirebase(joinCode: String, speechName: String, rawText: String, newCards: List<ArgumentCard>) {
        val columnData = FlowColumn(speechName, newCards, rawText)
        db.collection("DebateRooms").document(joinCode).collection("Flows").document(speechName).set(columnData)
    }
}