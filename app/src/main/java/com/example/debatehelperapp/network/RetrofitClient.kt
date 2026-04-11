package com.example.debatehelperapp.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // IMPORTANT HACKATHON NOTE: Change this URL!
    // 1. If testing on Android Emulator to a local python server: "http://10.0.2.2:8000/"
    // 2. If you are using a physical phone, you MUST use your teammate's IP or an ngrok link:
    //    Example: "http://192.168.1.55:8000/" or "https://your-ngrok-url.ngrok-free.app/"

    private const val BASE_URL = "http://10.0.2.2:8000/"

    val apiService: DebateApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // This automatically converts the JSON into your Kotlin Data Classes
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DebateApiService::class.java)
    }
}