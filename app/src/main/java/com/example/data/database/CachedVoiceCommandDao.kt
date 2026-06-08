package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedVoiceCommandDao {
    @Query("SELECT * FROM cached_voice_commands ORDER BY frequency DESC, lastUsedTimestamp DESC")
    fun getFrequentlyUsedCommands(): Flow<List<CachedVoiceCommand>>

    @Query("SELECT * FROM cached_voice_commands WHERE `query` = :query LIMIT 1")
    suspend fun getCommandByQuery(query: String): CachedVoiceCommand?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(command: CachedVoiceCommand)

    @Query("DELETE FROM cached_voice_commands WHERE `query` = :query")
    suspend fun deleteCommand(query: String)

    @Query("DELETE FROM cached_voice_commands")
    suspend fun clearCache()
}
