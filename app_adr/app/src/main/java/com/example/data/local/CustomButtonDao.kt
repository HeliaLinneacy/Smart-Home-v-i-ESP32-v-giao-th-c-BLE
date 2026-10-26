package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomButtonDao {
  @Query("SELECT * FROM custom_buttons ORDER BY orderIndex ASC, id ASC")
  fun getAllButtons(): Flow<List<CustomButton>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertButton(button: CustomButton): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(buttons: List<CustomButton>)

  @Update
  suspend fun updateButton(button: CustomButton)

  @Delete
  suspend fun deleteButton(button: CustomButton)

  @Query("DELETE FROM custom_buttons WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT COUNT(*) FROM custom_buttons")
  suspend fun getButtonCount(): Int
}
