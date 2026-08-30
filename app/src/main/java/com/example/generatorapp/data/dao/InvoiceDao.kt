package com.example.generatorapp.data.dao

import androidx.room.*
import com.example.generatorapp.data.entities.Invoice
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAll(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE subscriberId = :subscriberId ORDER BY date DESC")
    fun getBySubscriber(subscriberId: Long): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE subscriberId = :subscriberId AND date BETWEEN :start AND :end ORDER BY date ASC")
    fun getForSubscriberBetween(subscriberId: Long, start: Long, end: Long): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getBetween(start: Long, end: Long): Flow<List<Invoice>>

    @Query("SELECT subscriberId, MAX(date) as lastDate FROM invoices GROUP BY subscriberId")
    suspend fun getLastPaymentPerSubscriber(): List<SubscriberLastPayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: Invoice): Long

    @Delete
    suspend fun delete(invoice: Invoice)
}

/** إسقاط (Projection) يمثل آخر تاريخ دفعة لكل مشترك — يُستخدم للكشف عن المتأخرين */
data class SubscriberLastPayment(
    val subscriberId: Long,
    val lastDate: Long
)
