package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SessionSummary(
  val sessionId: String,
  val entryCount: Int,
  val minTime: Long,
  val maxTime: Long,
  val deviceName: String
)

@Dao
interface LogDao {
  @Query("SELECT * FROM terminal_logs ORDER BY timestamp DESC")
  fun getAllLogs(): Flow<List<LogEntry>>

  @Query("SELECT * FROM terminal_logs ORDER BY timestamp DESC LIMIT :limit")
  fun getRecentLogs(limit: Int = 500): Flow<List<LogEntry>>

  @Query("SELECT * FROM terminal_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
  fun getLogsBySession(sessionId: String): Flow<List<LogEntry>>

  @Query("SELECT * FROM terminal_logs WHERE message LIKE '%' || :query || '%' OR hexData LIKE '%' || :query || '%' ORDER BY timestamp DESC")
  fun searchLogs(query: String): Flow<List<LogEntry>>

  @Query("""
    SELECT sessionId, COUNT(*) as entryCount, MIN(timestamp) as minTime, MAX(timestamp) as maxTime, deviceName 
    FROM terminal_logs 
    GROUP BY sessionId 
    ORDER BY maxTime DESC
  """)
  fun getSessionSummaries(): Flow<List<SessionSummary>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: LogEntry): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLogs(logs: List<LogEntry>)

  @Query("DELETE FROM terminal_logs WHERE id = :id")
  suspend fun deleteLogById(id: Long)

  @Query("DELETE FROM terminal_logs WHERE sessionId = :sessionId")
  suspend fun deleteLogsBySession(sessionId: String)

  @Query("DELETE FROM terminal_logs")
  suspend fun clearAllLogs()

  @Query("SELECT COUNT(*) FROM terminal_logs")
  suspend fun getTotalLogCount(): Int
}
