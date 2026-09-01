package com.example.generatorapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.generatorapp.data.entities.GeneratorHourLog
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratorHourLogDao {
    @Query("SELECT * FROM generator_hour_logs WHERE generatorId = :generatorId ORDER BY date DESC")
    fun getForGenerator(generatorId: Long): Flow<List<GeneratorHourLog>>

    @Query("SELECT * FROM generator_hour_logs WHERE generatorId = :generatorId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestForGenerator(generatorId: Long): GeneratorHourLog?

    @Insert
    suspend fun insert(log: GeneratorHourLog): Long

    @Delete
    suspend fun delete(log: GeneratorHourLog)
}
