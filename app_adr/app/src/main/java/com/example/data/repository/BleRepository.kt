package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.CustomButton
import com.example.data.local.DeviceSwitch
import com.example.data.local.LogDao
import com.example.data.local.LogEntry
import com.example.data.local.SessionSummary
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BleRepository(private val database: AppDatabase) {
  private val logDao = database.logDao()
  private val buttonDao = database.customButtonDao()
  private val switchDao = database.deviceSwitchDao()

  var currentSessionId: String = UUID.randomUUID().toString().substring(0, 8)
    private set

  fun startNewSession(deviceTag: String = "ESP32"): String {
    currentSessionId = "${deviceTag}_${System.currentTimeMillis() % 100000}"
    return currentSessionId
  }

  val allLogs: Flow<List<LogEntry>> = logDao.getAllLogs()
  val recentLogs: Flow<List<LogEntry>> = logDao.getRecentLogs(500)
  val sessionSummaries: Flow<List<SessionSummary>> = logDao.getSessionSummaries()
  val customButtons: Flow<List<CustomButton>> = buttonDao.getAllButtons()
  val deviceSwitches: Flow<List<DeviceSwitch>> = switchDao.getAllSwitches()

  fun getLogsBySession(sessionId: String): Flow<List<LogEntry>> =
    logDao.getLogsBySession(sessionId)

  fun searchLogs(query: String): Flow<List<LogEntry>> =
    logDao.searchLogs(query)

  suspend fun insertLog(
    direction: String,
    message: String,
    hexData: String = "",
    deviceName: String = "ESP32",
    deviceAddress: String = "",
    tag: String = "DATA"
  ): Long {
    val entry = LogEntry(
      sessionId = currentSessionId,
      direction = direction,
      message = message,
      hexData = hexData,
      deviceName = deviceName,
      deviceAddress = deviceAddress,
      tag = tag
    )
    return logDao.insertLog(entry)
  }

  suspend fun clearLogs() {
    logDao.clearAllLogs()
  }

  suspend fun deleteSession(sessionId: String) {
    logDao.deleteLogsBySession(sessionId)
  }

  suspend fun insertButton(button: CustomButton): Long =
    buttonDao.insertButton(button)

  suspend fun updateButton(button: CustomButton) =
    buttonDao.updateButton(button)

  suspend fun deleteButton(button: CustomButton) =
    buttonDao.deleteButton(button)

  suspend fun insertSwitch(sw: DeviceSwitch): Long =
    switchDao.insertSwitch(sw)

  suspend fun updateSwitch(sw: DeviceSwitch) =
    switchDao.updateSwitch(sw)

  suspend fun deleteSwitch(sw: DeviceSwitch) =
    switchDao.deleteSwitch(sw)

  suspend fun ensureDefaults() {
    try {
      if (buttonDao.getButtonCount() == 0) {
        buttonDao.insertAll(AppDatabase.defaultButtons)
      }
    } catch (_: Exception) {}
    try {
      if (switchDao.getCount() == 0) {
        switchDao.insertAll(AppDatabase.defaultSwitches)
      }
    } catch (_: Exception) {}
  }
}
