package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "terminal_logs")
data class LogEntry(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val sessionId: String,
  val timestamp: Long = System.currentTimeMillis(),
  val direction: String, // "RX", "TX", "SYS"
  val message: String,
  val hexData: String = "",
  val deviceName: String = "",
  val deviceAddress: String = "",
  val tag: String = "DATA" // "DATA", "TELEMETRY", "CMD", "STATUS", "ALARM"
)
