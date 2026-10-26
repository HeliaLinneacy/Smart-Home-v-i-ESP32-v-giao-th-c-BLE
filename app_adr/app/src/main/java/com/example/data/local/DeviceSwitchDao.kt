package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceSwitchDao {
  @Query("SELECT * FROM device_switches ORDER BY id ASC")
  fun getAllSwitches(): Flow<List<DeviceSwitch>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSwitch(deviceSwitch: DeviceSwitch): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(switches: List<DeviceSwitch>)

  @Update
  suspend fun updateSwitch(deviceSwitch: DeviceSwitch)

  @Delete
  suspend fun deleteSwitch(deviceSwitch: DeviceSwitch)

  @Query("DELETE FROM device_switches WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT COUNT(*) FROM device_switches")
  suspend fun getCount(): Int
}
