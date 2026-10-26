package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_buttons")
data class CustomButton(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val label: String,
  val command: String,
  val colorHex: String = "#00E5FF",
  val iconName: String = "bolt",
  val orderIndex: Int = 0,
  val isHex: Boolean = false
)
