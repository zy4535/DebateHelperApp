package com.example.debatehelperapp.models

import com.google.gson.annotations.SerializedName

// ─── CORE DEBATE MODELS ─────────────────────────────────────────────────────

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
    val arguments: List<ArgumentCard>,

    // Stores the raw pasted text or spoken transcript so users can review the source
    val rawText: String = ""
)


// ─── GEMINI API MODELS ──────────────────────────────────────────────────────

data class GeminiPart(
    val text: String
)

data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

// What we send to Google's Gemini API
data class GeminiRequest(
    val systemInstruction: GeminiSystemInstruction,
    val contents: List<GeminiContent>
)

// What Google's Gemini API sends back
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)