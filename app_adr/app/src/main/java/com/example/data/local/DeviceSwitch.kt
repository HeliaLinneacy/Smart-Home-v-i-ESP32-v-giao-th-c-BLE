package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_switches")
data class DeviceSwitch(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val name: String,
  val onCommand: String,
  val offCommand: String,
  val isOn: Boolean = false,
  val iconName: String = "toggle", // "toggle", "lightbulb", "fan", "water_drop", "volume_up", "power"
  val colorHex: String = "#00E676"
)
