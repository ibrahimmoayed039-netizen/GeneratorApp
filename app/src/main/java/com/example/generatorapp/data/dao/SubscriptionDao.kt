package com.example.generatorapp.data.dao

import androidx.room.*
import com.example.generatorapp.data.entities.Subscription
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE active = 1")
    fun getActive(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE subscriberId = :subscriberId")
    fun getBySubscriber(subscriberId: Long): Flow<List<Subscription>>

    /** كل اشتراكات المشترك (المفعّلة والمعلّقة معًا) — تُستخدم بشاشة إدارة الاشتراك */
    @Query("SELECT * FROM subscriptions WHERE subscriberId = :subscriberId ORDER BY active DESC, startDate DESC")
    fun getAllBySubscriber(subscriberId: Long): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): Subscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: Subscription): Long

    @Update
    suspend fun update(subscription: Subscription)

    @Delete
    suspend fun delete(subscription: Subscription)
}
