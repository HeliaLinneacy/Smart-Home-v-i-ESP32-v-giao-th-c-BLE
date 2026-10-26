package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ble.BleManager
import com.example.ble.BlePacket
import com.example.ble.ConnectionStatus
import com.example.ble.DiscoveredDevice
import com.example.data.local.AppDatabase
import com.example.data.local.CustomButton
import com.example.data.local.DeviceSwitch
import com.example.data.local.LogEntry
import com.example.data.local.SessionSummary
import com.example.data.model.MetricPoint
import com.example.data.model.ParsedTelemetry
import com.example.data.model.TelemetryParser
import com.example.data.repository.BleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TerminalDisplayItem(
  val id: Long,
  val timestamp: Long,
  val formattedTime: String,
  val direction: String, // "RX", "TX", "SYS"
  val text: String,
  val hex: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
  val database = AppDatabase.getDatabase(application)
  val repository = BleRepository(database)
  val bleManager = BleManager(application)

  val connectionStatus: StateFlow<ConnectionStatus> = bleManager.connectionStatus
  val connectedDeviceName: StateFlow<String?> = bleManager.connectedDeviceName
  val connectedDeviceAddress: StateFlow<String?> = bleManager.connectedDeviceAddress
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleManager.discoveredDevices
  val mtuSize: StateFlow<Int> = bleManager.mtuSize
  val isSimulatorMode: StateFlow<Boolean> = bleManager.isSimulatorMode
  val discoveredGattServices = bleManager.discoveredGattServices
  val activeServiceUuid = bleManager.activeServiceUuid
  val activeRxUuid = bleManager.activeRxUuid
  val activeTxUuid = bleManager.activeTxUuid
  val connectedDeviceType = bleManager.connectedDeviceType

  // Terminal UI States
  private val _terminalItems = MutableStateFlow<List<TerminalDisplayItem>>(emptyList())
  val terminalItems: StateFlow<List<TerminalDisplayItem>> = _terminalItems.asStateFlow()

  val autoScroll = MutableStateFlow(true)
  val displayHex = MutableStateFlow(false)
  val showTimestamps = MutableStateFlow(true)
  val filterDirection = MutableStateFlow("ALL") // "ALL", "RX", "TX"
  val filterQuery = MutableStateFlow("")
  val newlineSetting = MutableStateFlow("\r\n") // "\r\n", "\n", "\r", ""
  val isHexInput = MutableStateFlow(false)
  val inputText = MutableStateFlow("")
  val autoSaveToDb = MutableStateFlow(true)
  val onlyOnUserCommand = MutableStateFlow(true) // Only display and log to DB when user sends command
  private var lastUserCommandTimestamp = 0L
  private val commandResponseWindowMs = 6000L

  val commandHistory = MutableStateFlow(
    listOf("STATUS", "LED:ON", "LED:OFF", "RELAY:1:TOGGLE", "RELAY:2:TOGGLE", "GET:DATA", "RESET")
  )

  // Remote Control States
  val customButtons: StateFlow<List<CustomButton>> = repository.customButtons
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val deviceSwitches: StateFlow<List<DeviceSwitch>> = repository.deviceSwitches
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val relay1State = MutableStateFlow(false)
  val relay2State = MutableStateFlow(false)
  val ledState = MutableStateFlow(false)
  val buzzerState = MutableStateFlow(false)
  val pwmValue = MutableStateFlow(128f)
  val servoValue = MutableStateFlow(90f)

  // Periodic sender
  val isRepeating = MutableStateFlow(false)
  val repeatCommand = MutableStateFlow("STATUS")
  val repeatIntervalMs = MutableStateFlow(2000L)
  private var repeatJob: Job? = null

  // Telemetry & Sensor States
  private val _latestTelemetry = MutableStateFlow<ParsedTelemetry?>(null)
  val latestTelemetry: StateFlow<ParsedTelemetry?> = _latestTelemetry.asStateFlow()

  private val _temperatureHistory = MutableStateFlow<List<MetricPoint>>(emptyList())
  val temperatureHistory: StateFlow<List<MetricPoint>> = _temperatureHistory.asStateFlow()

  private val _humidityHistory = MutableStateFlow<List<MetricPoint>>(emptyList())
  val humidityHistory: StateFlow<List<MetricPoint>> = _humidityHistory.asStateFlow()

  private val _voltageHistory = MutableStateFlow<List<MetricPoint>>(emptyList())
  val voltageHistory: StateFlow<List<MetricPoint>> = _voltageHistory.asStateFlow()

  private val _telemetryList = MutableStateFlow<List<ParsedTelemetry>>(emptyList())
  val telemetryList: StateFlow<List<ParsedTelemetry>> = _telemetryList.asStateFlow()

  // Database Logs State
  val dbRecentLogs: StateFlow<List<LogEntry>> = repository.recentLogs
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val sessionSummaries: StateFlow<List<SessionSummary>> = repository.sessionSummaries
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val currentSessionId: String
    get() = repository.currentSessionId

  // Filtered terminal items
  val filteredTerminalItems: StateFlow<List<TerminalDisplayItem>> = combine(
    _terminalItems,
    filterDirection,
    filterQuery
  ) { items, direction, query ->
    items.filter { item ->
      val matchesDir = when (direction) {
        "RX" -> item.direction == "RX"
        "TX" -> item.direction == "TX"
        else -> true
      }
      val matchesQuery = query.isEmpty() ||
        item.text.contains(query, ignoreCase = true) ||
        item.hex.contains(query, ignoreCase = true)
      matchesDir && matchesQuery
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
  private var itemCounter = 0L

  init {
    viewModelScope.launch {
      repository.ensureDefaults()
    }

    // Collect incoming packets from BLE manager
    viewModelScope.launch {
      bleManager.incomingPackets.collect { packet ->
        handleBlePacket(packet)
      }
    }
  }

  private fun handleBlePacket(packet: BlePacket) {
    // If onlyOnUserCommand is true:
    // Only display and save when user issues a command (TX) or receives a response (RX within window)
    if (onlyOnUserCommand.value) {
      when (packet.direction) {
        "TX" -> { /* User entered command: always accepted */ }
        "RX" -> {
          val timeSinceCmd = System.currentTimeMillis() - lastUserCommandTimestamp
          if (timeSinceCmd !in 0..commandResponseWindowMs) {
            // Unsolicited background packet: ignore
            return
          }
        }
        "SYS" -> { /* Connection state message: always accepted */ }
        else -> return
      }
    }

    itemCounter++
    val timeStr = timeFormatter.format(Date(packet.timestamp))
    val item = TerminalDisplayItem(
      id = itemCounter,
      timestamp = packet.timestamp,
      formattedTime = timeStr,
      direction = packet.direction,
      text = packet.text,
      hex = packet.hex
    )

    // Add to in-memory terminal buffer (keep up to 1000 items)
    val current = _terminalItems.value.toMutableList()
    if (current.size >= 1000) {
      current.removeAt(0)
    }
    current.add(item)
    _terminalItems.value = current

    // Auto-save to SQLite Room database (only user command TX and its response RX)
    if (autoSaveToDb.value && packet.direction != "SYS") {
      viewModelScope.launch {
        repository.insertLog(
          direction = packet.direction,
          message = packet.text,
          hexData = packet.hex,
          deviceName = connectedDeviceName.value ?: "ESP32",
          deviceAddress = connectedDeviceAddress.value ?: "",
          tag = if (packet.direction == "RX") "DATA" else "CMD"
        )
      }
    }

    // If RX, parse telemetry
    if (packet.direction == "RX") {
      parseAndRecordTelemetry(packet.text, packet.timestamp)
    }
  }

  private fun parseAndRecordTelemetry(raw: String, timestamp: Long) {
    val telemetry = TelemetryParser.parse(raw) ?: return
    _latestTelemetry.value = telemetry

    // Update telemetry state
    telemetry.relay1State?.let { relay1State.value = it }
    telemetry.relay2State?.let { relay2State.value = it }
    telemetry.ledState?.let { ledState.value = it }

    telemetry.temperature?.let { temp ->
      val list = _temperatureHistory.value.toMutableList()
      list.add(MetricPoint(timestamp, temp))
      if (list.size > 50) list.removeAt(0)
      _temperatureHistory.value = list
    }

    telemetry.humidity?.let { hum ->
      val list = _humidityHistory.value.toMutableList()
      list.add(MetricPoint(timestamp, hum))
      if (list.size > 50) list.removeAt(0)
      _humidityHistory.value = list
    }

    telemetry.voltage?.let { volt ->
      val list = _voltageHistory.value.toMutableList()
      list.add(MetricPoint(timestamp, volt))
      if (list.size > 50) list.removeAt(0)
      _voltageHistory.value = list
    }

    val tList = _telemetryList.value.toMutableList()
    tList.add(0, telemetry)
    if (tList.size > 100) tList.removeLast()
    _telemetryList.value = tList
  }

  fun sendCurrentInput() {
    val text = inputText.value
    if (text.isEmpty()) return

    val cmdToSend = if (isHexInput.value) {
      text
    } else {
      text + newlineSetting.value
    }

    sendCommand(cmdToSend, isHexInput.value)

    // Save to history
    val trimmed = text.trim()
    val hist = commandHistory.value.toMutableList()
    if (!hist.contains(trimmed)) {
      hist.add(0, trimmed)
      if (hist.size > 20) hist.removeLast()
      commandHistory.value = hist
    }

    inputText.value = ""
  }

  fun sendCommand(command: String, isHex: Boolean = false) {
    lastUserCommandTimestamp = System.currentTimeMillis()
    bleManager.send(command, isHex)
  }

  fun sendMacro(button: CustomButton) {
    sendCommand(button.command, button.isHex)
  }

  fun setDeviceSwitchState(deviceSwitch: DeviceSwitch, isChecked: Boolean) {
    if (deviceSwitch.isOn == isChecked) return
    val commandToSend = if (isChecked) deviceSwitch.onCommand else deviceSwitch.offCommand
    sendCommand(commandToSend, false)
    viewModelScope.launch {
      try {
        repository.updateSwitch(deviceSwitch.copy(isOn = isChecked))
      } catch (_: Exception) {}
    }
  }

  fun toggleDeviceSwitch(deviceSwitch: DeviceSwitch) {
    setDeviceSwitchState(deviceSwitch, !deviceSwitch.isOn)
  }

  fun addDeviceSwitch(
    name: String,
    onCommand: String,
    offCommand: String,
    iconName: String = "toggle",
    colorHex: String = "#00E676"
  ) {
    viewModelScope.launch {
      val onCmd = if (onCommand.endsWith("\n")) onCommand else "$onCommand\n"
      val offCmd = if (offCommand.endsWith("\n")) offCommand else "$offCommand\n"
      repository.insertSwitch(
        DeviceSwitch(
          name = name,
          onCommand = onCmd,
          offCommand = offCmd,
          isOn = false,
          iconName = iconName,
          colorHex = colorHex
        )
      )
    }
  }

  fun deleteDeviceSwitch(deviceSwitch: DeviceSwitch) {
    viewModelScope.launch {
      repository.deleteSwitch(deviceSwitch)
    }
  }

  fun toggleRelay1() {
    val next = !relay1State.value
    relay1State.value = next
    sendCommand("RELAY:1:${if (next) "ON" else "OFF"}\n")
  }

  fun toggleRelay2() {
    val next = !relay2State.value
    relay2State.value = next
    sendCommand("RELAY:2:${if (next) "ON" else "OFF"}\n")
  }

  fun toggleLed() {
    val next = !ledState.value
    ledState.value = next
    sendCommand("LED:${if (next) "ON" else "OFF"}\n")
  }

  fun triggerBuzzer() {
    buzzerState.value = true
    sendCommand("BUZZER:BEEP\n")
    viewModelScope.launch {
      delay(300)
      buzzerState.value = false
    }
  }

  fun updatePwm(value: Float) {
    pwmValue.value = value
  }

  fun commitPwm() {
    val intVal = pwmValue.value.toInt().coerceIn(0, 255)
    sendCommand("PWM:$intVal\n")
  }

  fun updateServo(value: Float) {
    servoValue.value = value
  }

  fun commitServo() {
    val intVal = servoValue.value.toInt().coerceIn(0, 180)
    sendCommand("SERVO:$intVal\n")
  }

  fun sendRobotCommand(action: String) {
    sendCommand("CMD:$action\n")
  }

  fun togglePeriodicRepeat() {
    if (isRepeating.value) {
      stopPeriodicRepeat()
    } else {
      startPeriodicRepeat()
    }
  }

  private fun startPeriodicRepeat() {
    isRepeating.value = true
    repeatJob?.cancel()
    repeatJob = viewModelScope.launch {
      while (isActive && isRepeating.value) {
        val cmd = repeatCommand.value.ifEmpty { "STATUS" } + newlineSetting.value
        sendCommand(cmd, false)
        delay(repeatIntervalMs.value.coerceAtLeast(200L))
      }
    }
  }

  private fun stopPeriodicRepeat() {
    isRepeating.value = false
    repeatJob?.cancel()
    repeatJob = null
  }

  fun startScan() {
    bleManager.startScan()
  }

  fun stopScan() {
    bleManager.stopScan()
  }

  fun connect(device: DiscoveredDevice) {
    repository.startNewSession(device.name.ifBlank { "ESP32" })
    bleManager.connect(device)
  }

  fun disconnect() {
    stopPeriodicRepeat()
    bleManager.disconnect()
  }

  fun toggleSimulatorMode(enabled: Boolean) {
    bleManager.setSimulatorMode(enabled)
  }

  fun clearTerminal() {
    _terminalItems.value = emptyList()
  }

  fun clearDatabase() {
    viewModelScope.launch {
      repository.clearLogs()
    }
  }

  fun deleteSession(sessionId: String) {
    viewModelScope.launch {
      repository.deleteSession(sessionId)
    }
  }

  fun addCustomButton(label: String, command: String, colorHex: String, isHex: Boolean = false) {
    viewModelScope.launch {
      repository.insertButton(
        CustomButton(
          label = label,
          command = command,
          colorHex = colorHex,
          isHex = isHex,
          orderIndex = (customButtons.value.maxOfOrNull { it.orderIndex } ?: 0) + 1
        )
      )
    }
  }

  fun deleteCustomButton(button: CustomButton) {
    viewModelScope.launch {
      repository.deleteButton(button)
    }
  }

  fun exportTerminalLogsAsText(): String {
    val sb = StringBuilder()
    sb.appendLine("=== ESP32 BLE Terminal Logs Export ===")
    sb.appendLine("Session: $currentSessionId")
    sb.appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
    sb.appendLine("Device: ${connectedDeviceName.value ?: "Unknown"} [${connectedDeviceAddress.value ?: "N/A"}]")
    sb.appendLine("----------------------------------------")
    for (item in _terminalItems.value) {
      sb.appendLine("[${item.formattedTime}] [${item.direction}] ${item.text.trimEnd()}")
    }
    return sb.toString()
  }

  fun exportDatabaseLogsCsv(logs: List<LogEntry>): String {
    val sb = StringBuilder()
    sb.appendLine("id,sessionId,timestamp,dateTime,direction,message,hexData,deviceName,deviceAddress,tag")
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    for (log in logs) {
      val timeStr = df.format(Date(log.timestamp))
      val safeMsg = log.message.replace("\"", "\"\"").replace("\n", "\\n").replace("\r", "\\r")
      sb.appendLine("${log.id},\"${log.sessionId}\",${log.timestamp},\"$timeStr\",\"${log.direction}\",\"$safeMsg\",\"${log.hexData}\",\"${log.deviceName}\",\"${log.deviceAddress}\",\"${log.tag}\"")
    }
    return sb.toString()
  }

  fun setCustomUuids(service: String, rx: String, tx: String) {
    bleManager.setCustomUuids(service, rx, tx)
  }

  fun selectCharacteristicAsWrite(charUuid: String) {
    bleManager.setWriteCharacteristic(charUuid)
  }

  fun selectCharacteristicAsRead(charUuid: String) {
    bleManager.setReadCharacteristic(charUuid)
  }

  override fun onCleared() {
    super.onCleared()
    stopPeriodicRepeat()
    bleManager.disconnect()
  }
}
