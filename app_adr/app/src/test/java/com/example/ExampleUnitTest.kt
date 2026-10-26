package com.example

import com.example.data.model.TelemetryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testJsonTelemetryParser() {
    val json = """{"temp": 28.5, "hum": 64.2, "volt": 3.31, "r1": 1, "r2": 0}"""
    val parsed = TelemetryParser.parse(json)

    assertNotNull(parsed)
    assertEquals(28.5f, parsed!!.temperature ?: 0f, 0.01f)
    assertEquals(64.2f, parsed.humidity ?: 0f, 0.01f)
    assertEquals(3.31f, parsed.voltage ?: 0f, 0.01f)
    assertEquals(true, parsed.relay1State)
    assertEquals(false, parsed.relay2State)
  }

  @Test
  fun testKeyValueTelemetryParser() {
    val kv = "TEMP:29.1 HUM:70.5 VOLT:3.28"
    val parsed = TelemetryParser.parse(kv)

    assertNotNull(parsed)
    assertEquals(29.1f, parsed!!.temperature ?: 0f, 0.01f)
    assertEquals(70.5f, parsed.humidity ?: 0f, 0.01f)
    assertEquals(3.28f, parsed.voltage ?: 0f, 0.01f)
  }

  @Test
  fun testCsvTelemetryParser() {
    val csv = "30.2, 55.4, 3.3"
    val parsed = TelemetryParser.parse(csv)

    assertNotNull(parsed)
    assertEquals(30.2f, parsed!!.temperature ?: 0f, 0.01f)
    assertEquals(55.4f, parsed.humidity ?: 0f, 0.01f)
  }
}
