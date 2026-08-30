package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * يمثل فاتورة/وصل دفع لمشترك معيّن
 */
@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = Subscriber::class,
            parentColumns = ["id"],
            childColumns = ["subscriberId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subscriberId: Long,
    val subscriberName: String,
    val generatorName: String,
    val amperes: Double,
    val pricePerAmpere: Double,
    val amount: Double,
    val date: Long,
    val paid: Boolean = true,
    val note: String = ""
)
