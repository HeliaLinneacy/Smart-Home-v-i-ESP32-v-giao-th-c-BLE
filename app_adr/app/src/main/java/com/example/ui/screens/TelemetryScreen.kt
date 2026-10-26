package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MetricPoint
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDarkBg
import com.example.ui.theme.TerminalDarkCard
import com.example.ui.theme.TerminalMint
import com.example.ui.theme.TerminalRed
import com.example.viewmodel.MainViewModel

@Composable
fun TelemetryScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val latestTelemetry by viewModel.latestTelemetry.collectAsStateWithLifecycle()
  val tempHistory by viewModel.temperatureHistory.collectAsStateWithLifecycle()
  val humHistory by viewModel.humidityHistory.collectAsStateWithLifecycle()
  val voltHistory by viewModel.voltageHistory.collectAsStateWithLifecycle()
  val telemetryList by viewModel.telemetryList.collectAsStateWithLifecycle()

  var selectedMetricTab by remember { mutableStateOf("ALL") } // "ALL", "TEMP", "HUM", "VOLT"

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
          text = "Thu Thập Dữ Liệu Cảm Biến",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = "Giải mã luồng dữ liệu thời gian thực từ ESP32",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (latestTelemetry != null) {
        Box(
          modifier = Modifier
            .background(TerminalMint.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "Cập nhật: ${latestTelemetry?.formattedTime}",
            fontSize = 11.sp,
            color = TerminalMint,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // KPI Cards Grid (Temperature, Humidity, Voltage, Light)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Temperature Card
      KpiMetricCard(
        title = "Nhiệt độ",
        value = latestTelemetry?.temperature?.let { "%.1f°C".format(it) } ?: "-- °C",
        icon = Icons.Default.Thermostat,
        accentColor = TerminalAmber,
        modifier = Modifier.weight(1f)
      )

      // Humidity Card
      KpiMetricCard(
        title = "Độ ẩm",
        value = latestTelemetry?.humidity?.let { "%.1f%%".format(it) } ?: "-- %",
        icon = Icons.Default.WaterDrop,
        accentColor = TerminalCyan,
        modifier = Modifier.weight(1f)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Voltage Card
      KpiMetricCard(
        title = "Điện áp",
        value = latestTelemetry?.voltage?.let { "%.2f V".format(it) } ?: "-- V",
        icon = Icons.Default.ElectricBolt,
        accentColor = TerminalMint,
        modifier = Modifier.weight(1f)
      )

      // Light Card
      KpiMetricCard(
        title = "Ánh sáng",
        value = latestTelemetry?.light?.let { "%.0f Lux".format(it) } ?: "Bình thường",
        icon = Icons.Default.LightMode,
        accentColor = Color(0xFFFBBF24),
        modifier = Modifier.weight(1f)
      )
    }

    // Chart Filter Chips
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf(
        "ALL" to "Tất cả",
        "TEMP" to "Nhiệt độ (°C)",
        "HUM" to "Độ ẩm (%)",
        "VOLT" to "Điện áp (V)"
      ).forEach { (key, label) ->
        FilterChip(
          selected = selectedMetricTab == key,
          onClick = { selectedMetricTab = key },
          label = { Text(label, fontSize = 11.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = when (key) {
              "TEMP" -> TerminalAmber.copy(alpha = 0.25f)
              "HUM" -> TerminalCyan.copy(alpha = 0.25f)
              "VOLT" -> TerminalMint.copy(alpha = 0.25f)
              else -> Color(0xFF6366F1).copy(alpha = 0.25f)
            },
            selectedLabelColor = when (key) {
              "TEMP" -> TerminalAmber
              "HUM" -> TerminalCyan
              "VOLT" -> TerminalMint
              else -> Color(0xFFA5B4FC)
            }
          )
        )
      }
    }

    // Real-Time Sensor Chart Card
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Đồ Thị Dữ Liệu Thời Gian Thực",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
          )

          // Legend
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedMetricTab == "ALL" || selectedMetricTab == "TEMP") {
              LegendDot(color = TerminalAmber, label = "Nhiệt")
            }
            if (selectedMetricTab == "ALL" || selectedMetricTab == "HUM") {
              LegendDot(color = TerminalCyan, label = "Ẩm")
            }
            if (selectedMetricTab == "ALL" || selectedMetricTab == "VOLT") {
              LegendDot(color = TerminalMint, label = "Điện")
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom Canvas Chart
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          TelemetryCanvasChart(
            selectedMetric = selectedMetricTab,
            tempHistory = tempHistory,
            humHistory = humHistory,
            voltHistory = voltHistory
          )
        }
      }
    }

    // Recent Sensor Packets Stream
    Card(
      colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text(
          text = "Nhật Ký Gói Dữ Liệu Thu Thập Được",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (telemetryList.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Đang chờ gói dữ liệu từ ESP32...",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            items(telemetryList) { item ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                  .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = item.formattedTime,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = item.rawText,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                  color = TerminalCyan,
                  maxLines = 1
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun KpiMetricCard(
  title: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = TerminalDarkCard),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Text(
          text = title,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = value,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = accentColor,
          fontFamily = FontFamily.Monospace
        )
      }

      Box(
        modifier = Modifier
          .size(36.dp)
          .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
      }
    }
  }
}

@Composable
fun LegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .background(color, RoundedCornerShape(2.dp))
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun TelemetryCanvasChart(
  selectedMetric: String,
  tempHistory: List<MetricPoint>,
  humHistory: List<MetricPoint>,
  voltHistory: List<MetricPoint>
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val width = size.width
    val height = size.height

    if (width <= 0 || height <= 0) return@Canvas

    // Draw grid background lines
    val gridColor = Color(0xFF1E293B)
    for (i in 0..4) {
      val y = height * (i / 4f)
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = 1f
      )
    }

    fun drawSeries(points: List<MetricPoint>, color: Color, minVal: Float, maxVal: Float) {
      if (points.size < 2) return
      val range = if (maxVal - minVal == 0f) 1f else (maxVal - minVal)
      val path = Path()

      points.forEachIndexed { index, point ->
        val x = (index.toFloat() / (points.size - 1).coerceAtLeast(1)) * width
        val normalizedY = (point.value - minVal) / range
        val y = height - (normalizedY * (height - 20f) + 10f)

        if (index == 0) {
          path.moveTo(x, y)
        } else {
          path.lineTo(x, y)
        }
      }

      drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
      )

      // Draw end point glowing dot
      val lastPoint = points.last()
      val lastX = width
      val lastNormY = (lastPoint.value - minVal) / range
      val lastY = height - (lastNormY * (height - 20f) + 10f)
      drawCircle(
        color = color,
        radius = 4.dp.toPx(),
        center = Offset(lastX, lastY)
      )
    }

    if ((selectedMetric == "ALL" || selectedMetric == "TEMP") && tempHistory.isNotEmpty()) {
      drawSeries(tempHistory, TerminalAmber, 15f, 45f)
    }
    if ((selectedMetric == "ALL" || selectedMetric == "HUM") && humHistory.isNotEmpty()) {
      drawSeries(humHistory, TerminalCyan, 20f, 100f)
    }
    if ((selectedMetric == "ALL" || selectedMetric == "VOLT") && voltHistory.isNotEmpty()) {
      drawSeries(voltHistory, TerminalMint, 2.8f, 3.8f)
    }
  }
}
