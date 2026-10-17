package com.example.generatorapp.data.dao

import androidx.room.*
import com.example.generatorapp.data.entities.Employee
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY name")
    fun getAll(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE pin = :pin AND active = 1 LIMIT 1")
    suspend fun findActiveByPin(pin: String): Employee?

    @Query("SELECT COUNT(*) FROM employees WHERE pin = :pin")
    suspend fun countWithPin(pin: String): Int

    @Insert
    suspend fun insert(employee: Employee): Long

    @Update
    suspend fun update(employee: Employee)

    @Delete
    suspend fun delete(employee: Employee)
}
