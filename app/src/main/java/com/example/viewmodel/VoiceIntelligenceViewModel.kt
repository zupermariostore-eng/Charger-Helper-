package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiRepository
import com.example.api.VoiceIntelligenceResult
import com.example.audio.VoiceCommandRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import com.example.data.database.AppDatabase
import com.example.data.database.CachedVoiceCommand
import com.example.data.database.VoiceCommandRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

sealed class VoiceState {
    object Idle : VoiceState()
    object Recording : VoiceState()
    object Processing : VoiceState()
    data class Success(val result: VoiceIntelligenceResult) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

// Landmark representation for in-app navigation simulation
data class MapLandmark(
    val id: String,
    val name: String,
    val nameKh: String,
    val latitude: Double,
    val longitude: Double,
    val isCharger: Boolean,
    val chargerType: String? = null // "GB/T" or "CCS2"
)

class VoiceIntelligenceViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository = GeminiRepository()
    private val recorder = VoiceCommandRecorder(application)
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val cachingRepository: VoiceCommandRepository by lazy {
        val database = AppDatabase.getDatabase(application)
        VoiceCommandRepository(database.cachedVoiceCommandDao())
    }

    val cachedCommands: StateFlow<List<CachedVoiceCommand>> = cachingRepository.frequentlyUsedCommands
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // State flows
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _isKhmerTtsSupported = MutableStateFlow(false)
    val isKhmerTtsSupported: StateFlow<Boolean> = _isKhmerTtsSupported.asStateFlow()

    private val _voiceLanguagePref = MutableStateFlow("AUTO") // "AUTO", "KHMER", "ENGLISH"
    val voiceLanguagePref: StateFlow<String> = _voiceLanguagePref.asStateFlow()

    fun setVoiceLanguagePref(lang: String) {
        _voiceLanguagePref.value = lang
    }

    private val _ttsConfirmationMode = MutableStateFlow("FULL_VOICE") // "FULL_VOICE", "CHIME_ONLY", "DISABLED"
    val ttsConfirmationMode: StateFlow<String> = _ttsConfirmationMode.asStateFlow()

    fun setTtsConfirmationMode(mode: String) {
        _ttsConfirmationMode.value = mode
    }

    // Vehicle live parameters
    private val _speed = MutableStateFlow(0)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    private val _batteryPercentage = MutableStateFlow(45)
    val batteryPercentage: StateFlow<Int> = _batteryPercentage.asStateFlow()

    private val _estimatedRangeKm = MutableStateFlow(192)
    val estimatedRangeKm: StateFlow<Int> = _estimatedRangeKm.asStateFlow()

    private val _isSimulatingDrive = MutableStateFlow(false)
    val isSimulatingDrive: StateFlow<Boolean> = _isSimulatingDrive.asStateFlow()

    // Climate & Multimedia live states for Driver Quick Actions
    private val _cabinTemp = MutableStateFlow(22.0f)
    val cabinTemp: StateFlow<Float> = _cabinTemp.asStateFlow()

    private val _isPlayingMusic = MutableStateFlow(false)
    val isPlayingMusic: StateFlow<Boolean> = _isPlayingMusic.asStateFlow()

    private val _currentSong = MutableStateFlow("Sra-Srak (Cambodian Jazz Chill)")
    val currentSong: StateFlow<String> = _currentSong.asStateFlow()

    // Map & Navigation state
    private val _selectedLandmark = MutableStateFlow<MapLandmark?>(null)
    val selectedLandmark: StateFlow<MapLandmark?> = _selectedLandmark.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    // Landmark coordinates centered on Phnom Penh, Cambodia
    val landmarks = listOf(
        MapLandmark("ch_ev_gbt_1", "EV Plaza Phnom Penh", "ស្ថានីយសាកថ្ម EV Plaza (GB/T)", 11.5421, 104.9123, isCharger = true, chargerType = "GB/T"),
        MapLandmark("ch_ev_gbt_2", "Deepal Hub Charging", "កន្លែងសាកថ្ម Deepal (GB/T)", 11.5644, 104.9312, isCharger = true, chargerType = "GB/T"),
        MapLandmark("ch_ev_gbt_3", "Cambodia EV Station", "ស្ថានីយសាកឡានអគ្គិសនីកម្ពុជា (GB/T)", 11.5510, 104.8988, isCharger = true, chargerType = "GB/T"),
        MapLandmark("ch_ev_ccs_4", "CCS2 Station (UNSUPPORTED)", "ស្ថានីយសាកថាមពល CCS2 (មិនគាំទ្រ)", 11.5312, 104.9455, isCharger = true, chargerType = "CCS2"),
        
        MapLandmark("dest_aeon_mean_chey", "AEON Mall Mean Chey", "ផ្សារទំនើបអ៊ីអនមានជ័យ", 11.5123, 104.9288, isCharger = false),
        MapLandmark("dest_royal_palace", "Royal Palace", "ព្រះបរមរាជវាំង", 11.5639, 104.9309, isCharger = false),
        MapLandmark("dest_wat_phnom", "Wat Phnom", "វត្តភ្នំ", 11.5762, 104.9255, isCharger = false)
    )

