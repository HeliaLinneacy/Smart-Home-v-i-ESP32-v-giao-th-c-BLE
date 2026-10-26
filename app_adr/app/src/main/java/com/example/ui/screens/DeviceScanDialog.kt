package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ble.ConnectionStatus
import com.example.ble.DeviceType
import com.example.ble.DiscoveredDevice
import com.example.ble.GattCharInfo
import com.example.ble.GattServiceInfo
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDarkBg
import com.example.ui.theme.TerminalDarkCard
import com.example.ui.theme.TerminalMint
import com.example.ui.theme.TerminalRed
import com.example.viewmodel.MainViewModel

@Composable
fun DeviceScanDialog(
  viewModel: MainViewModel,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
  val connectedName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
  val connectedAddress by viewModel.connectedDeviceAddress.collectAsStateWithLifecycle()
  val devices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
  val isSimulator by viewModel.isSimulatorMode.collectAsStateWithLifecycle()

  val discoveredGattServices by viewModel.discoveredGattServices.collectAsStateWithLifecycle()
  val activeServiceUuid by viewModel.activeServiceUuid.collectAsStateWithLifecycle()
  val activeRxUuid by viewModel.activeRxUuid.collectAsStateWithLifecycle()
  val activeTxUuid by viewModel.activeTxUuid.collectAsStateWithLifecycle()

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Quét thiết bị, 1: Cấu hình UUID & GATT

  // Form input states for UUIDs
  var serviceUuidInput by remember { mutableStateOf(activeServiceUuid) }
  var rxUuidInput by remember { mutableStateOf(activeRxUuid) } // Write to ESP32
  var txUuidInput by remember { mutableStateOf(activeTxUuid) } // Read from ESP32

  // Sync inputs with active UUIDs when they update
  LaunchedEffect(activeServiceUuid, activeRxUuid, activeTxUuid) {
    serviceUuidInput = activeServiceUuid
    rxUuidInput = activeRxUuid
    txUuidInput = activeTxUuid
  }

  // Device list filter (0: All, 1: ESP32 & BLE, 2: Classic BT & Paired)
  var deviceCategoryFilter by remember { mutableIntStateOf(0) }

  // Check hardware Bluetooth state
  val isBtEnabled = viewModel.bleManager.bluetoothAdapter?.isEnabled == true

  // Check Location enabled (vital on many Android versions for BLE)
  val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
  val isLocationEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

  // Check BLE Permissions
  var hasPermissions by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
      } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
      }
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    hasPermissions = perms.values.all { it }
    if (hasPermissions) {
      viewModel.startScan()
    }
  }

  LaunchedEffect(Unit) {
    if (hasPermissions && isBtEnabled) {
      viewModel.startScan()
    }
  }

  // Filtered devices (0: Tất cả, 1: Chỉ ESP32, 2: Thiết bị Bluetooth thông thường)
  val filteredDevices = remember(devices, deviceCategoryFilter) {
    when (deviceCategoryFilter) {
      1 -> devices.filter { it.isEsp32 }
      2 -> devices.filter { !it.isEsp32 }
      else -> devices
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.fillMaxWidth(0.96f),
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = TerminalCyan)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Quét & Cấu Hình ESP32", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Tab Row: [1. Quét thiết bị (BLE & Classic)] [2. Cấu hình UUID & GATT]
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = TerminalCyan,
          indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
              modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
              color = TerminalCyan
            )
          }
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Quét thiết bị (${devices.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cấu hình UUID", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (discoveredGattServices.isNotEmpty()) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Box(
                    modifier = Modifier
                      .background(TerminalMint, CircleShape)
                      .size(6.dp)
                  )
                }
              }
            }
          )
        }

        // Active Connection Info (if connected)
        if (connectionStatus == ConnectionStatus.CONNECTED) {
          Card(
            colors = CardDefaults.cardColors(containerColor = TerminalMint.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TerminalMint, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Đang kết nối: ${connectedName ?: "ESP32"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TerminalMint
                  )
                }
                Text(
                  text = connectedAddress ?: "",
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Button(
                onClick = { viewModel.disconnect() },
                colors = ButtonDefaults.buttonColors(containerColor = TerminalRed),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("disconnect_button")
              ) {
                Text("Ngắt kết nối", fontSize = 11.sp)
              }
            }
          }
        }

        // TAB 0: Quét thiết bị (BLE & Classic)
        if (selectedTab == 0) {
          // Warning if Bluetooth hardware is turned off
          if (!isBtEnabled) {
            Card(
              colors = CardDefaults.cardColors(containerColor = TerminalAmber.copy(alpha = 0.2f)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TerminalAmber, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text("Bluetooth đang TẮT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TerminalAmber)
                  Text("Vui lòng bật Bluetooth trên điện thoại để quét thiết bị ESP32.", fontSize = 11.sp)
                }
                TextButton(onClick = {
                  try {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                  } catch (_: Exception) {}
                }) {
                  Text("Bật", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Helpful tip if Location is disabled on older Android or certain OEM devices
          if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !isLocationEnabled) {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = TerminalCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Mẹo: Hãy bật Vị trí (GPS) nếu điện thoại không tìm thấy thiết bị BLE.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          // Permissions banner if not granted
          if (!hasPermissions) {
            Card(
              colors = CardDefaults.cardColors(containerColor = TerminalAmber.copy(alpha = 0.2f)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("Cần cấp quyền Bluetooth để quét ESP32", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                  onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                      permissionLauncher.launch(
                        arrayOf(
                          Manifest.permission.BLUETOOTH_SCAN,
                          Manifest.permission.BLUETOOTH_CONNECT,
                          Manifest.permission.ACCESS_FINE_LOCATION,
                          Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                      )
                    } else {
                      permissionLauncher.launch(
                        arrayOf(
                          Manifest.permission.ACCESS_FINE_LOCATION,
                          Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                      )
                    }
                  },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("Cấp quyền quét Bluetooth")
                }
              }
            }
          }

          // Simulator toggle card
          Card(
            colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Memory, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Mô phỏng ESP32-S3 (Simulator)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                  text = "Dùng thử nghiệm trên máy ảo hoặc khi chưa có board mạch ESP32",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = isSimulator,
                onCheckedChange = { viewModel.toggleSimulatorMode(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = TerminalCyan)
              )
            }
          }

          // Category Filter Chips
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            FilterChip(
              selected = deviceCategoryFilter == 0,
              onClick = { deviceCategoryFilter = 0 },
              label = { Text("Tất cả (${devices.size})", fontSize = 11.sp) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TerminalCyan.copy(alpha = 0.2f))
            )
            FilterChip(
              selected = deviceCategoryFilter == 1,
              onClick = { deviceCategoryFilter = 1 },
              label = {
                Text(
                  "ESP32 (${devices.count { it.isEsp32 }})",
                  fontSize = 11.sp
                )
              },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TerminalCyan.copy(alpha = 0.2f))
            )
            FilterChip(
              selected = deviceCategoryFilter == 2,
              onClick = { deviceCategoryFilter = 2 },
              label = {
                Text(
                  "BT thông thường (${devices.count { !it.isEsp32 }})",
                  fontSize = 11.sp
                )
              },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TerminalAmber.copy(alpha = 0.2f))
            )
          }

          // Scan Action Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (connectionStatus == ConnectionStatus.SCANNING) "Đang tìm ESP32 & BT thường..." else "Tìm thấy ${filteredDevices.size} thiết bị",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
              if (connectionStatus == ConnectionStatus.SCANNING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TerminalCyan)
                Spacer(modifier = Modifier.width(6.dp))
              }

              TextButton(
                onClick = {
                  if (connectionStatus == ConnectionStatus.SCANNING) {
                    viewModel.stopScan()
                  } else {
                    viewModel.startScan()
                  }
                }
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (connectionStatus == ConnectionStatus.SCANNING) "Dừng" else "Quét lại", fontSize = 12.sp)
              }
            }
          }

          // Devices List Container
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp)
              .background(TerminalDarkBg, RoundedCornerShape(10.dp))
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
              .padding(4.dp)
          ) {
            if (filteredDevices.isEmpty()) {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = if (connectionStatus == ConnectionStatus.SCANNING) {
                      "Đang quét tìm ESP32-S3 và Bluetooth..."
                    } else {
                      "Không tìm thấy thiết bị. Hãy bấm 'Quét lại' hoặc kiểm tra nguồn ESP32."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                items(filteredDevices) { dev ->
                  EnhancedDeviceItemRow(
                    device = dev,
                    isConnected = dev.address.equals(connectedAddress, ignoreCase = true),
                    onConnect = {
                      viewModel.connect(dev)
                      // If user connects, switch to UUID tab to view discovered services
                      selectedTab = 1
                    }
                  )
                }
              }
            }
          }
        }

        // TAB 1: Cấu hình UUID BLE & GATT Explorer
        if (selectedTab == 1) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Explanation Banner
            Card(
              colors = CardDefaults.cardColors(containerColor = TerminalCyan.copy(alpha = 0.1f)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Cấu hình 3 dòng UUID cho ESP32 BLE. Bạn có thể tự điền, chọn mẫu chuẩn, hoặc bấm trực tiếp vào danh sách GATT tìm thấy bên dưới!",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }

            // Quick Preset Profiles Buttons
            Text("Mẫu cấu hình thông dụng (1 chạm):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              AssistChip(
                onClick = {
                  serviceUuidInput = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
                  rxUuidInput = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
                  txUuidInput = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
                  viewModel.setCustomUuids(serviceUuidInput, rxUuidInput, txUuidInput)
                  Toast.makeText(context, "Đã chọn Nordic UART Service (ESP32 Chuẩn)", Toast.LENGTH_SHORT).show()
                },
                label = { Text("ESP32 Nordic UART (NUS)", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.DeveloperBoard, contentDescription = null, modifier = Modifier.size(14.dp), tint = TerminalCyan) }
              )

              AssistChip(
                onClick = {
                  serviceUuidInput = "4FAFC201-1FB5-459E-8FCC-C5C9C331914B"
                  rxUuidInput = "BEB5483E-36E1-4688-B7F5-EA07361B26A8"
                  txUuidInput = "BEB5483E-36E1-4688-B7F5-EA07361B26A8"
                  viewModel.setCustomUuids(serviceUuidInput, rxUuidInput, txUuidInput)
                  Toast.makeText(context, "Đã chọn ESP32 Custom GATT", Toast.LENGTH_SHORT).show()
                },
                label = { Text("ESP32 Custom GATT", fontSize = 10.sp) }
              )

              AssistChip(
                onClick = {
                  serviceUuidInput = "0000FFE0-0000-1000-8000-00805F9B34FB"
                  rxUuidInput = "0000FFE1-0000-1000-8000-00805F9B34FB"
                  txUuidInput = "0000FFE1-0000-1000-8000-00805F9B34FB"
                  viewModel.setCustomUuids(serviceUuidInput, rxUuidInput, txUuidInput)
                  Toast.makeText(context, "Đã chọn HM-10 / CC2541 Serial", Toast.LENGTH_SHORT).show()
                },
                label = { Text("HM-10 Serial (0xFFE0)", fontSize = 10.sp) }
              )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // CLEAR 3-LINE UUID CONFIGURATION AS REQUESTED
            Text(
              text = "Mục cấu hình UUID BLE (Rõ ràng từng dòng):",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = TerminalCyan
            )

            // DÒNG 1: SERVICE UUID
            Card(
              colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1. Service UUID (Dịch vụ chính)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }
                  IconButton(
                    onClick = {
                      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clip.setPrimaryClip(ClipData.newPlainText("Service UUID", serviceUuidInput))
                      Toast.makeText(context, "Đã sao chép Service UUID", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                OutlinedTextField(
                  value = serviceUuidInput,
                  onValueChange = { serviceUuidInput = it },
                  placeholder = { Text("Ví dụ: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E", fontSize = 10.sp) },
                  singleLine = true,
                  textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                )
              }
            }

            // DÒNG 2: READ / NOTIFY CHARACTERISTIC UUID
            Card(
              colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SouthWest, contentDescription = null, tint = TerminalMint, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("2. Read / Notify Characteristic UUID (Đọc / Nhận)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TerminalMint)
                  }
                  IconButton(
                    onClick = {
                      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clip.setPrimaryClip(ClipData.newPlainText("Read UUID", txUuidInput))
                      Toast.makeText(context, "Đã sao chép Read UUID", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                Text("Dùng để nhận dữ liệu cảm biến & phản hồi từ ESP32 gửi lên", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                  value = txUuidInput,
                  onValueChange = { txUuidInput = it },
                  placeholder = { Text("Ví dụ: 6E400003-B5A3-F393-E0A9-E50E24DCCA9E", fontSize = 10.sp) },
                  singleLine = true,
                  textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                )
              }
            }

            // DÒNG 3: WRITE CHARACTERISTIC UUID
            Card(
              colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NorthEast, contentDescription = null, tint = TerminalCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("3. Write Characteristic UUID (Ghi / Gửi lệnh)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TerminalCyan)
                  }
                  IconButton(
                    onClick = {
                      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clip.setPrimaryClip(ClipData.newPlainText("Write UUID", rxUuidInput))
                      Toast.makeText(context, "Đã sao chép Write UUID", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                Text("Dùng để gửi lệnh điều khiển relay, switch, text từ điện thoại xuống ESP32", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                  value = rxUuidInput,
                  onValueChange = { rxUuidInput = it },
                  placeholder = { Text("Ví dụ: 6E400002-B5A3-F393-E0A9-E50E24DCCA9E", fontSize = 10.sp) },
                  singleLine = true,
                  textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                )
              }
            }

            // Apply Button
            Button(
              onClick = {
                viewModel.setCustomUuids(serviceUuidInput, rxUuidInput, txUuidInput)
                Toast.makeText(context, "Đã lưu cấu hình UUID!", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.align(Alignment.End),
              colors = ButtonDefaults.buttonColors(containerColor = TerminalCyan)
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Lưu & Áp Dụng UUID", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // GATT EXPLORER (Dịch vụ & Đặc tính tìm thấy trên thiết bị để bấm vào không nhầm)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "Dịch vụ & Đặc tính tìm thấy trên ESP32:",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Bấm trực tiếp vào đặc tính để gán tự động mà không sợ gõ nhầm",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (discoveredGattServices.isEmpty()) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Text(
                    text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                      "Đang đọc danh sách GATT Services từ thiết bị..."
                    } else {
                      "Chưa kết nối thiết bị BLE nào. Khi bạn bấm 'Kết nối', toàn bộ Services & Characteristics thực tế sẽ hiện ngay tại đây để bấm chọn 1 chạm!"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (connectionStatus != ConnectionStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                      onClick = {
                        viewModel.toggleSimulatorMode(true)
                        viewModel.connect(viewModel.bleManager.simulatedDevice)
                      }
                    ) {
                      Text("Xem trước trên ESP32 Simulator", fontSize = 11.sp)
                    }
                  }
                }
              }
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                discoveredGattServices.forEach { srv ->
                  GattServiceCard(
                    service = srv,
                    activeServiceUuid = activeServiceUuid,
                    activeRxUuid = activeRxUuid,
                    activeTxUuid = activeTxUuid,
                    onSelectAsService = {
                      serviceUuidInput = srv.uuid
                      viewModel.setCustomUuids(serviceUuidInput, rxUuidInput, txUuidInput)
                      Toast.makeText(context, "Đã chọn Service: ${srv.uuid.take(8)}...", Toast.LENGTH_SHORT).show()
                    },
                    onSelectAsWrite = { charUuid ->
                      rxUuidInput = charUuid
                      viewModel.selectCharacteristicAsWrite(charUuid)
                      Toast.makeText(context, "Đã gán làm Write UUID: ${charUuid.take(8)}...", Toast.LENGTH_SHORT).show()
                    },
                    onSelectAsRead = { charUuid ->
                      txUuidInput = charUuid
                      viewModel.selectCharacteristicAsRead(charUuid)
                      Toast.makeText(context, "Đã gán làm Read UUID: ${charUuid.take(8)}...", Toast.LENGTH_SHORT).show()
                    }
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Đóng")
      }
    }
  )
}

@Composable
fun EnhancedDeviceItemRow(
  device: DiscoveredDevice,
  isConnected: Boolean,
  onConnect: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (isConnected) TerminalMint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onConnect() }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        // Status indicator dot
        Box(
          modifier = Modifier
            .size(10.dp)
            .background(
              color = when {
                isConnected -> TerminalMint
                device.isEsp32 -> TerminalCyan
                device.isBonded -> TerminalAmber
                else -> Color.Gray
              },
              shape = CircleShape
            )
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = device.name,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = if (device.isEsp32) TerminalCyan else MaterialTheme.colorScheme.onSurface,
              maxLines = 1
            )
          }

          Spacer(modifier = Modifier.height(2.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // Badges
            if (device.isEsp32) {
              Box(
                modifier = Modifier
                  .background(TerminalCyan.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text("ESP32-S3 BLE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TerminalCyan)
              }
            } else if (device.deviceType == DeviceType.BLE) {
              Box(
                modifier = Modifier
                  .background(TerminalCyan.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text("BLE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TerminalCyan)
              }
            } else {
              Box(
                modifier = Modifier
                  .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text("BT Classic", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
              }
            }

            if (device.isBonded) {
              Box(
                modifier = Modifier
                  .background(TerminalAmber.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text("Đã ghép đôi", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TerminalAmber)
              }
            }

            Text(
              text = "${device.address} • ${device.rssi} dBm",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      Button(
        onClick = onConnect,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.height(30.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isConnected) TerminalMint else MaterialTheme.colorScheme.primary
        )
      ) {
        Text(if (isConnected) "Đã nối" else "Kết nối", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GattServiceCard(
  service: GattServiceInfo,
  activeServiceUuid: String,
  activeRxUuid: String,
  activeTxUuid: String,
  onSelectAsService: () -> Unit,
  onSelectAsWrite: (String) -> Unit,
  onSelectAsRead: (String) -> Unit
) {
  val isCurrentServiceActive = service.uuid.equals(activeServiceUuid, ignoreCase = true)

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (isCurrentServiceActive) TerminalCyan.copy(alpha = 0.12f) else TerminalDarkCard
    ),
    border = if (isCurrentServiceActive) androidx.compose.foundation.BorderStroke(1.dp, TerminalCyan) else null,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = service.name,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (isCurrentServiceActive) TerminalCyan else MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = service.uuid,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        OutlinedButton(
          onClick = onSelectAsService,
          modifier = Modifier.height(28.dp),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text("Chọn Service", fontSize = 10.sp)
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
      Spacer(modifier = Modifier.height(6.dp))

      Text("Characteristics bên trong (${service.characteristics.size}):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

      Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        service.characteristics.forEach { ch ->
          val isWriteActive = ch.uuid.equals(activeRxUuid, ignoreCase = true)
          val isReadActive = ch.uuid.equals(activeTxUuid, ignoreCase = true)

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                color = when {
                  isWriteActive && isReadActive -> TerminalMint.copy(alpha = 0.15f)
                  isWriteActive -> TerminalCyan.copy(alpha = 0.15f)
                  isReadActive -> TerminalMint.copy(alpha = 0.15f)
                  else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(6.dp)
              )
              .padding(6.dp)
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(text = ch.name, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                  Text(
                    text = ch.uuid,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = "Thuộc tính: ${ch.propertiesDesc}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                // Status tag if currently selected
                if (isWriteActive) {
                  Box(
                    modifier = Modifier
                      .background(TerminalCyan, RoundedCornerShape(4.dp))
                      .padding(horizontal = 4.dp, vertical = 2.dp)
                  ) {
                    Text("ĐANG DÙNG (WRITE)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                  }
                }
                if (isReadActive) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Box(
                    modifier = Modifier
                      .background(TerminalMint, RoundedCornerShape(4.dp))
                      .padding(horizontal = 4.dp, vertical = 2.dp)
                  ) {
                    Text("ĐANG DÙNG (READ)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                  }
                }
              }

              // Direct 1-tap assignment buttons so user won't mistype!
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
              ) {
                if (ch.canWrite) {
                  Button(
                    onClick = { onSelectAsWrite(ch.uuid) },
                    modifier = Modifier.height(26.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalCyan)
                  ) {
                    Icon(Icons.Default.NorthEast, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Chọn làm Write UUID", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                  }
                }

                if (ch.canRead || ch.canNotify) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Button(
                    onClick = { onSelectAsRead(ch.uuid) },
                    modifier = Modifier.height(26.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalMint)
                  ) {
                    Icon(Icons.Default.SouthWest, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Chọn làm Read UUID", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
