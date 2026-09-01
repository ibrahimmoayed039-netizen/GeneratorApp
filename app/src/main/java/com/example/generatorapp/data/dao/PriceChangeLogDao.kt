package com.example.generatorapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.generatorapp.data.entities.PriceChangeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceChangeLogDao {
    @Query("SELECT * FROM price_change_logs WHERE generatorId = :generatorId ORDER BY changeDate DESC")
    fun getForGenerator(generatorId: Long): Flow<List<PriceChangeLog>>

    @Insert
    suspend fun insert(log: PriceChangeLog): Long
}
