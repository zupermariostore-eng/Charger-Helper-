package com.example.api

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GeminiRepository {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val resultAdapter = moshi.adapter(VoiceIntelligenceResult::class.java)

    /**
     * Sends an audio file (recorded via MediaRecorder) to Gemini 3.5 Flash for transcription and intent extraction.
     */
    suspend fun analyzeVoiceCommand(
        audioFile: File,
        presetDeveloperHint: String? = null
    ): VoiceIntelligenceResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // No valid API key provided. Fallback to highly descriptive local processing or alert
            Log.w("GeminiRepository", "Gemini API key is missing or is the default placeholder!")
            throw IllegalStateException("API key missing")
        }

        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        // Give clear instructions under system instructions or content prompt
        val systemPrompt = """
            You are the central voice intelligence system for a Deepal S05 Electric Vehicle (Chinese-spec import) operating in Phnom Penh, Cambodia.
            Your input is an audio file containing spoken Khmer (Cambodian).
            
            DIAGNOSTIC HINT:
            ${if (presetDeveloperHint != null) "The administrator simulated this text input request: '$presetDeveloperHint'. Use this as the accurate transcription if the audio query is unrecognized or blank." else "Listen to the raw audio carefully to transcribe and extract intent."}

            CRITICAL ELECTRICAL VEHICLE CONTEXT:
            The vehicle is a Chinese domestic market model. Therefore, it requires the "GB/T" charging standard, NOT the European "CCS2" standard prevalent in Cambodia. Whenever the user's intent is to find a charger, you must strictly output "GB/T" as the required charger type so the application can filter locations correctly.

            Please determine the user's intent and return a strict JSON response matching this schema:
            {
              "intent": "navigate_ev_charger" | "navigate_general" | "unsupported",
              "charger_type_required": "GB/T" | null,
              "destination_name": "Name of the place in English or Khmer (if intent is navigate_general)" | null,
              "spoken_response_khmer": "Short, friendly Khmer text to reply to the driver (e.g. 'ចាស៎ កំពុងស្វែងរកកន្លែងសាកថ្មប្រភេទ GB/T ជិតបំផុតជូនលោកអ្នក។')",
              "transcribed_khmer_text": "The exact Khmer text for app UI feedback (e.g. 'រកកន្លែងសាកថ្មឡាន' or 'ទៅផ្សារទំនើបអ៊ីអន')"
            }

            Be friendly, professional, and strictly conversational in 'spoken_response_khmer'. Do not use markdown wrappers, codeblocks, or other text outside the JSON.
        """.trimIndent()

        val promptText = "Analyze this Khmer speech audio command. If there is a diagnostic hint, prioritize its transcription. Output strictly valid JSON conforming to the schema."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText),
                        Part(inlineData = InlineData(mimeType = "audio/mp4", data = base64Audio))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json"
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemPrompt))
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response from Gemini API")

            Log.d("GeminiRepository", "Raw Gemini JSON response: $jsonText")

            // Parse response
            resultAdapter.fromJson(jsonText) ?: throw Exception("Failed to parse Gemini response JSON")
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error analyzing voice from Gemini", e)
            throw e
        }
    }
}
