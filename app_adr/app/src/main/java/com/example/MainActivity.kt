package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ble.ConnectionStatus
import com.example.ui.screens.ControlScreen
import com.example.ui.screens.DeviceScanDialog
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.TelemetryScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDarkBg
import com.example.ui.theme.TerminalDarkSurface
import com.example.ui.theme.TerminalMint
import com.example.ui.theme.TerminalRed
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: MainViewModel = viewModel()
        Esp32BleApp(viewModel = viewModel)
      }
    }
  }
}

enum class AppTab(val title: String, val testTag: String) {
  TERMINAL("Terminal", "tab_terminal"),
  CONTROL("Điều khiển", "tab_control"),
  TELEMETRY("Cảm biến", "tab_telemetry"),
  LOGS("Nhật ký DB", "tab_logs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32BleApp(viewModel: MainViewModel) {
  var selectedTabIndex by remember { mutableIntStateOf(0) }
  var showScanDialog by remember { mutableStateOf(false) }

  val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
  val connectedName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
  val isSimulator by viewModel.isSimulatorMode.collectAsStateWithLifecycle()
  val dbRecentLogs by viewModel.dbRecentLogs.collectAsStateWithLifecycle()

  // Handle system back button when on sub-tabs
  BackHandler(enabled = selectedTabIndex != 0) {
    selectedTabIndex = 0
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showScanDialog = true }
          ) {
            Box(
              modifier = Modifier
                .size(9.dp)
                .background(
                  color = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> TerminalMint
                    ConnectionStatus.CONNECTING, ConnectionStatus.DISCOVERING_SERVICES -> TerminalAmber
                    ConnectionStatus.SCANNING -> TerminalCyan
                    ConnectionStatus.DISCONNECTED -> TerminalRed
                  },
                  shape = CircleShape
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "ESP32 BLE",
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
              color = TerminalCyan,
              fontFamily = FontFamily.Monospace
            )
            if (isSimulator) {
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                  .background(TerminalAmber.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                  .padding(horizontal = 4.dp, vertical = 2.dp)
              ) {
                Text("SIMULATOR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TerminalAmber)
              }
            }
          }
        },
        actions = {
          // Toggle simulator mode button
          IconButton(
            onClick = { viewModel.toggleSimulatorMode(!isSimulator) },
            modifier = Modifier.testTag("toggle_simulator_mode_button")
          ) {
            Icon(
              imageVector = Icons.Default.DeveloperMode,
              contentDescription = "Simulator Mode",
              tint = if (isSimulator) TerminalAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          // Scan / Connect dialog trigger
          IconButton(
            onClick = { showScanDialog = true },
            modifier = Modifier.testTag("top_bar_bluetooth_button")
          ) {
            Icon(
              imageVector = if (connectionStatus == ConnectionStatus.CONNECTED) {
                Icons.Default.BluetoothConnected
              } else {
                Icons.Default.Bluetooth
              },
              contentDescription = "Bluetooth Status",
              tint = if (connectionStatus == ConnectionStatus.CONNECTED) TerminalMint else TerminalCyan
            )
          }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = TerminalDarkSurface,
          titleContentColor = TerminalCyan
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = TerminalDarkSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("bottom_nav_bar")
      ) {
        AppTab.values().forEachIndexed { index, tab ->
          val isSelected = selectedTabIndex == index

          NavigationBarItem(
            selected = isSelected,
            onClick = { selectedTabIndex = index },
            modifier = Modifier.testTag(tab.testTag),
            icon = {
              when (tab) {
                AppTab.TERMINAL -> Icon(
                  Icons.Default.Terminal,
                  contentDescription = tab.title
                )
                AppTab.CONTROL -> Icon(
                  Icons.Default.Tune,
                  contentDescription = tab.title
                )
                AppTab.TELEMETRY -> Icon(
                  Icons.Default.ShowChart,
                  contentDescription = tab.title
                )
                AppTab.LOGS -> {
                  if (dbRecentLogs.isNotEmpty()) {
                    BadgedBox(
                      badge = {
                        Badge(containerColor = TerminalMint) {
                          Text("${dbRecentLogs.size.coerceAtMost(99)}")
                        }
                      }
                    ) {
                      Icon(Icons.Default.Storage, contentDescription = tab.title)
                    }
                  } else {
                    Icon(Icons.Default.Storage, contentDescription = tab.title)
                  }
                }
              }
            },
            label = {
              Text(
                text = tab.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = TerminalCyan,
              selectedTextColor = TerminalCyan,
              indicatorColor = TerminalCyan.copy(alpha = 0.15f),
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(TerminalDarkBg)
    ) {
      Crossfade(targetState = selectedTabIndex, label = "TabCrossfade") { tabIdx ->
        when (AppTab.values().getOrElse(tabIdx) { AppTab.TERMINAL }) {
          AppTab.TERMINAL -> TerminalScreen(
            viewModel = viewModel,
            onOpenScanDialog = { showScanDialog = true }
          )
          AppTab.CONTROL -> ControlScreen(viewModel = viewModel)
          AppTab.TELEMETRY -> TelemetryScreen(viewModel = viewModel)
          AppTab.LOGS -> LogsScreen(viewModel = viewModel)
        }
      }

      if (showScanDialog) {
        DeviceScanDialog(
          viewModel = viewModel,
          onDismiss = { showScanDialog = false }
        )
      }
    }
  }
}
