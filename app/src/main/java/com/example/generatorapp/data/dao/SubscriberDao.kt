package com.example.generatorapp.data.dao

import androidx.room.*
import com.example.generatorapp.data.entities.Subscriber
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriberDao {
    @Query("SELECT * FROM subscribers ORDER BY name ASC")
    fun getAll(): Flow<List<Subscriber>>

    @Query("SELECT * FROM subscribers WHERE id = :id")
    suspend fun getById(id: Long): Subscriber?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscriber: Subscriber): Long

    @Update
    suspend fun update(subscriber: Subscriber)

    @Delete
    suspend fun delete(subscriber: Subscriber)
}
