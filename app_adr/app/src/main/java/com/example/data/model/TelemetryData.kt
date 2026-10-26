package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MetricPoint(
  val timestamp: Long,
  val value: Float
)

data class ParsedTelemetry(
  val temperature: Float? = null,
  val humidity: Float? = null,
  val voltage: Float? = null,
  val light: Float? = null,
  val relay1State: Boolean? = null,
  val relay2State: Boolean? = null,
  val ledState: Boolean? = null,
  val customMetrics: Map<String, Float> = emptyMap(),
  val rawText: String = "",
  val timestamp: Long = System.currentTimeMillis()
) {
  val formattedTime: String
    get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

object TelemetryParser {
  // Regexes for common ESP32 telemetry formats
  private val jsonNumberRegex = """"([a-zA-Z0-9_-]+)"\s*:\s*(-?[0-9]+(?:\.[0-9]+)?)""".toRegex()
  private val jsonBooleanRegex = """"([a-zA-Z0-9_-]+)"\s*:\s*(true|false)""".toRegex(RegexOption.IGNORE_CASE)
  private val kvRegex = """([a-zA-Z0-9_-]+)\s*[:=]\s*(-?[0-9]+(?:\.[0-9]+)?)""".toRegex()
  private val kvBoolRegex = """([a-zA-Z0-9_-]+)\s*[:=]\s*(true|false|on|off|1|0)""".toRegex(RegexOption.IGNORE_CASE)

  fun parse(line: String): ParsedTelemetry? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null

    var temp: Float? = null
    var hum: Float? = null
    var volt: Float? = null
    var light: Float? = null
    var r1: Boolean? = null
    var r2: Boolean? = null
    var led: Boolean? = null
    val custom = mutableMapOf<String, Float>()

    // Try parsing numbers (JSON or KV)
    val numberMatches = jsonNumberRegex.findAll(trimmed).toList().ifEmpty {
      kvRegex.findAll(trimmed).toList()
    }

    for (match in numberMatches) {
      val key = match.groupValues[1].lowercase(Locale.ROOT)
      val value = match.groupValues[2].toFloatOrNull() ?: continue

      when {
        key.contains("temp") || key == "t" -> temp = value
        key.contains("hum") || key.contains("rh") || key == "h" -> hum = value
        key.contains("volt") || key.contains("vbat") || key == "v" -> volt = value
        key.contains("lux") || key.contains("light") || key == "l" -> light = value
        else -> custom[key] = value
      }
    }

    // Try parsing boolean switches
    val boolMatches = jsonBooleanRegex.findAll(trimmed).toList().ifEmpty {
      kvBoolRegex.findAll(trimmed).toList()
    }

    for (match in boolMatches) {
      val key = match.groupValues[1].lowercase(Locale.ROOT)
      val boolVal = when (match.groupValues[2].lowercase(Locale.ROOT)) {
        "true", "on", "1" -> true
        "false", "off", "0" -> false
        else -> null
      } ?: continue

      when {
        key.contains("relay1") || key == "r1" -> r1 = boolVal
        key.contains("relay2") || key == "r2" -> r2 = boolVal
        key.contains("led") -> led = boolVal
      }
    }

    // Support numeric 1/0 for relays and led
    if (r1 == null) {
      val v = custom["r1"] ?: custom["relay1"]
      if (v != null) r1 = (v != 0f)
    }
    if (r2 == null) {
      val v = custom["r2"] ?: custom["relay2"]
      if (v != null) r2 = (v != 0f)
    }
    if (led == null) {
      val v = custom["led"]
      if (v != null) led = (v != 0f)
    }

    // Simple CSV fallback: e.g. "28.5, 65.2, 3.3"
    if (temp == null && hum == null && volt == null && trimmed.contains(",")) {
      val parts = trimmed.split(",").mapNotNull { it.trim().toFloatOrNull() }
      if (parts.size >= 2) {
        temp = parts.getOrNull(0)
        hum = parts.getOrNull(1)
        volt = parts.getOrNull(2)
      }
    }

    // If at least one metric or state is detected
    return if (temp != null || hum != null || volt != null || light != null || r1 != null || r2 != null || led != null || custom.isNotEmpty()) {
      ParsedTelemetry(
        temperature = temp,
        humidity = hum,
        voltage = volt,
        light = light,
        relay1State = r1,
        relay2State = r2,
        ledState = led,
        customMetrics = custom,
        rawText = trimmed
      )
    } else {
      null
    }
  }
}
