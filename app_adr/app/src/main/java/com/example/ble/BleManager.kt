package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

enum class ConnectionStatus {
  DISCONNECTED,
  SCANNING,
  CONNECTING,
  DISCOVERING_SERVICES,
  CONNECTED
}

enum class DeviceType {
  BLE,
  CLASSIC,
  DUAL,
  UNKNOWN
}

data class DiscoveredDevice(
  val name: String,
  val address: String,
  val rssi: Int,
  val device: BluetoothDevice? = null,
  val isBonded: Boolean = false,
  val deviceType: DeviceType = DeviceType.BLE,
  val isEsp32: Boolean = false,
  val lastSeen: Long = System.currentTimeMillis()
)

data class BlePacket(
  val timestamp: Long = System.currentTimeMillis(),
  val direction: String, // "RX", "TX", "SYS"
  val text: String,
  val hex: String
)

data class GattCharInfo(
  val uuid: String,
  val serviceUuid: String,
  val canRead: Boolean,
  val canWrite: Boolean,
  val canNotify: Boolean,
  val canIndicate: Boolean,
  val propertiesDesc: String,
  val name: String
)

data class GattServiceInfo(
  val uuid: String,
  val name: String,
  val characteristics: List<GattCharInfo>
)

class BleManager(private val context: Context) {
  // Standard Nordic UART Service (NUS) UUIDs
  var serviceUuidString: String = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
  var rxCharUuidString: String = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E" // Write to ESP32
  var txCharUuidString: String = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // Notifications from ESP32

  private val _activeServiceUuid = MutableStateFlow(serviceUuidString)
  val activeServiceUuid: StateFlow<String> = _activeServiceUuid.asStateFlow()

  private val _activeRxUuid = MutableStateFlow(rxCharUuidString)
  val activeRxUuid: StateFlow<String> = _activeRxUuid.asStateFlow()

  private val _activeTxUuid = MutableStateFlow(txCharUuidString)
  val activeTxUuid: StateFlow<String> = _activeTxUuid.asStateFlow()

  // Standard Serial Port Profile (SPP) UUID for Classic Bluetooth
  private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
  private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

  private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private var bluetoothGatt: BluetoothGatt? = null
  private var writeCharacteristic: BluetoothGattCharacteristic? = null
  private var notifyCharacteristic: BluetoothGattCharacteristic? = null

  // Classic Bluetooth socket & streams
  private var classicSocket: BluetoothSocket? = null
  private var classicOutputStream: OutputStream? = null
  private var classicInputStream: InputStream? = null
  private var classicReaderJob: Job? = null

  private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
  val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

  private val _connectedDeviceName = MutableStateFlow<String?>(null)
  val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

  private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
  val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

  private val _connectedDeviceType = MutableStateFlow<DeviceType>(DeviceType.BLE)
  val connectedDeviceType: StateFlow<DeviceType> = _connectedDeviceType.asStateFlow()

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private val _discoveredGattServices = MutableStateFlow<List<GattServiceInfo>>(emptyList())
  val discoveredGattServices: StateFlow<List<GattServiceInfo>> = _discoveredGattServices.asStateFlow()

  private val _incomingPackets = MutableSharedFlow<BlePacket>(extraBufferCapacity = 64)
  val incomingPackets: SharedFlow<BlePacket> = _incomingPackets.asSharedFlow()

  private val _mtuSize = MutableStateFlow(23)
  val mtuSize: StateFlow<Int> = _mtuSize.asStateFlow()

  private val _isSimulatorMode = MutableStateFlow(false)
  val isSimulatorMode: StateFlow<Boolean> = _isSimulatorMode.asStateFlow()

  private val writeQueue: Queue<ByteArray> = LinkedList()
  private var isWriting = false
  private val scope = CoroutineScope(Dispatchers.IO)
  private var simulatorJob: Job? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  private var isClassicReceiverRegistered = false

  // Simulated ESP32 device
  val simulatedDevice = DiscoveredDevice(
    name = "ESP32-S3-WROOM-1 (Simulator)",
    address = "4C:EB:D6:88:1A:F0",
    rssi = -52,
    isBonded = false,
    deviceType = DeviceType.BLE,
    isEsp32 = true
  )

