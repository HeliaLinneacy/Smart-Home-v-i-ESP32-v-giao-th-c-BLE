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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LogEntry
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDarkBg
import com.example.ui.theme.TerminalDarkCard
import com.example.ui.theme.TerminalMint
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextRxGreen
import com.example.ui.theme.TextSysBlue
import com.example.ui.theme.TextTxAmber
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val dbLogs by viewModel.dbRecentLogs.collectAsStateWithLifecycle()
  val sessions by viewModel.sessionSummaries.collectAsStateWithLifecycle()

  var selectedSession by remember { mutableStateOf<String?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var filterDirection by remember { mutableStateOf("ALL") }
  var showConfirmClearDialog by remember { mutableStateOf(false) }
  var showSessionDropdown by remember { mutableStateOf(false) }

  val filteredLogs = remember(dbLogs, selectedSession, searchQuery, filterDirection) {
    dbLogs.filter { log ->
      val sessionMatch = selectedSession == null || log.sessionId == selectedSession
      val dirMatch = when (filterDirection) {
        "RX" -> log.direction == "RX"
        "TX" -> log.direction == "TX"
        else -> true
      }
      val queryMatch = searchQuery.isEmpty() ||
        log.message.contains(searchQuery, ignoreCase = true) ||
        log.hexData.contains(searchQuery, ignoreCase = true) ||
        log.deviceName.contains(searchQuery, ignoreCase = true)

      sessionMatch && dirMatch && queryMatch
    }
  }

  val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(TerminalDarkBg)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Cơ Sở Dữ Liệu Nhật Ký",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = "Lưu trữ SQLite cục bộ với Room Database",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Export CSV button
        IconButton(
          onClick = {
            if (filteredLogs.isEmpty()) {
              Toast.makeText(context, "Không có nhật ký nào để xuất", Toast.LENGTH_SHORT).show()
              return@IconButton
            }
            val csv = viewModel.exportDatabaseLogsCsv(filteredLogs)
            val sendIntent = Intent().apply {
              action = Intent.ACTION_SEND
              putExtra(Intent.EXTRA_TEXT, csv)
              type = "text/csv"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ tệp CSV Nhật ký")
            context.startActivity(shareIntent)
          },
          modifier = Modifier.testTag("export_csv_button")
        ) {
          Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = TerminalCyan)
        }

        // Clear DB button
        IconButton(
          onClick = { showConfirmClearDialog = true },
          modifier = Modifier.testTag("clear_db_button")
        ) {
          Icon(Icons.Default.DeleteSweep, contentDescription = "Clear DB", tint = TerminalRed)
        }
      }
    }

    // Database Metrics Banner
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .background(TerminalMint.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = TerminalMint)
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Tổng số bản ghi: ${dbLogs.size}",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
            Text(
              text = "Phiên hoạt động: ${sessions.size} phiên ghi",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Session Selector Button
        Box {
          OutlinedButton(
            onClick = { showSessionDropdown = true },
            shape = RoundedCornerShape(10.dp)
          ) {
            Text(
              text = if (selectedSession == null) "Tất cả phiên" else "Phiên: $selectedSession",
              fontSize = 11.sp,
              maxLines = 1
            )
          }

          DropdownMenu(
            expanded = showSessionDropdown,
            onDismissRequest = { showSessionDropdown = false }
          ) {
            DropdownMenuItem(
              text = { Text("Tất cả phiên") },
              onClick = {
                selectedSession = null
                showSessionDropdown = false
              }
            )
            sessions.forEach { s ->
              DropdownMenuItem(
                text = { Text("${s.sessionId} (${s.entryCount} bản ghi)") },
                onClick = {
                  selectedSession = s.sessionId
                  showSessionDropdown = false
                }
              )
            }
          }
        }
      }
    }

    // Search and Filter Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Tìm kiếm nội dung nhật ký...", fontSize = 12.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
            }
          }
        },
        modifier = Modifier.weight(1f),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
      )

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("ALL" to "Tất cả", "RX" to "RX", "TX" to "TX").forEach { (dir, label) ->
          FilterChip(
            selected = filterDirection == dir,
            onClick = { filterDirection = dir },
            label = { Text(label, fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = when (dir) {
                "RX" -> TerminalMint.copy(alpha = 0.25f)
                "TX" -> TerminalAmber.copy(alpha = 0.25f)
                else -> TerminalCyan.copy(alpha = 0.25f)
              }
            )
          )
        }
      }
    }

    // Log list
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      if (filteredLogs.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "Không có nhật ký nào phù hợp",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Dữ liệu BLE gửi/nhận sẽ tự động được ghi lại tại đây",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredLogs, key = { it.id }) { log ->
            LogItemCard(
              log = log,
              dateFormatter = dateFormatter,
              onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Log entry", log.message))
                Toast.makeText(context, "Đã sao chép vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
              }
            )
          }
        }
      }
    }
  }

  // Clear DB Confirmation Dialog
  if (showConfirmClearDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmClearDialog = false },
      title = { Text("Xoá toàn bộ cơ sở dữ liệu?") },
      text = { Text("Hành động này sẽ xoá vĩnh viễn tất cả nhật ký BLE đã thu thập trong cơ sở dữ liệu Room.") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearDatabase()
            showConfirmClearDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = TerminalRed)
        ) {
          Text("Xoá tất cả")
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmClearDialog = false }) {
          Text("Huỷ")
        }
      }
    )
  }
}

@Composable
fun LogItemCard(
  log: LogEntry,
  dateFormatter: SimpleDateFormat,
  onCopy: () -> Unit
) {
  val (color, prefix) = when (log.direction) {
    "RX" -> TextRxGreen to "RX"
    "TX" -> TextTxAmber to "TX"
    else -> TextSysBlue to "SYS"
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCopy() }
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = prefix,
              color = color,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = log.deviceName.ifBlank { "ESP32" },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Text(
          text = dateFormatter.format(Date(log.timestamp)),
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontFamily = FontFamily.Monospace
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = log.message.trimEnd(),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFFF1F5F9)
      )

      if (log.hexData.isNotEmpty()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "HEX: ${log.hexData}",
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          color = Color(0xFF94A3B8)
        )
      }
    }
  }
}
