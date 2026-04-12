package com.example.debatehelperapp.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.io.IOException

// --- 1. THE MAGIC FIX: AUTOMATIC RETRY ENGINE ---
// This silently catches 503 errors and forces the app to try again without crashing
class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0

        // If Google says the server is overloaded (503), we wait and retry!
        while (!response.isSuccessful && response.code == 503 && tryCount < maxRetries) {
            tryCount++
            response.close() // Clear the failed network call from memory

            // Exponential Backoff: Wait 2s, then 4s, then 6s before knocking again
            Thread.sleep((2000 * tryCount).toLong())

            response = chain.proceed(request)
        }

        return response
    }
}

// --- 2. YOUR NETWORK CLIENT ---
object RetrofitClient {

    // PASTE YOUR ACTUAL API KEY HERE
    const val GEMINI_API_KEY = "AIzaSyAn-J4W6k9DDcx7nmpALoPGUNTsmQNUOps"

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor()) // <-- 3. WE ATTACH THE RETRY ENGINE HERE
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: DebateApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DebateApiService::class.java)
    }
}