  // Espressif MAC OUI prefixes to identify ESP32 devices even if unnamed
  private val espressifMacPrefixes = listOf(
    "24:0A:C4", "24:6F:28", "30:AE:A4", "3C:61:05", "3C:71:BF",
    "4C:EB:D6", "84:0D:8E", "84:F7:03", "94:B5:55", "A4:CF:12",
    "A4:E5:7C", "AC:67:B2", "B4:E6:2D", "C4:4F:33", "D8:A0:1D",
    "E8:31:CD", "EC:62:60", "08:3A:F2", "10:97:BD", "18:FE:34",
    "24:DC:C3", "40:22:D8", "48:E7:29", "54:43:B2", "60:55:F9",
    "68:B6:B3", "70:04:1D", "7C:87:CE", "7C:DF:A1", "90:38:0C",
    "A0:20:A6", "A0:76:4E", "B8:D6:1A", "C8:2E:18", "CC:50:E3",
    "D4:D4:DA", "DC:54:75", "E0:98:06", "FC:F5:C4", "34:85:18"
  )

  fun isEsp32MacOrName(name: String?, address: String): Boolean {
    val upperName = name?.uppercase() ?: ""
    if (upperName.contains("ESP32") ||
      upperName.contains("ESP-") ||
      upperName.contains("ESP_") ||
      upperName.contains("WROOM") ||
      upperName.contains("S3") ||
      upperName.contains("C3") ||
      upperName.contains("C6") ||
      upperName.contains("NODE") ||
      upperName.contains("NODEMCU") ||
      upperName.contains("RELAY") ||
      upperName.contains("UART") ||
      upperName.contains("ESPRESSIF") ||
      upperName.contains("DOIT")
    ) {
      return true
    }
    val upperAddr = address.uppercase()
    return espressifMacPrefixes.any { upperAddr.startsWith(it) }
  }

