package com.example.generatorapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.generatorapp.data.entities.AmpereChangeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AmpereChangeLogDao {
    @Query("SELECT * FROM ampere_change_logs WHERE subscriberId = :subscriberId ORDER BY changeDate DESC")
    fun getForSubscriber(subscriberId: Long): Flow<List<AmpereChangeLog>>

    @Query("SELECT * FROM ampere_change_logs WHERE subscriptionId = :subscriptionId ORDER BY changeDate DESC")
    fun getForSubscription(subscriptionId: Long): Flow<List<AmpereChangeLog>>

    @Insert
    suspend fun insert(log: AmpereChangeLog): Long
}