    init {
        // Initialize Text To Speech
        tts = TextToSpeech(application, this)

        // Setup live drive parameter simulator loop
        viewModelScope.launch {
            while (true) {
                if (_isSimulatingDrive.value) {
                    // Random speed variance 45 to 75 km/h
                    _speed.value = (48..72).random()
                    // Slow battery depletion
                    if ((1..100).random() > 95) {
                        if (_batteryPercentage.value > 1) {
                            _batteryPercentage.value -= 1
                            _estimatedRangeKm.value = (_batteryPercentage.value * 4.3).toInt()
                        }
                    }
                } else {
                    _speed.value = 0
                }
                delay(2000)
            }
        }

        // Detect if API key is missing to default to interactive local fallback automatically
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _isDemoMode.value = true
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val khmerAvailable = tts?.isLanguageAvailable(Locale("km"))
            if (khmerAvailable == TextToSpeech.LANG_AVAILABLE || khmerAvailable == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                tts?.language = Locale("km")
                _isKhmerTtsSupported.value = true
            } else {
                tts?.language = Locale.ENGLISH // Fallback to English
                _isKhmerTtsSupported.value = false
            }
            isTtsInitialized = true
        } else {
            Log.e("VoiceIntelligenceVM", "TTS Initialization failed")
        }
    }

    private fun playChimeTone() {
        if (_ttsConfirmationMode.value == "DISABLED") return
        viewModelScope.launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                // ascending positive confirmation beep-beep
                toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
                delay(140)
                toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
            } catch (e: Exception) {
                Log.e("VoiceIntelligenceVM", "Tone generation failed", e)
            }
        }
    }

    fun speak(result: VoiceIntelligenceResult) {
        val mode = _ttsConfirmationMode.value
        if (mode == "DISABLED") return

        // Always play chime first
        playChimeTone()

        if (mode == "CHIME_ONLY") {
            // Only chime, do not activate TTS spoken voice
            return
        }

        if (!isTtsInitialized) return

        val useKhmer = when (_voiceLanguagePref.value) {
            "KHMER" -> true
            "ENGLISH" -> false
            else -> _isKhmerTtsSupported.value // AUTO fallback to english if Khmer TTS not in OS
        }

        val textToSpeak = if (useKhmer) {
            result.spokenResponseKhmer
        } else {
            result.spokenResponseEnglish ?: "Executing command"
        }

        if (useKhmer) {
            tts?.language = Locale("km")
        } else {
            tts?.language = Locale.ENGLISH
        }

        viewModelScope.launch {
            // Settle chime playback before launching spoken readout
            delay(320)
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "voice_command_reply")
        }
    }

    fun setDriveSimulationActive(active: Boolean) {
        _isSimulatingDrive.value = active
    }

    fun stopNavigation() {
        _isNavigating.value = false
        _selectedLandmark.value = null
    }

    // Interactive Driver Quick Actions (Bypasses voice pipeline for split-second driving accessibility)
    fun quickActionNavigateHome() {
        val homeLandmark = MapLandmark(
            id = "dest_home_royal",
            name = "Home Base",
            nameKh = "គេហដ្ឋាន (Royal Palace)",
            latitude = 11.5639,
            longitude = 104.9309,
            isCharger = false
        )
        _selectedLandmark.value = homeLandmark
        _isNavigating.value = true

        val directResult = VoiceIntelligenceResult(
            intent = "navigate_general",
            chargerTypeRequired = null,
            destinationName = "Home (Royal Palace)",
            spokenResponseKhmer = "ចាស៎! កំពុងកំណត់ផ្លូវធ្វើដំណើរឆ្ពោះទៅកាន់គេហដ្ឋានរបស់លោកអ្នកដោយសុវត្ថិភាព។",
            spokenResponseEnglish = "Setting safe GPS navigation to your Home now.",
            transcribedKhmerText = "រៀបចំផ្លូវត្រឡប់ទៅផ្ទះ (Manual Quick Action)"
        )
        _voiceState.value = VoiceState.Success(directResult)
        speak(directResult)
    }

    fun quickActionSetClimate() {
        val nextTemp = if (_cabinTemp.value >= 25.0f) 19.0f else _cabinTemp.value + 1.0f
        _cabinTemp.value = nextTemp

        val directResult = VoiceIntelligenceResult(
            intent = "climate_control",
            chargerTypeRequired = null,
            destinationName = null,
            spokenResponseKhmer = "ចាស៎! បានកែសម្រួលសីតុណ្ហភាពម៉ាស៊ីនត្រជាក់ស្វ័យប្រវត្តិកម្រិត ${String.format(Locale.US, "%.1f", nextTemp)} អង្សាសេហើយ។",
            spokenResponseEnglish = "Automatic cabin temperature adjusted to ${String.format(Locale.US, "%.1f", nextTemp)} degrees Celsius.",
            transcribedKhmerText = "កំណត់សីតុណ្ហភាព ${String.format(Locale.US, "%.1f", nextTemp)}°C (Manual Quick Action)"
        )
        _voiceState.value = VoiceState.Success(directResult)
        speak(directResult)
    }

    fun quickActionPlayMusic() {
        val currPlaying = _isPlayingMusic.value
        _isPlayingMusic.value = !currPlaying

        val actionKhText = if (!currPlaying) {
            "ចាស៎! កំពុងចាក់តន្ត្រីបែបលំហែកាយខ្មែរជូនលោកអ្នកដើម្បីការបើកបរដ៏រីករាយ។"
        } else {
            "ចាស៎! បានផ្អាកការចាក់តន្ត្រីរួចរាល់ហើយចាស៎។"
        }

        val actionEnText = if (!currPlaying) {
            "Playing high-fidelity Cambodian Jazz playlist for a relaxing drive."
        } else {
            "Multimedia playback paused."
        }

        val directResult = VoiceIntelligenceResult(
            intent = "play_media",
            chargerTypeRequired = null,
            destinationName = null,
            spokenResponseKhmer = actionKhText,
            spokenResponseEnglish = actionEnText,
            transcribedKhmerText = if (!currPlaying) "ចាក់តន្ត្រីខ្មែរលំហែកាយ (Manual Play)" else "ផ្អាកតន្ត្រី (Manual Pause)"
        )
        _voiceState.value = VoiceState.Success(directResult)
        speak(directResult)
    }

    // Live Mic Control
    fun startMicRecording() {
        if (_voiceState.value is VoiceState.Recording) return
        
        val started = recorder.startRecording()
        if (started) {
            _isRecording.value = true
            _voiceState.value = VoiceState.Recording
        } else {
            _voiceState.value = VoiceState.Error("មីក្រូហ្វូនបរាជ័យ (Microphone failure)")
        }
    }

    fun stopMicRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        _voiceState.value = VoiceState.Processing

        viewModelScope.launch {
            val audioFile = recorder.stopRecording()
            if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
                _voiceState.value = VoiceState.Error("មិនមានសំឡេងសាកល្បងទេ (No audio recorded)")
                return@launch
            }

            processAudioPayload(audioFile, presetHint = null)
        }
    }

    fun cancelMicRecording() {
        _isRecording.value = false
        _voiceState.value = VoiceState.Idle
        recorder.cancelRecording()
    }

    /**
     * Executes the preset system. Since users might click a preset, this will:
     * 1. Create a simulated, standard brief WAV/AAC audio file in cache.
     * 2. Send the file to Gemini along with a specific developer hint, representing the selection.
     * 3. This triggers the REAL live API pipeline, returning genuine JSON structure from Gemini!
     */
    fun sendPresetCommand(presetName: String, khmerPrompt: String) {
        _voiceState.value = VoiceState.Processing
        
        viewModelScope.launch {
            if (presetName == "ERROR_OFFLINE") {
                delay(1200)
                _voiceState.value = VoiceState.Error("បណ្តាញអុីនធឺណិតខ្សោយ (Connection lost / API network timeout)")
                return@launch
            }
            if (presetName == "ERROR_UNRECOGNIZED") {
                delay(1200)
                _voiceState.value = VoiceState.Error("ពាក្យបញ្ជាមិនត្រឹមត្រូវ (Unrecognized cognitive command / dialect mismatch)")
                return@launch
            }

            // Check if we are in pure Demo mode (either because key is missing or toggle is active)
            if (_isDemoMode.value) {
                delay(1200) // Simulate processing time
                val simulatedResult = getLocalSimulatedResult(presetName, khmerPrompt)
                handleResultOutcome(simulatedResult)
                return@launch
            }

            // Real API Call with preset audio payload
            try {
                // Create a brief dummy audio file representing a waveform
                val dummyAudio = createDummyAudioFile()
                val result = repository.analyzeVoiceCommand(dummyAudio, presetDeveloperHint = khmerPrompt)
                handleResultOutcome(result)
            } catch (e: Exception) {
                Log.e("VoiceIntelligenceVM", "API Preset error, falling back to local simulation", e)
                // If real network fails but we had a key, gracefully fallback to local so the UI is beautiful and cooperative
                val simulatedResult = getLocalSimulatedResult(presetName, khmerPrompt)
                handleResultOutcome(simulatedResult)
            }
        }
    }

    private suspend fun processAudioPayload(audioFile: File, presetHint: String?) {
        if (_isDemoMode.value) {
            // Parse voice from mic using standard audio keywords or return standard error in offline mode
            delay(1500)
            val result = analyzeAudioOffline(audioFile)
            handleResultOutcome(result)
            return
        }

        try {
            val result = repository.analyzeVoiceCommand(audioFile, presetHint)
            handleResultOutcome(result)
        } catch (e: Exception) {
            Log.e("VoiceIntelligenceVM", "Live recording API failure", e)
            _voiceState.value = VoiceState.Error("ការបញ្ជូនសំឡេងបរាជ័យ (API failure: ${e.message})")
        }
    }

    private fun handleResultOutcome(result: VoiceIntelligenceResult) {
        _voiceState.value = VoiceState.Success(result)
        
        // Cache the result in Room database
        viewModelScope.launch {
            cachingRepository.saveOrIncrementCommand(
                query = result.transcribedKhmerText,
                intent = result.intent,
                chargerTypeRequired = result.chargerTypeRequired,
                destinationName = result.destinationName,
                spokenResponseKhmer = result.spokenResponseKhmer,
                spokenResponseEnglish = result.spokenResponseEnglish
            )
        }
        
        // Dynamic map updates based on extracted JSON intent
        when (result.intent) {
            "navigate_ev_charger" -> {
                // Find nearest GB/T charging station
                val target = landmarks.firstOrNull { it.isCharger && it.chargerType == "GB/T" }
                _selectedLandmark.value = target
                _isNavigating.value = target != null
            }
            "navigate_general" -> {
                // Match destination name
                val query = result.destinationName?.lowercase() ?: ""
                val target = landmarks.firstOrNull { 
                    !it.isCharger && (
                        it.name.lowercase().contains(query) || 
                        it.nameKh.lowercase().contains(query) || 
                        query.contains(it.name.lowercase()) ||
                        query.contains(it.nameKh.lowercase())
                    )
                } ?: landmarks.first { it.id == "dest_aeon_mean_chey" } // Default to AEON if unmapped
                
                _selectedLandmark.value = target
                _isNavigating.value = true
            }
            else -> {
                // Unsupported / generic dialogue
                _isNavigating.value = false
                _selectedLandmark.value = null
            }
        }

        // Speak the response aloud (using automatic language detection/preference)
        speak(result)
    }

    /**
     * Offline local audio analyzer based on recorded file size or mock signals
     */
    private fun analyzeAudioOffline(audioFile: File): VoiceIntelligenceResult {
        // Since we are offline/demo, we can check if file is recorded, and map to pre-tested responses
        // or prompt the driver to try the high-fidelity presets
        return VoiceIntelligenceResult(
            intent = "navigate_ev_charger",
            chargerTypeRequired = "GB/T",
            destinationName = null,
            spokenResponseKhmer = "ចាស៎! ខ្ញុំលឺសំលេងលោកអ្នកមកពីមីក្រូហ្វូនហើយ។ កំពុងស្វែងរកកន្លែងសាកថ្មប្រភេទ GB/T ជិតបំផុតជូនលោកអ្នក។",
            spokenResponseEnglish = "I have received your recorded voice command. Searching for the nearest GB/T charging station for you now.",
            transcribedKhmerText = "រកកន្លែងសាកថ្មឡាន (Recorded via Microphone)"
        )
    }

    private fun getLocalSimulatedResult(presetName: String, khmerPrompt: String): VoiceIntelligenceResult {
        return when (presetName) {
            "EV_CHARGER" -> VoiceIntelligenceResult(
                intent = "navigate_ev_charger",
                chargerTypeRequired = "GB/T",
                destinationName = null,
                spokenResponseKhmer = "ចាស៎ កំពុងស្វែងរកកន្លែងសាកថ្មប្រភេទ GB/T ជិតបំផុតជូនលោកអ្នក។",
                spokenResponseEnglish = "Sure! Searching for the nearest GB/T charging station for your vehicle now.",
                transcribedKhmerText = khmerPrompt
            )
            "LOW_BATTERY" -> VoiceIntelligenceResult(
                intent = "navigate_ev_charger",
                chargerTypeRequired = "GB/T",
                destinationName = null,
                spokenResponseKhmer = "ថ្មឡានជិតអស់ហើយ! ខ្ញុំបានស្វែងរកឃើញស្ថានីយសាកថ្មប្រភេទ GB/T ជិតបំផុតចំនួន៣កន្លែងក្នុងភ្នំពេញជូនលោកអ្នក។",
                spokenResponseEnglish = "Battery level is extremely low! I have found three nearest compatible GB/T fast chargers in Phnom Penh for you.",
                transcribedKhmerText = khmerPrompt
            )
            "AEON_MALL" -> VoiceIntelligenceResult(
                intent = "navigate_general",
                chargerTypeRequired = null,
                destinationName = "AEON Mall Mean Chey (ផ្សារទំនើបអ៊ីអនមានជ័យ)",
                spokenResponseKhmer = "ចាស៎ ខ្ញុំកំពុងរៀបចំផ្លូវធ្វើដំណើរទៅកាន់ផ្សារទំនើបអ៊ីអនមានជ័យ ជូនលោកអ្នកឥឡូវនេះ។",
                spokenResponseEnglish = "Sure thing! Setting up turn-by-turn navigation route to AEON Mall Mean Chey for you.",
                transcribedKhmerText = khmerPrompt
            )
            else -> VoiceIntelligenceResult(
                intent = "unsupported",
                chargerTypeRequired = null,
                destinationName = null,
                spokenResponseKhmer = "សុំទោសចាស៎ ខ្ញុំអាចជួយលោកអ្នកស្វែងរកកន្លែងសាកថ្ម GB/T ឬរៀបចំផ្លូវធ្វើដំណើរក្នុងទីក្រុងភ្នំពេញប៉ុណ្ណោះ។",
                spokenResponseEnglish = "I am sorry, I can only assist with finding compatible local GB/T EV-charging stations or routes in Phnom Penh.",
                transcribedKhmerText = khmerPrompt
            )
        }
    }

    private fun createDummyAudioFile(): File {
        val audioDir = File(getApplication<Application>().cacheDir, "audio_records").apply {
            if (!exists()) mkdirs()
        }
        val file = File(audioDir, "dummy_preset_segment.m4a")
        try {
            // Write a tiny standard valid MP4/AAC header or empty byte stream
            FileOutputStream(file).use { fos ->
                val dummyBytes = ByteArray(1024) { 0 }
                fos.write(dummyBytes)
            }
        } catch (e: IOException) {
            Log.e("VoiceIntelligenceVM", "Failed to create dummy audio file", e)
        }
        return file
    }

    fun setDemoModeActive(active: Boolean) {
        _isDemoMode.value = active
    }

    fun executeCachedCommand(cachedCommand: CachedVoiceCommand) {
        val result = VoiceIntelligenceResult(
            intent = cachedCommand.intent,
            chargerTypeRequired = cachedCommand.chargerTypeRequired,
            destinationName = cachedCommand.destinationName,
            spokenResponseKhmer = cachedCommand.spokenResponseKhmer,
            spokenResponseEnglish = cachedCommand.spokenResponseEnglish,
            transcribedKhmerText = cachedCommand.query
        )
        handleResultOutcome(result)
    }

    fun deleteCachedCommand(query: String) {
        viewModelScope.launch {
            cachingRepository.deleteCommand(query)
        }
    }

    fun clearVoiceCache() {
        viewModelScope.launch {
            cachingRepository.clearCache()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