  // Classic Bluetooth BroadcastReceiver
  private val classicBtReceiver = object : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        BluetoothDevice.ACTION_FOUND -> {
          val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
          } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          } ?: return

          val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
          val addr = dev.address ?: return
          val rawName = dev.name
          val isEsp = isEsp32MacOrName(rawName, addr)
          val isBonded = dev.bondState == BluetoothDevice.BOND_BONDED
          val isNamed = !rawName.isNullOrBlank()

          // Filter: Chỉ quét thiết bị bình thường (có tên hoặc đã ghép đôi) và ESP32
          if (!isEsp && !isNamed && !isBonded) {
            return
          }

          val name = if (isNamed) {
            rawName!!
          } else {
            "ESP32 (BT Classic ${addr.takeLast(5)})"
          }

          val dType = when (dev.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
            BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL
            BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
            else -> DeviceType.CLASSIC
          }

          addOrUpdateDevice(
            DiscoveredDevice(
              name = name,
              address = addr,
              rssi = if (rssi == Short.MIN_VALUE.toInt()) -65 else rssi,
              device = dev,
              isBonded = isBonded,
              deviceType = dType,
              isEsp32 = isEsp
            )
          )
        }
        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
          // Classic discovery finished
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun startScan() {
    // Ngắt đợt quét cũ để không bị chồng chéo và cộng dồn thiết bị
    stopScan()

    if (_isSimulatorMode.value) {
      _connectionStatus.value = ConnectionStatus.SCANNING
      _discoveredDevices.value = listOf(simulatedDevice)
      mainHandler.postDelayed({
        if (_connectionStatus.value == ConnectionStatus.SCANNING) {
          _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
      }, 3000)
      return
    }

    val adapter = bluetoothAdapter
    if (adapter == null || !adapter.isEnabled) {
      _discoveredDevices.value = emptyList()
      return
    }

    _connectionStatus.value = ConnectionStatus.SCANNING

    // 1. Chỉ lấy thiết bị đã ghép đôi (Bonded) hợp lệ
    val bondedList = mutableListOf<DiscoveredDevice>()
    try {
      adapter.bondedDevices?.forEach { dev ->
        val addr = dev.address ?: return@forEach
        val rawName = dev.name
        val isEsp = isEsp32MacOrName(rawName, addr)
        val name = if (!rawName.isNullOrBlank()) {
          rawName
        } else {
          if (isEsp) "ESP32 (Đã ghép đôi)" else "Thiết bị Bluetooth (${addr.takeLast(5)})"
        }
        val dType = when (dev.type) {
          BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
          BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL
          BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
          else -> DeviceType.CLASSIC
        }
        bondedList.add(
          DiscoveredDevice(
            name = name,
            address = addr,
            rssi = -60,
            device = dev,
            isBonded = true,
            deviceType = dType,
            isEsp32 = isEsp
          )
        )
      }
    } catch (_: Exception) {}

    // Làm mới danh sách thiết bị mỗi khi bắt đầu quét lại (chỉ giữ bonded devices ban đầu)
    _discoveredDevices.value = bondedList.distinctBy { it.address.uppercase() }

    // 2. Đăng ký nhận Bluetooth Classic và bắt đầu Discovery
    try {
      if (!isClassicReceiverRegistered) {
        val filter = IntentFilter().apply {
          addAction(BluetoothDevice.ACTION_FOUND)
          addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(classicBtReceiver, filter)
        isClassicReceiverRegistered = true
      }
      if (adapter.isDiscovering) {
        adapter.cancelDiscovery()
      }
      adapter.startDiscovery()
    } catch (_: Exception) {}

    // 3. Khởi chạy BLE Scanner tối ưu cho ESP32 và thiết bị BLE có tên
    try {
      val scanner = adapter.bluetoothLeScanner
      if (scanner != null) {
        val scanSettings = ScanSettings.Builder()
          .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
          .setReportDelay(0)
          .build()

        scanner.startScan(null, scanSettings, bleScanCallback)
      }
    } catch (_: Exception) {}

    // Tự động dừng quét sau 10 giây để tránh tốn pin và ổn định danh sách
    mainHandler.postDelayed({
      stopScan()
    }, 10000)
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    try {
      bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
    } catch (_: Exception) {}

    try {
      if (bluetoothAdapter?.isDiscovering == true) {
        bluetoothAdapter.cancelDiscovery()
      }
    } catch (_: Exception) {}

    if (isClassicReceiverRegistered) {
      try {
        context.unregisterReceiver(classicBtReceiver)
      } catch (_: Exception) {}
      isClassicReceiverRegistered = false
    }

    if (_connectionStatus.value == ConnectionStatus.SCANNING) {
      _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
  }

  private val bleScanCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      result?.let { res ->
        val dev = res.device
        val addr = dev.address ?: return
        val rawName = dev.name ?: res.scanRecord?.deviceName
        val serviceUuids = res.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList()
        val isEspUartService = serviceUuids.any { it.contains("6e400001") || it.contains("4fafc201") }
        val isEsp = isEsp32MacOrName(rawName, addr) || isEspUartService
        val isBonded = dev.bondState == BluetoothDevice.BOND_BONDED
        val isNamed = !rawName.isNullOrBlank()

        // CHỈ quét thiết bị thông thường (có tên hoặc đã ghép đôi) và ESP32
        // Bỏ qua hoàn toàn các gói beacon BLE ẩn danh, MAC xoay vòng ngẫu nhiên không tên
        if (!isEsp && !isNamed && !isBonded) {
          return
        }

        val name = if (isNamed) {
          rawName!!
        } else {
          "ESP32-S3 BLE (${addr.takeLast(5)})"
        }

        val dType = when (dev.type) {
          BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
          BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL
          else -> DeviceType.BLE
        }

        addOrUpdateDevice(
          DiscoveredDevice(
            name = name,
            address = addr,
            rssi = res.rssi,
            device = dev,
            isBonded = isBonded,
            deviceType = dType,
            isEsp32 = isEsp
          )
        )
      }
    }

    @SuppressLint("MissingPermission")
    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
      results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
    }

    override fun onScanFailed(errorCode: Int) {
      emitSystemPacket("BLE scan failed with error code $errorCode")
    }
  }

  @Synchronized
  private fun addOrUpdateDevice(device: DiscoveredDevice) {
    val normAddr = device.address.uppercase()
    val current = _discoveredDevices.value.toMutableList()
    val idx = current.indexOfFirst { it.address.equals(normAddr, ignoreCase = true) }
    if (idx >= 0) {
      val existing = current[idx]
      // Nếu thiết bị đã có trong danh sách thì cập nhật RSSI và ưu tiên giữ tên thực tế
      val bestName = if (existing.name.contains("(") && !device.name.contains("(")) {
        device.name
      } else {
        existing.name
      }
      current[idx] = existing.copy(
        name = bestName,
        rssi = device.rssi,
        isBonded = existing.isBonded || device.isBonded,
        isEsp32 = existing.isEsp32 || device.isEsp32,
        device = existing.device ?: device.device
      )
    } else {
      current.add(device)
    }
    // Sắp xếp: Thiết bị ghép đôi & ESP32 lên đầu, theo cường độ sóng RSSI
    _discoveredDevices.value = current.sortedWith(
      compareByDescending<DiscoveredDevice> { it.isBonded }
        .thenByDescending { it.isEsp32 }
        .thenByDescending { it.rssi }
    )
  }

  fun setSimulatorMode(enabled: Boolean) {
    _isSimulatorMode.value = enabled
    if (enabled && _connectionStatus.value == ConnectionStatus.DISCONNECTED) {
      if (_discoveredDevices.value.none { it.address == simulatedDevice.address }) {
        _discoveredDevices.value = listOf(simulatedDevice) + _discoveredDevices.value
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun connect(device: DiscoveredDevice) {
    stopScan()
    _connectedDeviceName.value = device.name
    _connectedDeviceAddress.value = device.address
    _connectedDeviceType.value = device.deviceType

    if (_isSimulatorMode.value || device.address == simulatedDevice.address || device.device == null) {
      connectSimulator(device.name, device.address)
      return
    }

    _connectionStatus.value = ConnectionStatus.CONNECTING
    disconnectRealConnections()

    // If device is pure Classic Bluetooth (e.g. HC-05, ESP32 Classic Bluetooth Serial)
    if (device.deviceType == DeviceType.CLASSIC) {
      connectClassicBluetooth(device.device)
      return
    }

    // Default: Connect via BLE GATT (ESP32-S3 WROOM 1 BLE, ESP32 BLE GATT, etc.)
    try {
      bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        device.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
      } else {
        device.device.connectGatt(context, false, gattCallback)
      }
    } catch (e: Exception) {
      emitSystemPacket("Connection failed: ${e.localizedMessage}")
      _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
  }

  @SuppressLint("MissingPermission")
  private fun connectClassicBluetooth(device: BluetoothDevice) {
    scope.launch(Dispatchers.IO) {
      try {
        bluetoothAdapter?.cancelDiscovery()
        emitSystemPacket("Connecting via Classic Bluetooth SPP (${device.address})...")
        val socket = device.createRfcommSocketToServiceRecord(sppUuid)
        socket.connect()
        classicSocket = socket
        classicOutputStream = socket.outputStream
        classicInputStream = socket.inputStream

        _connectionStatus.value = ConnectionStatus.CONNECTED
        emitSystemPacket("Connected to ${device.name ?: "Device"} via Classic Bluetooth SPP!")

        // Start background reader
        classicReaderJob = launch(Dispatchers.IO) {
          val buffer = ByteArray(1024)
          while (isActive && classicSocket?.isConnected == true) {
            try {
              val bytesRead = classicInputStream?.read(buffer) ?: -1
              if (bytesRead > 0) {
                val data = buffer.copyOf(bytesRead)
                handleIncomingBytes(data)
              } else if (bytesRead == -1) {
                break
              }
            } catch (_: Exception) {
              break
            }
          }
          if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            emitSystemPacket("Classic Bluetooth connection closed")
          }
        }
      } catch (e: Exception) {
        emitSystemPacket("Classic Bluetooth connection failed: ${e.localizedMessage}. Trying BLE GATT fallback...")
        launch(Dispatchers.Main) {
          try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
              device.connectGatt(context, false, gattCallback)
            }
          } catch (_: Exception) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
          }
        }
      }
    }
  }

  private fun connectSimulator(name: String, address: String) {
    _connectionStatus.value = ConnectionStatus.CONNECTING
    scope.launch {
      delay(300)
      _connectionStatus.value = ConnectionStatus.DISCOVERING_SERVICES
      delay(250)
      _connectionStatus.value = ConnectionStatus.CONNECTED
      _mtuSize.value = 512

      // Provide simulated GATT Services for user to explore and click
      _discoveredGattServices.value = listOf(
        GattServiceInfo(
          uuid = serviceUuidString,
          name = "Nordic UART Service (NUS - ESP32 Chuẩn)",
          characteristics = listOf(
            GattCharInfo(
              uuid = rxCharUuidString,
              serviceUuid = serviceUuidString,
              canRead = false,
              canWrite = true,
              canNotify = false,
              canIndicate = false,
              propertiesDesc = "WRITE, WRITE_NO_RESPONSE",
              name = "RX Characteristic (Ghi/Gửi lệnh tới ESP32)"
            ),
            GattCharInfo(
              uuid = txCharUuidString,
              serviceUuid = serviceUuidString,
              canRead = true,
              canWrite = false,
              canNotify = true,
              canIndicate = false,
              propertiesDesc = "READ, NOTIFY",
              name = "TX Characteristic (Đọc/Nhận từ ESP32)"
            )
          )
        ),
        GattServiceInfo(
          uuid = "0000180A-0000-1000-8000-00805F9B34FB",
          name = "Device Information Service",
          characteristics = listOf(
            GattCharInfo(
              uuid = "00002A29-0000-1000-8000-00805F9B34FB",
              serviceUuid = "0000180A-0000-1000-8000-00805F9B34FB",
              canRead = true,
              canWrite = false,
              canNotify = false,
              canIndicate = false,
              propertiesDesc = "READ",
              name = "Manufacturer Name (Espressif)"
            ),
            GattCharInfo(
              uuid = "00002A24-0000-1000-8000-00805F9B34FB",
              serviceUuid = "0000180A-0000-1000-8000-00805F9B34FB",
              canRead = true,
              canWrite = false,
              canNotify = false,
              canIndicate = false,
              propertiesDesc = "READ",
              name = "Model Number (ESP32-S3-WROOM-1)"
            )
          )
        )
      )

      emitSystemPacket("Connected to simulated $name [$address]")
      emitIncomingPacket("ESP32-S3 BLE Ready. Nordic UART Service active.\r\n")
    }
  }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    simulatorJob?.cancel()
    simulatorJob = null
    disconnectRealConnections()
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
    _connectedDeviceName.value = null
    _connectedDeviceAddress.value = null
    _discoveredGattServices.value = emptyList()
    emitSystemPacket("Disconnected")
  }

  @SuppressLint("MissingPermission")
  private fun disconnectRealConnections() {
    classicReaderJob?.cancel()
    classicReaderJob = null
    try {
      classicOutputStream?.close()
      classicInputStream?.close()
      classicSocket?.close()
    } catch (_: Exception) {}
    classicOutputStream = null
    classicInputStream = null
    classicSocket = null

    try {
      bluetoothGatt?.disconnect()
      bluetoothGatt?.close()
    } catch (_: Exception) {}
    bluetoothGatt = null
    writeCharacteristic = null
    notifyCharacteristic = null
  }

  fun send(text: String, isHex: Boolean = false) {
    val bytes = if (isHex) {
      parseHexToBytes(text)
    } else {
      text.toByteArray(Charsets.UTF_8)
    }

    if (bytes.isEmpty()) return

    val hexRepr = bytesToHex(bytes)
    scope.launch {
      _incomingPackets.emit(
        BlePacket(
          direction = "TX",
          text = if (isHex) "[HEX] $text" else text,
          hex = hexRepr
        )
      )
    }

    if (_isSimulatorMode.value || simulatorJob != null || _connectedDeviceAddress.value == simulatedDevice.address) {
      handleSimulatorCommand(text.trim())
      return
    }

    // If Classic Bluetooth SPP socket is active
    if (classicSocket != null && classicSocket?.isConnected == true) {
      scope.launch(Dispatchers.IO) {
        try {
          classicOutputStream?.write(bytes)
          classicOutputStream?.flush()
        } catch (e: Exception) {
          emitSystemPacket("Classic BT Write Error: ${e.localizedMessage}")
        }
      }
      return
    }

    // BLE GATT write
    val gatt = bluetoothGatt
    val charac = writeCharacteristic
    if (gatt == null || charac == null || _connectionStatus.value != ConnectionStatus.CONNECTED) {
      emitSystemPacket("Not connected to a BLE or Bluetooth device")
      return
    }

    enqueueWrite(bytes)
  }

  private fun handleSimulatorCommand(cmd: String) {
    scope.launch {
      delay(60)
      val upper = cmd.uppercase()
      when {
        upper.startsWith("LED:ON") -> emitIncomingPacket("OK: GPIO2 LED turned ON\r\n")
        upper.startsWith("LED:OFF") -> emitIncomingPacket("OK: GPIO2 LED turned OFF\r\n")
        upper.startsWith("RELAY:1:ON") -> emitIncomingPacket("OK: Relay 1 turned ON\r\n")
        upper.startsWith("RELAY:1:OFF") -> emitIncomingPacket("OK: Relay 1 turned OFF\r\n")
        upper.startsWith("RELAY:1") -> emitIncomingPacket("OK: Relay 1 toggled\r\n")
        upper.startsWith("RELAY:2:ON") -> emitIncomingPacket("OK: Relay 2 turned ON\r\n")
        upper.startsWith("RELAY:2:OFF") -> emitIncomingPacket("OK: Relay 2 turned OFF\r\n")
        upper.startsWith("RELAY:2") -> emitIncomingPacket("OK: Relay 2 toggled\r\n")
        upper.startsWith("BUZZER:ON") -> emitIncomingPacket("OK: Buzzer turned ON\r\n")
        upper.startsWith("BUZZER:OFF") -> emitIncomingPacket("OK: Buzzer turned OFF\r\n")
        upper.startsWith("BUZZER") -> emitIncomingPacket("OK: Buzzer tone beeped\r\n")
        upper.startsWith("STATUS") -> emitIncomingPacket("ESP32-S3 STATUS: OK, Heap: 284512B, Core: 240MHz, Flash: 8MB, Temp: 31.4C\r\n")
        upper.startsWith("GET:DATA") || upper.startsWith("GET") || upper.startsWith("DATA") || upper.startsWith("TEMP") -> {
          val temp = 28.5f + ((-8..8).random() * 0.1f)
          val hum = 63.0f + ((-10..10).random() * 0.1f)
          val volt = 3.32f
          emitIncomingPacket("""{"temp":%.1f,"hum":%.1f,"volt":%.2f,"r1":1,"r2":0}""".format(temp, hum, volt) + "\r\n")
        }
        upper.startsWith("RESET") -> emitIncomingPacket("[ESP32-S3] Restarting MCU...\r\n[ESP32-S3] BLE NUS Ready.\r\n")
        upper.startsWith("PWM:") -> emitIncomingPacket("OK: PWM duty set to ${cmd.substringAfter(":")}\r\n")
        upper.startsWith("SERVO:") -> emitIncomingPacket("OK: Servo angle set to ${cmd.substringAfter(":")} deg\r\n")
        upper.startsWith("CMD:") -> emitIncomingPacket("ROBOT ACK: ${cmd.substringAfter(":")}\r\n")
        else -> emitIncomingPacket("OK: ${cmd.trim()}\r\n")
      }
    }
  }

  private fun enqueueWrite(data: ByteArray) {
    synchronized(writeQueue) {
      writeQueue.add(data)
      if (!isWriting) {
        processNextWrite()
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun processNextWrite() {
    synchronized(writeQueue) {
      val data = writeQueue.poll() ?: run {
        isWriting = false
        return
      }
      isWriting = true

      val gatt = bluetoothGatt ?: run {
        isWriting = false
        return
      }
      val charac = writeCharacteristic ?: run {
        isWriting = false
        return
      }

      val writeType = if (charac.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
      } else {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(charac, data, writeType)
      } else {
        @Suppress("DEPRECATION")
        charac.value = data
        @Suppress("DEPRECATION")
        charac.writeType = writeType
        @Suppress("DEPRECATION")
        gatt.writeCharacteristic(charac)
      }
    }
  }

  private val gattCallback = object : BluetoothGattCallback() {
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        _connectionStatus.value = ConnectionStatus.DISCOVERING_SERVICES
        emitSystemPacket("Connected. Requesting MTU 512...")
        try {
          gatt?.requestMtu(512)
        } catch (_: Exception) {
          gatt?.discoverServices()
        }
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        emitSystemPacket("Disconnected from device")
        disconnectRealConnections()
      }
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        _mtuSize.value = mtu
        emitSystemPacket("MTU set to $mtu")
      }
      gatt?.discoverServices()
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
        // Parse all services and characteristics
        val serviceList = mutableListOf<GattServiceInfo>()
        for (srv in gatt.services) {
          val sUuidStr = srv.uuid.toString().uppercase()
          val charList = mutableListOf<GattCharInfo>()
          for (ch in srv.characteristics) {
            val cUuidStr = ch.uuid.toString().uppercase()
            val canRead = ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
            val canWrite = (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
              (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            val canNotify = ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            val canIndicate = ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

            val props = mutableListOf<String>()
            if (canRead) props.add("READ")
            if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("WRITE")
            if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("WRITE_NO_RESP")
            if (canNotify) props.add("NOTIFY")
            if (canIndicate) props.add("INDICATE")

            val cName = getFriendlyCharacteristicName(cUuidStr, sUuidStr)
            charList.add(
              GattCharInfo(
                uuid = cUuidStr,
                serviceUuid = sUuidStr,
                canRead = canRead,
                canWrite = canWrite,
                canNotify = canNotify,
                canIndicate = canIndicate,
                propertiesDesc = props.joinToString(", "),
                name = cName
              )
            )
          }
          serviceList.add(
            GattServiceInfo(
              uuid = sUuidStr,
              name = getFriendlyServiceName(sUuidStr),
              characteristics = charList
            )
          )
        }
        _discoveredGattServices.value = serviceList

        // Auto-bind active characteristics based on configured UUIDs or first suitable NUS
        bindCharacteristics(gatt)
      } else {
        emitSystemPacket("Service discovery failed with status $status")
        _connectionStatus.value = ConnectionStatus.CONNECTED
      }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
      processNextWrite()
    }

    // Android 13+
    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray
    ) {
      handleIncomingBytes(value)
    }

    // Pre-Android 13
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
      @Suppress("DEPRECATION")
      val data = characteristic?.value ?: return
      handleIncomingBytes(data)
    }
  }

  @SuppressLint("MissingPermission")
  private fun bindCharacteristics(gatt: BluetoothGatt) {
    val sUuid = try { UUID.fromString(serviceUuidString) } catch (_: Exception) { null }
    val rxUuid = try { UUID.fromString(rxCharUuidString) } catch (_: Exception) { null }
    val txUuid = try { UUID.fromString(txCharUuidString) } catch (_: Exception) { null }

    var targetService = sUuid?.let { gatt.getService(it) }
    if (targetService == null) {
      // Find any service having both a writable and a notifiable characteristic
      for (service in gatt.services) {
        val hasWrite = service.characteristics.any {
          it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
            it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        }
        val hasNotify = service.characteristics.any {
          it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        }
        if (hasWrite && hasNotify) {
          targetService = service
          // Update configured active service UUID
          serviceUuidString = service.uuid.toString().uppercase()
          _activeServiceUuid.value = serviceUuidString
          break
        }
      }
    }

    if (targetService != null) {
      writeCharacteristic = rxUuid?.let { targetService.getCharacteristic(it) }
        ?: targetService.characteristics.firstOrNull {
          it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
            it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        }
      writeCharacteristic?.let {
        rxCharUuidString = it.uuid.toString().uppercase()
        _activeRxUuid.value = rxCharUuidString
      }

      val notifyChar = txUuid?.let { targetService.getCharacteristic(it) }
        ?: targetService.characteristics.firstOrNull {
          it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        }

      if (notifyChar != null) {
        notifyCharacteristic = notifyChar
        txCharUuidString = notifyChar.uuid.toString().uppercase()
        _activeTxUuid.value = txCharUuidString

        gatt.setCharacteristicNotification(notifyChar, true)
        val desc = notifyChar.getDescriptor(cccdUuid)
        if (desc != null) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
          } else {
            @Suppress("DEPRECATION")
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(desc)
          }
        }
      }

      _connectionStatus.value = ConnectionStatus.CONNECTED
      emitSystemPacket("GATT Ready: Write [${rxCharUuidString.take(8)}...], Read [${txCharUuidString.take(8)}...]")
    } else {
      emitSystemPacket("Connected. Please select Service & Characteristics from the GATT list.")
      _connectionStatus.value = ConnectionStatus.CONNECTED
    }
  }

  @SuppressLint("MissingPermission")
  fun setCustomUuids(service: String, rx: String, tx: String) {
    serviceUuidString = service.trim().uppercase()
    rxCharUuidString = rx.trim().uppercase()
    txCharUuidString = tx.trim().uppercase()

    _activeServiceUuid.value = serviceUuidString
    _activeRxUuid.value = rxCharUuidString
    _activeTxUuid.value = txCharUuidString

    // If currently connected to a real GATT device, rebind
    val gatt = bluetoothGatt
    if (gatt != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      bindCharacteristics(gatt)
    }
  }

  @SuppressLint("MissingPermission")
  fun setWriteCharacteristic(charUuid: String) {
    rxCharUuidString = charUuid.trim().uppercase()
    _activeRxUuid.value = rxCharUuidString

    val gatt = bluetoothGatt
    if (gatt != null) {
      val parsedUuid = try { UUID.fromString(rxCharUuidString) } catch (_: Exception) { null }
      if (parsedUuid != null) {
        for (srv in gatt.services) {
          val ch = srv.getCharacteristic(parsedUuid)
          if (ch != null) {
            writeCharacteristic = ch
            emitSystemPacket("Write Characteristic updated: ${ch.uuid}")
            break
          }
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun setReadCharacteristic(charUuid: String) {
    txCharUuidString = charUuid.trim().uppercase()
    _activeTxUuid.value = txCharUuidString

    val gatt = bluetoothGatt
    if (gatt != null) {
      val parsedUuid = try { UUID.fromString(txCharUuidString) } catch (_: Exception) { null }
      if (parsedUuid != null) {
        for (srv in gatt.services) {
          val ch = srv.getCharacteristic(parsedUuid)
          if (ch != null) {
            notifyCharacteristic = ch
            gatt.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(cccdUuid)
            if (desc != null) {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
              } else {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(desc)
              }
            }
            emitSystemPacket("Read/Notify Characteristic updated: ${ch.uuid}")
            break
          }
        }
      }
    }
  }

  private fun getFriendlyServiceName(uuidStr: String): String {
    return when {
      uuidStr.startsWith("6E400001", ignoreCase = true) -> "Nordic UART Service (NUS - ESP32 Chuẩn)"
      uuidStr.startsWith("0000FFE0", ignoreCase = true) -> "HM-10 / CC2541 Serial Service"
      uuidStr.startsWith("0000180A", ignoreCase = true) -> "Device Information Service"
      uuidStr.startsWith("00001800", ignoreCase = true) -> "Generic Access Service"
      uuidStr.startsWith("00001801", ignoreCase = true) -> "Generic Attribute Service"
      uuidStr.startsWith("0000180F", ignoreCase = true) -> "Battery Service"
      uuidStr.startsWith("4FAFC201", ignoreCase = true) -> "ESP32 Custom GATT Service"
      uuidStr.startsWith("0000FFFF", ignoreCase = true) -> "ESP32 SPP-over-BLE Service"
      else -> "Custom Service"
    }
  }

  private fun getFriendlyCharacteristicName(cUuidStr: String, sUuidStr: String): String {
    return when {
      cUuidStr.startsWith("6E400002", ignoreCase = true) -> "RX Characteristic (Ghi/Gửi lệnh tới ESP32)"
      cUuidStr.startsWith("6E400003", ignoreCase = true) -> "TX Characteristic (Đọc/Nhận từ ESP32)"
      cUuidStr.startsWith("0000FFE1", ignoreCase = true) -> "Serial TX/RX Characteristic (HM-10)"
      cUuidStr.startsWith("00002A00", ignoreCase = true) -> "Device Name"
      cUuidStr.startsWith("00002A29", ignoreCase = true) -> "Manufacturer Name"
      cUuidStr.startsWith("00002A24", ignoreCase = true) -> "Model Number"
      cUuidStr.startsWith("00002A19", ignoreCase = true) -> "Battery Level"
      else -> "Custom Characteristic"
    }
  }

  private fun handleIncomingBytes(bytes: ByteArray) {
    val text = String(bytes, Charsets.UTF_8)
    val hex = bytesToHex(bytes)
    scope.launch {
      _incomingPackets.emit(
        BlePacket(
          direction = "RX",
          text = text,
          hex = hex
        )
      )
    }
  }

  private fun emitIncomingPacket(text: String) {
    scope.launch {
      _incomingPackets.emit(
        BlePacket(
          direction = "RX",
          text = text,
          hex = bytesToHex(text.toByteArray(Charsets.UTF_8))
        )
      )
    }
  }

  private fun emitSystemPacket(message: String) {
    scope.launch {
      _incomingPackets.emit(
        BlePacket(
          direction = "SYS",
          text = message,
          hex = ""
        )
      )
    }
  }

  private fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString(" ") { "%02X".format(it) }
  }

  private fun parseHexToBytes(hex: String): ByteArray {
    val clean = hex.replace("0x", "", ignoreCase = true)
      .replace(",", "")
      .replace(" ", "")
      .trim()
    if (clean.length % 2 != 0) return byteArrayOf()
    return try {
      clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    } catch (_: Exception) {
      byteArrayOf()
    }
  }
}
