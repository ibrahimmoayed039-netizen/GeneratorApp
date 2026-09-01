package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * سجل تغييرات أسعار الأمبير لمولد معيّن: تكلفة، بيع منزلي، بيع تجاري.
 * يُسجَّل تلقائيًا في كل مرة تُعدَّل فيها أسعار المولد من شاشة المولدات.
 */
@Entity(
    tableName = "price_change_logs",
    foreignKeys = [
        ForeignKey(
            entity = Generator::class,
            parentColumns = ["id"],
            childColumns = ["generatorId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PriceChangeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val generatorId: Long,
    val generatorName: String,
    val oldCostPrice: Double,
    val newCostPrice: Double,
    val oldResidentialPrice: Double,
    val newResidentialPrice: Double,
    val oldCommercialPrice: Double,
    val newCommercialPrice: Double,
    val changeDate: Long,
    val note: String = ""
)
