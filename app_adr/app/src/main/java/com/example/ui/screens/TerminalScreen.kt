package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ble.ConnectionStatus
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDarkBg
import com.example.ui.theme.TerminalMint
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextRxGreen
import com.example.ui.theme.TextSysBlue
import com.example.ui.theme.TextTxAmber
import com.example.viewmodel.MainViewModel

@Composable
fun TerminalScreen(
  viewModel: MainViewModel,
  onOpenScanDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
  val deviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
  val deviceAddress by viewModel.connectedDeviceAddress.collectAsStateWithLifecycle()
  val mtu by viewModel.mtuSize.collectAsStateWithLifecycle()

  val items by viewModel.filteredTerminalItems.collectAsStateWithLifecycle()
  val autoScroll by viewModel.autoScroll.collectAsStateWithLifecycle()
  val displayHex by viewModel.displayHex.collectAsStateWithLifecycle()
  val showTimestamps by viewModel.showTimestamps.collectAsStateWithLifecycle()
  val filterDirection by viewModel.filterDirection.collectAsStateWithLifecycle()
  val filterQuery by viewModel.filterQuery.collectAsStateWithLifecycle()
  val newlineSetting by viewModel.newlineSetting.collectAsStateWithLifecycle()
  val isHexInput by viewModel.isHexInput.collectAsStateWithLifecycle()
  val inputText by viewModel.inputText.collectAsStateWithLifecycle()
  val history by viewModel.commandHistory.collectAsStateWithLifecycle()
  val autoSaveToDb by viewModel.autoSaveToDb.collectAsStateWithLifecycle()
  val onlyOnUserCommand by viewModel.onlyOnUserCommand.collectAsStateWithLifecycle()

  val listState = rememberLazyListState()
  var showHistoryDropdown by remember { mutableStateOf(false) }
  var showSearchRow by remember { mutableStateOf(false) }

  // Auto-scroll on new message
  LaunchedEffect(items.size, autoScroll) {
    if (autoScroll && items.isNotEmpty()) {
      listState.animateScrollToItem(items.size - 1)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(TerminalDarkBg)
  ) {
    // Top Connection & Status Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .weight(1f)
            .clickable { onOpenScanDialog() }
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
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
          Column {
            Text(
              text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                deviceName ?: "ESP32 Device"
              } else {
                when (connectionStatus) {
                  ConnectionStatus.SCANNING -> "Đang quét BLE..."
                  ConnectionStatus.CONNECTING -> "Đang kết nối..."
                  ConnectionStatus.DISCOVERING_SERVICES -> "Đang tìm dịch vụ..."
                  ConnectionStatus.DISCONNECTED -> "Chưa kết nối ESP32"
                  ConnectionStatus.CONNECTED -> "Đã kết nối"
                }
              },
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1
            )
            if (connectionStatus == ConnectionStatus.CONNECTED) {
              Text(
                text = "${deviceAddress ?: ""} | MTU: $mtu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              Text(
                text = "Chạm để quét và kết nối",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { onOpenScanDialog() },
            modifier = Modifier.testTag("scan_button")
          ) {
            Icon(
              imageVector = if (connectionStatus == ConnectionStatus.CONNECTED) {
                Icons.Default.BluetoothConnected
              } else {
                Icons.Default.Bluetooth
              },
              contentDescription = "Bluetooth",
              tint = if (connectionStatus == ConnectionStatus.CONNECTED) TerminalMint else MaterialTheme.colorScheme.primary
            )
          }

          IconButton(
            onClick = { showSearchRow = !showSearchRow },
            modifier = Modifier.testTag("search_toggle_button")
          ) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search",
              tint = if (filterQuery.isNotEmpty()) TerminalCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          IconButton(
            onClick = {
              val logText = viewModel.exportTerminalLogsAsText()
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, logText)
                type = "text/plain"
              }
              val shareIntent = Intent.createChooser(sendIntent, "Xuất dữ liệu Terminal")
              context.startActivity(shareIntent)
            },
            modifier = Modifier.testTag("export_button")
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = "Export Logs",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    // Search bar expandable
    AnimatedVisibility(visible = showSearchRow) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = filterQuery,
          onValueChange = { viewModel.filterQuery.value = it },
          placeholder = { Text("Lọc nội dung hoặc Hex...", fontSize = 13.sp) },
          modifier = Modifier
            .weight(1f)
            .height(48.dp),
          singleLine = true,
          trailingIcon = {
            if (filterQuery.isNotEmpty()) {
              IconButton(onClick = { viewModel.filterQuery.value = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
              }
            }
          }
        )
      }
    }

    // Terminal Settings Chips Bar (Direction, Hex, Auto-scroll, Clear)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Direction Filter Chips
      listOf("ALL" to "Tất cả", "RX" to "Nhận (RX)", "TX" to "Gửi (TX)").forEach { (dir, label) ->
        FilterChip(
          selected = filterDirection == dir,
          onClick = { viewModel.filterDirection.value = dir },
          label = { Text(label, fontSize = 11.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = when (dir) {
              "RX" -> TerminalMint.copy(alpha = 0.25f)
              "TX" -> TerminalAmber.copy(alpha = 0.25f)
              else -> TerminalCyan.copy(alpha = 0.25f)
            },
            selectedLabelColor = when (dir) {
              "RX" -> TerminalMint
              "TX" -> TerminalAmber
              else -> TerminalCyan
            }
          )
        )
      }

      // Hex toggle
      FilterChip(
        selected = displayHex,
        onClick = { viewModel.displayHex.value = !displayHex },
        label = { Text("HEX", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = TerminalCyan.copy(alpha = 0.25f),
          selectedLabelColor = TerminalCyan
        )
      )

      // Auto-scroll toggle
      FilterChip(
        selected = autoScroll,
        onClick = { viewModel.autoScroll.value = !autoScroll },
        label = { Text("Cuộn tự động", fontSize = 11.sp) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
        }
      )

      // Only log/show on user command toggle
      FilterChip(
        selected = onlyOnUserCommand,
        onClick = { viewModel.onlyOnUserCommand.value = !onlyOnUserCommand },
        label = { Text(if (onlyOnUserCommand) "Chỉ hiện theo lệnh" else "Hiện tất cả", fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = TerminalCyan.copy(alpha = 0.25f),
          selectedLabelColor = TerminalCyan
        )
      )

      // Room DB auto-save indicator
      AssistChip(
        onClick = { viewModel.autoSaveToDb.value = !autoSaveToDb },
        label = { Text(if (autoSaveToDb) "Lưu DB: BẬT" else "Lưu DB: TẮT", fontSize = 11.sp) },
        leadingIcon = {
          Icon(
            Icons.Default.Storage,
            contentDescription = null,
            tint = if (autoSaveToDb) TerminalMint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
        },
        colors = AssistChipDefaults.assistChipColors(
          labelColor = if (autoSaveToDb) TerminalMint else MaterialTheme.colorScheme.onSurfaceVariant
        )
      )

      // Clear button
      AssistChip(
        onClick = { viewModel.clearTerminal() },
        label = { Text("Xoá màn hình", fontSize = 11.sp) },
        leadingIcon = {
          Icon(
            Icons.Default.Clear,
            contentDescription = null,
            tint = TerminalRed,
            modifier = Modifier.size(14.dp)
          )
        }
      )
    }

    // Terminal Log Output Window
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(TerminalDarkBg)
        .padding(horizontal = 8.dp)
    ) {
      if (items.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Màn hình Terminal",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Chưa có dữ liệu. Kết nối ESP32 hoặc gửi lệnh để xem dữ liệu.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      } else {
        SelectionContainer {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
          ) {
            items(items, key = { it.id }) { item ->
              TerminalRow(
                item = item,
                displayHex = displayHex,
                showTimestamps = showTimestamps,
                onCopy = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Log", it))
                  Toast.makeText(context, "Đã sao chép vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
                }
              )
            }
          }
        }
      }
    }

    // Bottom Send Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp)
      ) {
        // Quick Newline / Hex Format Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            listOf(
              "\r\n" to "CR+LF",
              "\n" to "LF",
              "\r" to "CR",
              "" to "None"
            ).forEach { (ending, label) ->
              val isSelected = newlineSetting == ending
              Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TerminalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .background(
                    color = if (isSelected) TerminalCyan.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                  )
                  .clickable { viewModel.newlineSetting.value = ending }
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = if (isHexInput) "Chế độ: HEX" else "Chế độ: ASCII",
              fontSize = 10.sp,
              color = if (isHexInput) TerminalAmber else TerminalMint,
              modifier = Modifier
                .clickable { viewModel.isHexInput.value = !isHexInput }
                .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Box {
              IconButton(
                onClick = { showHistoryDropdown = true },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(
                  Icons.Default.History,
                  contentDescription = "History",
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              DropdownMenu(
                expanded = showHistoryDropdown,
                onDismissRequest = { showHistoryDropdown = false }
              ) {
                if (history.isEmpty()) {
                  DropdownMenuItem(
                    text = { Text("Chưa có lịch sử") },
                    onClick = { showHistoryDropdown = false }
                  )
                } else {
                  history.forEach { cmd ->
                    DropdownMenuItem(
                      text = { Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                      onClick = {
                        viewModel.inputText.value = cmd
                        showHistoryDropdown = false
                      }
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Input Field and Send Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = inputText,
            onValueChange = { viewModel.inputText.value = it },
            placeholder = {
              Text(
                if (isHexInput) "Nhập mã hex: 0x01 AA FF..." else "Nhập lệnh gửi ESP32...",
                fontSize = 13.sp
              )
            },
            modifier = Modifier
              .weight(1f)
              .testTag("terminal_input_field"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = TerminalCyan,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.sendCurrentInput() })
          )

          Spacer(modifier = Modifier.width(8.dp))

          Button(
            onClick = { viewModel.sendCurrentInput() },
            modifier = Modifier
              .size(50.dp)
              .testTag("terminal_send_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TerminalCyan),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Send,
              contentDescription = "Gửi lệnh",
              tint = Color(0xFF00363F)
            )
          }
        }
      }
    }
  }
}

@Composable
fun TerminalRow(
  item: com.example.viewmodel.TerminalDisplayItem,
  displayHex: Boolean,
  showTimestamps: Boolean,
  onCopy: (String) -> Unit
) {
  val (color, prefix) = when (item.direction) {
    "RX" -> TextRxGreen to "◀ RX"
    "TX" -> TextTxAmber to "▶ TX"
    else -> TextSysBlue to "● SYS"
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    shape = RoundedCornerShape(2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable {
        val copyText = if (displayHex && item.hex.isNotEmpty()) item.hex else item.text
        onCopy(copyText)
      }
      .padding(vertical = 1.dp)
  ) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (showTimestamps) {
          Text(
            text = item.formattedTime,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
          )
          Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
          text = prefix,
          color = color,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
      }

      Text(
        text = item.text.trimEnd(),
        color = when (item.direction) {
          "RX" -> Color(0xFFE2E8F0)
          "TX" -> Color(0xFFFDE68A)
          else -> Color(0xFF93C5FD)
        },
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 4.dp, top = 1.dp)
      )

      if (displayHex && item.hex.isNotEmpty()) {
        Text(
          text = item.hex,
          color = Color(0xFF94A3B8),
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(start = 4.dp, top = 1.dp)
        )
      }
    }
  }
}
