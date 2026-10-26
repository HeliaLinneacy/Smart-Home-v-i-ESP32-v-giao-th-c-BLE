package com.example.ui.screens

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CustomButton
import com.example.data.local.DeviceSwitch
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDarkBg
import com.example.ui.theme.TerminalDarkCard
import com.example.ui.theme.TerminalMint
import com.example.ui.theme.TerminalRed
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val buttons by viewModel.customButtons.collectAsStateWithLifecycle()
  val deviceSwitches by viewModel.deviceSwitches.collectAsStateWithLifecycle()

  val pwmValue by viewModel.pwmValue.collectAsStateWithLifecycle()
  val servoValue by viewModel.servoValue.collectAsStateWithLifecycle()

  val isRepeating by viewModel.isRepeating.collectAsStateWithLifecycle()
  val repeatCommand by viewModel.repeatCommand.collectAsStateWithLifecycle()
  val repeatInterval by viewModel.repeatIntervalMs.collectAsStateWithLifecycle()

  var showAddMacroDialog by remember { mutableStateOf(false) }
  var showAddSwitchDialog by remember { mutableStateOf(false) }
  var macroToDelete by remember { mutableStateOf<CustomButton?>(null) }
  var switchToDelete by remember { mutableStateOf<DeviceSwitch?>(null) }

  fun vibrate() {
    try {
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(android.os.VibratorManager::class.java)
        vibratorManager?.defaultVibrator
      } else {
        context.getSystemService(Vibrator::class.java)
      }
      if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(30)
        }
      }
    } catch (_: Throwable) {
      // Ignored safely to prevent crashes on emulators without vibrator
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(TerminalDarkBg)
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Text(
          text = "Bảng Điều Khiển Từ Xa",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = "Điều khiển Relay, công tắc thiết bị số, PWM và Robot",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // =========================================================================
    // SECTION 1: CÔNG TẮC BẬT/TẮT THIẾT BỊ SỐ & RELAY (SWITCHES)
    // =========================================================================
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.ToggleOn,
              contentDescription = null,
              tint = TerminalMint,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
              Text(
                text = "Công Tắc Relay & Thiết Bị Số",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TerminalMint
              )
              Text(
                text = "Bấm trực tiếp để chuyển BẬT / TẮT",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          FilledTonalButton(
            onClick = { showAddSwitchDialog = true },
            modifier = Modifier.testTag("add_switch_device_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Thêm", fontSize = 12.sp)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (deviceSwitches.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Chưa có công tắc nào. Bấm 'Thêm' để tạo công tắc thiết bị mới.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          // Dynamic List of Switch Cards
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            deviceSwitches.forEach { sw ->
              val accentColor = try {
                Color(android.graphics.Color.parseColor(sw.colorHex))
              } catch (_: Exception) {
                TerminalMint
              }

              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (sw.isOn) {
                    accentColor.copy(alpha = 0.15f)
                  } else {
                    MaterialTheme.colorScheme.surface
                  }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .border(
                    width = if (sw.isOn) 1.5.dp else 1.dp,
                    color = if (sw.isOn) accentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                  )
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  // Left info row: clickable to toggle
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .weight(1f)
                      .clickable {
                        vibrate()
                        viewModel.setDeviceSwitchState(sw, !sw.isOn)
                      }
                  ) {
                    Box(
                      modifier = Modifier
                        .size(38.dp)
                        .background(
                          color = if (sw.isOn) accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                          shape = RoundedCornerShape(10.dp)
                        ),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = when (sw.iconName) {
                          "lightbulb" -> Icons.Default.Lightbulb
                          "water_drop" -> Icons.Default.WaterDrop
                          "volume_up" -> Icons.AutoMirrored.Filled.VolumeUp
                          "power" -> Icons.Default.Power
                          "bolt" -> Icons.Default.Bolt
                          else -> Icons.Default.PowerSettingsNew
                        },
                        contentDescription = null,
                        tint = if (sw.isOn) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                      )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                      Text(
                        text = sw.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                          modifier = Modifier
                            .size(6.dp)
                            .background(if (sw.isOn) accentColor else Color(0xFF64748B), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = if (sw.isOn) "Đang BẬT (ON)" else "Đang TẮT (OFF)",
                          fontSize = 11.sp,
                          fontWeight = if (sw.isOn) FontWeight.SemiBold else FontWeight.Normal,
                          color = if (sw.isOn) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    // Switch toggle
                    Switch(
                      checked = sw.isOn,
                      onCheckedChange = { newState ->
                        vibrate()
                        viewModel.setDeviceSwitchState(sw, newState)
                      },
                      colors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.35f)
                      ),
                      modifier = Modifier.testTag("switch_${sw.id}")
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Delete button
                    IconButton(
                      onClick = { switchToDelete = sw },
                      modifier = Modifier.size(32.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xoá công tắc",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // =========================================================================
    // SECTION 2: MACRO BUTTONS (PHÍM TẮT TIỆN ÍCH)
    // =========================================================================
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Lệnh Thao Tác Nhanh (Macros)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = TerminalCyan
            )
            Text(
              text = "Bấm để gửi lệnh nhanh một chạm",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          FilledTonalButton(
            onClick = { showAddMacroDialog = true },
            modifier = Modifier.testTag("add_macro_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Thêm", fontSize = 12.sp)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          buttons.forEach { btn ->
            val color = try {
              Color(android.graphics.Color.parseColor(btn.colorHex))
            } catch (_: Exception) {
              TerminalCyan
            }

            Box(
              modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable {
                  vibrate()
                  viewModel.sendMacro(btn)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = when (btn.iconName) {
                    "lightbulb" -> Icons.Default.Lightbulb
                    "info" -> Icons.Default.Info
                    "sensors" -> Icons.Default.Sensors
                    "volume_up" -> Icons.AutoMirrored.Filled.VolumeUp
                    "restart_alt" -> Icons.Default.RestartAlt
                    else -> Icons.Default.Bolt
                  },
                  contentDescription = null,
                  tint = color,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = btn.label,
                  color = color,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = { macroToDelete = btn },
                  modifier = Modifier.size(18.dp)
                ) {
                  Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    // =========================================================================
    // SECTION 3: ANALOG PWM & SERVO SLIDERS
    // =========================================================================
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = "Điều Khiển Tương Tự (PWM & Servo)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = TerminalAmber
        )

        Spacer(modifier = Modifier.height(10.dp))

        // PWM Slider
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Độ sáng LED / Tốc độ Motor (PWM)", fontSize = 13.sp)
          Text("${pwmValue.toInt()} / 255", fontWeight = FontWeight.Bold, color = TerminalAmber)
        }
        Slider(
          value = pwmValue,
          onValueChange = { viewModel.updatePwm(it) },
          onValueChangeFinished = {
            vibrate()
            viewModel.commitPwm()
          },
          valueRange = 0f..255f,
          colors = SliderDefaults.colors(
            thumbColor = TerminalAmber,
            activeTrackColor = TerminalAmber
          )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Servo Slider
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Góc quay động cơ Servo", fontSize = 13.sp)
          Text("${servoValue.toInt()}°", fontWeight = FontWeight.Bold, color = TerminalCyan)
        }
        Slider(
          value = servoValue,
          onValueChange = { viewModel.updateServo(it) },
          onValueChangeFinished = {
            vibrate()
            viewModel.commitServo()
          },
          valueRange = 0f..180f,
          colors = SliderDefaults.colors(
            thumbColor = TerminalCyan,
            activeTrackColor = TerminalCyan
          )
        )
      }
    }

    // =========================================================================
    // SECTION 4: ROBOT / MOTOR D-PAD CONTROLLER
    // =========================================================================
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Bảng Điều Hướng Robot / Xe ESP32",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = TerminalCyan
        )
        Text(
          text = "Gửi các lệnh: CMD:FWD, CMD:BACK, CMD:LEFT, CMD:RIGHT, CMD:STOP",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // D-Pad Grid
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // UP
          IconButton(
            onClick = {
              vibrate()
              viewModel.sendRobotCommand("FWD")
            },
            modifier = Modifier
              .size(54.dp)
              .background(TerminalCyan.copy(alpha = 0.2f), CircleShape)
              .border(1.dp, TerminalCyan, CircleShape)
          ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Forward", tint = TerminalCyan)
          }

          // LEFT - STOP - RIGHT
          Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = {
                vibrate()
                viewModel.sendRobotCommand("LEFT")
              },
              modifier = Modifier
                .size(54.dp)
                .background(TerminalCyan.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, TerminalCyan, CircleShape)
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left", tint = TerminalCyan)
            }

            IconButton(
              onClick = {
                vibrate()
                viewModel.sendRobotCommand("STOP")
              },
              modifier = Modifier
                .size(54.dp)
                .background(TerminalRed.copy(alpha = 0.25f), CircleShape)
                .border(1.dp, TerminalRed, CircleShape)
            ) {
              Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TerminalRed)
            }

            IconButton(
              onClick = {
                vibrate()
                viewModel.sendRobotCommand("RIGHT")
              },
              modifier = Modifier
                .size(54.dp)
                .background(TerminalCyan.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, TerminalCyan, CircleShape)
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right", tint = TerminalCyan)
            }
          }

          // DOWN
          IconButton(
            onClick = {
              vibrate()
              viewModel.sendRobotCommand("BACK")
            },
            modifier = Modifier
              .size(54.dp)
              .background(TerminalCyan.copy(alpha = 0.2f), CircleShape)
              .border(1.dp, TerminalCyan, CircleShape)
          ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "Backward", tint = TerminalCyan)
          }
        }
      }
    }

    // =========================================================================
    // SECTION 5: PERIODIC COMMAND SENDER
    // =========================================================================
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Loop, contentDescription = null, tint = TerminalMint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Lệnh Lặp Định Kỳ (Auto Polling)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }

          Switch(
            checked = isRepeating,
            onCheckedChange = { viewModel.togglePeriodicRepeat() },
            colors = SwitchDefaults.colors(checkedThumbColor = TerminalMint)
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = repeatCommand,
            onValueChange = { viewModel.repeatCommand.value = it },
            label = { Text("Lệnh lặp", fontSize = 11.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true
          )

          Column(modifier = Modifier.width(110.dp)) {
            Text("Chu kỳ: ${repeatInterval}ms", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              listOf(1000L, 2000L, 5000L).forEach { interval ->
                val selected = repeatInterval == interval
                Text(
                  text = "${interval / 1000}s",
                  fontSize = 11.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                  color = if (selected) TerminalMint else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier
                    .background(
                      if (selected) TerminalMint.copy(alpha = 0.2f) else Color.Transparent,
                      RoundedCornerShape(4.dp)
                    )
                    .clickable { viewModel.repeatIntervalMs.value = interval }
                    .padding(4.dp)
                )
              }
            }
          }
        }
      }
    }
  }

  // ===========================================================================
  // DIALOG: THÊM CÔNG TẮC THIẾT BỊ MỚI
  // ===========================================================================
  if (showAddSwitchDialog) {
    var switchName by remember { mutableStateOf("") }
    var onCommand by remember { mutableStateOf("") }
    var offCommand by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("toggle") }
    var selectedColor by remember { mutableStateOf("#00E676") }

    AlertDialog(
      onDismissRequest = { showAddSwitchDialog = false },
      title = { Text("Thêm Công Tắc Thiết Bị Mới") },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
          OutlinedTextField(
            value = switchName,
            onValueChange = { switchName = it },
            label = { Text("Tên thiết bị") },
            placeholder = { Text("Ví dụ: Relay 3, Máy Bơm, Đèn Sân") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = onCommand,
            onValueChange = { onCommand = it },
            label = { Text("Lệnh khi BẬT (ON)") },
            placeholder = { Text("Ví dụ: RELAY:3:ON hoặc PUMP:1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = offCommand,
            onValueChange = { offCommand = it },
            label = { Text("Lệnh khi TẮT (OFF)") },
            placeholder = { Text("Ví dụ: RELAY:3:OFF hoặc PUMP:0") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Text("Biểu tượng (Icon):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
          ) {
            listOf(
              "toggle" to Icons.Default.ToggleOn,
              "lightbulb" to Icons.Default.Lightbulb,
              "water_drop" to Icons.Default.WaterDrop,
              "volume_up" to Icons.AutoMirrored.Filled.VolumeUp,
              "power" to Icons.Default.Power,
              "bolt" to Icons.Default.Bolt
            ).forEach { (iconKey, vector) ->
              val isSelected = selectedIcon == iconKey
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .background(
                    if (isSelected) TerminalMint.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                  )
                  .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) TerminalMint else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                  )
                  .clickable { selectedIcon = iconKey },
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = vector,
                  contentDescription = null,
                  tint = if (isSelected) TerminalMint else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(22.dp)
                )
              }
            }
          }

          Text("Màu sắc nhận diện:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
          ) {
            listOf(
              "#00E676", "#00E5FF", "#10B981", "#3B82F6", "#F59E0B", "#FF9100", "#EC4899", "#EF4444"
            ).forEach { hex ->
              val c = Color(android.graphics.Color.parseColor(hex))
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .background(c, CircleShape)
                  .border(
                    width = if (selectedColor == hex) 3.dp else 1.dp,
                    color = if (selectedColor == hex) Color.White else Color.Transparent,
                    shape = CircleShape
                  )
                  .clickable { selectedColor = hex }
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (switchName.isNotBlank() && onCommand.isNotBlank() && offCommand.isNotBlank()) {
              viewModel.addDeviceSwitch(
                name = switchName.trim(),
                onCommand = onCommand.trim(),
                offCommand = offCommand.trim(),
                iconName = selectedIcon,
                colorHex = selectedColor
              )
              showAddSwitchDialog = false
            }
          }
        ) {
          Text("Lưu công tắc")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddSwitchDialog = false }) {
          Text("Huỷ")
        }
      }
    )
  }

  // ===========================================================================
  // DIALOG: XOÁ CÔNG TẮC THIẾT BỊ
  // ===========================================================================
  switchToDelete?.let { sw ->
    AlertDialog(
      onDismissRequest = { switchToDelete = null },
      title = { Text("Xoá công tắc?") },
      text = { Text("Bạn có chắc chắn muốn xoá công tắc '${sw.name}' không?") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteDeviceSwitch(sw)
            switchToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = TerminalRed)
        ) {
          Text("Xoá")
        }
      },
      dismissButton = {
        TextButton(onClick = { switchToDelete = null }) {
          Text("Huỷ")
        }
      }
    )
  }

  // ===========================================================================
  // DIALOG: THÊM NÚT MACRO
  // ===========================================================================
  if (showAddMacroDialog) {
    var labelInput by remember { mutableStateOf("") }
    var commandInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#00E5FF") }
    var isHex by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showAddMacroDialog = false },
      title = { Text("Thêm phím tắt Macro") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = labelInput,
            onValueChange = { labelInput = it },
            label = { Text("Tên nút hiển thị") },
            placeholder = { Text("Ví dụ: STATUS, GET DATA") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = commandInput,
            onValueChange = { commandInput = it },
            label = { Text("Lệnh gửi tới ESP32") },
            placeholder = { Text("Ví dụ: STATUS hoặc PING") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Text("Màu sắc nút:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
          ) {
            listOf(
              "#00E5FF", "#00E676", "#FF9100", "#FF5252", "#D500F9", "#3B82F6", "#F59E0B", "#10B981"
            ).forEach { hex ->
              val c = Color(android.graphics.Color.parseColor(hex))
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .background(c, CircleShape)
                  .border(
                    width = if (selectedColor == hex) 3.dp else 1.dp,
                    color = if (selectedColor == hex) Color.White else Color.Transparent,
                    shape = CircleShape
                  )
                  .clickable { selectedColor = hex }
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (labelInput.isNotBlank() && commandInput.isNotBlank()) {
              viewModel.addCustomButton(labelInput.trim(), commandInput.trim() + "\n", selectedColor, isHex)
              showAddMacroDialog = false
            }
          }
        ) {
          Text("Lưu nút")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddMacroDialog = false }) {
          Text("Huỷ")
        }
      }
    )
  }

  // ===========================================================================
  // DIALOG: XOÁ NÚT MACRO
  // ===========================================================================
  macroToDelete?.let { btn ->
    AlertDialog(
      onDismissRequest = { macroToDelete = null },
      title = { Text("Xoá nút Macro?") },
      text = { Text("Bạn có chắc muốn xoá nút '${btn.label}' không?") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteCustomButton(btn)
            macroToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = TerminalRed)
        ) {
          Text("Xoá")
        }
      },
      dismissButton = {
        TextButton(onClick = { macroToDelete = null }) {
          Text("Huỷ")
        }
      }
    )
  }
}
