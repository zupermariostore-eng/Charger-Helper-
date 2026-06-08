package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_voice_commands")
data class CachedVoiceCommand(
    @PrimaryKey val query: String, // Transcribed Khmer/English text of the user command
    val intent: String, // "navigate_ev_charger" | "navigate_general" | "unsupported" | "climate_control" | "play_media"
    val chargerTypeRequired: String?, // "GB/T" etc.
    val destinationName: String?,
    val spokenResponseKhmer: String,
    val spokenResponseEnglish: String?,
    val frequency: Int = 1,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)
