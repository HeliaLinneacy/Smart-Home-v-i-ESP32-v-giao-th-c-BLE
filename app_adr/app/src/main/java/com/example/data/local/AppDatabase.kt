package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
  entities = [LogEntry::class, CustomButton::class, DeviceSwitch::class],
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun logDao(): LogDao
  abstract fun customButtonDao(): CustomButtonDao
  abstract fun deviceSwitchDao(): DeviceSwitchDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "esp32_ble_database.db"
        )
          .fallbackToDestructiveMigration()
          .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
              super.onCreate(db)
              populateDefaults(context)
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
              super.onDestructiveMigration(db)
              populateDefaults(context)
            }
          })
          .build()
        INSTANCE = instance
        instance
      }
    }

    private fun populateDefaults(context: Context) {
      CoroutineScope(Dispatchers.IO).launch {
        try {
          val dbInstance = getDatabase(context)
          dbInstance.customButtonDao().insertAll(defaultButtons)
          dbInstance.deviceSwitchDao().insertAll(defaultSwitches)
        } catch (_: Exception) {}
      }
    }

    val defaultButtons = listOf(
      CustomButton(label = "STATUS", command = "STATUS\n", colorHex = "#38BDF8", iconName = "info", orderIndex = 0),
      CustomButton(label = "GET DATA", command = "GET:DATA\n", colorHex = "#F59E0B", iconName = "sensors", orderIndex = 1),
      CustomButton(label = "BEEP 100ms", command = "BUZZER:BEEP\n", colorHex = "#EC4899", iconName = "volume_up", orderIndex = 2),
      CustomButton(label = "RESET ESP", command = "RESET\n", colorHex = "#EF4444", iconName = "restart_alt", orderIndex = 3),
      CustomButton(label = "PING", command = "PING\n", colorHex = "#00E5FF", iconName = "bolt", orderIndex = 4),
      CustomButton(label = "HELP", command = "HELP\n", colorHex = "#A855F7", iconName = "info", orderIndex = 5)
    )

    val defaultSwitches = listOf(
      DeviceSwitch(name = "Relay 1", onCommand = "RELAY:1:ON\n", offCommand = "RELAY:1:OFF\n", iconName = "toggle", colorHex = "#00E676"),
      DeviceSwitch(name = "Relay 2", onCommand = "RELAY:2:ON\n", offCommand = "RELAY:2:OFF\n", iconName = "toggle", colorHex = "#10B981"),
      DeviceSwitch(name = "Đèn LED Pin 2", onCommand = "LED:ON\n", offCommand = "LED:OFF\n", iconName = "lightbulb", colorHex = "#00E5FF"),
      DeviceSwitch(name = "Còi Buzzer", onCommand = "BUZZER:ON\n", offCommand = "BUZZER:OFF\n", iconName = "volume_up", colorHex = "#EC4899")
    )
  }
}
