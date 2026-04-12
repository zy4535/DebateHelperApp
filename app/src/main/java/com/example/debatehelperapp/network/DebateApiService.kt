package com.example.debatehelperapp.network

import com.example.debatehelperapp.models.GeminiRequest
import com.example.debatehelperapp.models.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface DebateApiService {

    // Pointing directly to the Gemini 1.5 Flash endpoint
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun analyzeSpeech(
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}