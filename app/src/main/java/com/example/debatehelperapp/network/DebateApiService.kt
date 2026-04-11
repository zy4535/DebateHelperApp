package com.example.debatehelperapp.network

import com.example.debatehelperapp.models.DebateStateResponse
import com.example.debatehelperapp.models.SpeechUploadRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface DebateApiService {

    // Sends the transcribed text to Python and gets the updated flow board back
    // "suspend" means it runs on a background thread so the UI doesn't freeze
    @POST("api/analyze-speech")
    suspend fun analyzeSpeech(
        @Body request: SpeechUploadRequest
    ): Response<DebateStateResponse>

    // You can add more endpoints here later, like uploading a PDF document
}