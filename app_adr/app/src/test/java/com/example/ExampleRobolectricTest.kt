package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ESP32 BLE Terminal", appName)
  }

  @Test
  fun `test toggle device switch`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.MainViewModel(application)
    val testSw = com.example.data.local.DeviceSwitch(
      id = 1L,
      name = "Relay 1",
      onCommand = "RELAY:1:ON\n",
      offCommand = "RELAY:1:OFF\n",
      isOn = false
    )
    vm.toggleDeviceSwitch(testSw)
  }
}
