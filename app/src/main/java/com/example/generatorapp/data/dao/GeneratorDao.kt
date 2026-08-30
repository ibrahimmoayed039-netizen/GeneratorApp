package com.example.generatorapp.data.dao

import androidx.room.*
import com.example.generatorapp.data.entities.Generator
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratorDao {
    @Query("SELECT * FROM generators ORDER BY name ASC")
    fun getAll(): Flow<List<Generator>>

    @Query("SELECT * FROM generators WHERE id = :id")
    suspend fun getById(id: Long): Generator?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(generator: Generator): Long

    @Update
    suspend fun update(generator: Generator)

    @Delete
    suspend fun delete(generator: Generator)
}
