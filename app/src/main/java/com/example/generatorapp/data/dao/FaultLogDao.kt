package com.example.generatorapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.generatorapp.data.entities.FaultLog
import kotlinx.coroutines.flow.Flow

@Dao
interface FaultLogDao {
    @Query("SELECT * FROM fault_logs WHERE generatorId = :generatorId ORDER BY date DESC")
    fun getForGenerator(generatorId: Long): Flow<List<FaultLog>>

    @Insert
    suspend fun insert(log: FaultLog): Long

    @Update
    suspend fun update(log: FaultLog)

    @Delete
    suspend fun delete(log: FaultLog)
}
