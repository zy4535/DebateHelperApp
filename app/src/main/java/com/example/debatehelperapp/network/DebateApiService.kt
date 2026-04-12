package com.example.debatehelperapp.network

import com.example.debatehelperapp.models.GeminiRequest
import com.example.debatehelperapp.models.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface DebateApiService {

    // This is the exact, official path for Gemini 1.5 Flash
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun analyzeSpeech(
        @Query("key") apiKey: String, // Injects your API key into the URL
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

}