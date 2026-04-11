package com.example.debatehelperapp.models

import com.google.gson.annotations.SerializedName

// Represents a single argument card on the flow board
data class ArgumentCard(
    @SerializedName("argument_tag")
    val argumentTag: String,

    @SerializedName("content_text")
    val contentText: String,

    @SerializedName("source")
    val source: String,

    @SerializedName("is_dropped")
    val isDropped: Boolean,

    @SerializedName("responds_to_id")
    val respondsToId: String? = null // Optional: for drawing arrows between cards later
)

// Represents one column on the screen (e.g., the 1AC column)
data class FlowColumn(
    @SerializedName("speech_name")
    val speechName: String,

    @SerializedName("arguments")
    val arguments: List<ArgumentCard>
)

// The overall state of the debate returned by the Python backend
data class DebateStateResponse(
    @SerializedName("current_speech")
    val currentSpeech: String,

    @SerializedName("flows")
    val flows: List<FlowColumn>
)

// The data we send TO the Python backend
data class SpeechUploadRequest(
    val text: String,
    val speech_phase: String
)