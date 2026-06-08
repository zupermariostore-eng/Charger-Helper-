package com.example.data.database

import kotlinx.coroutines.flow.Flow

class VoiceCommandRepository(private val dao: CachedVoiceCommandDao) {

    val frequentlyUsedCommands: Flow<List<CachedVoiceCommand>> = dao.getFrequentlyUsedCommands()

    suspend fun getCommandByQuery(query: String): CachedVoiceCommand? {
        return dao.getCommandByQuery(query)
    }

    suspend fun saveOrIncrementCommand(
        query: String,
        intent: String,
        chargerTypeRequired: String?,
        destinationName: String?,
        spokenResponseKhmer: String,
        spokenResponseEnglish: String?
    ) {
        val existing = dao.getCommandByQuery(query)
        if (existing != null) {
            val updated = existing.copy(
                frequency = existing.frequency + 1,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            dao.insertRaw(updated)
        } else {
            val newCommand = CachedVoiceCommand(
                query = query,
                intent = intent,
                chargerTypeRequired = chargerTypeRequired,
                destinationName = destinationName,
                spokenResponseKhmer = spokenResponseKhmer,
                spokenResponseEnglish = spokenResponseEnglish,
                frequency = 1,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            dao.insertRaw(newCommand)
        }
    }

    suspend fun deleteCommand(query: String) {
        dao.deleteCommand(query)
    }

    suspend fun clearCache() {
        dao.clearCache()
    }
}
