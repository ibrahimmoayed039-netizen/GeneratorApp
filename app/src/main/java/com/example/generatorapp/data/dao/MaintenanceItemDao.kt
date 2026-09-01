package com.example.generatorapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.generatorapp.data.entities.MaintenanceItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceItemDao {
    @Query("SELECT * FROM maintenance_items WHERE generatorId = :generatorId ORDER BY type ASC")
    fun getForGenerator(generatorId: Long): Flow<List<MaintenanceItem>>

    @Query("SELECT * FROM maintenance_items")
    fun getAll(): Flow<List<MaintenanceItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MaintenanceItem): Long

    @Update
    suspend fun update(item: MaintenanceItem)

    @Delete
    suspend fun delete(item: MaintenanceItem)
}
