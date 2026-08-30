package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * سجل تغييرات الأمبيرات: يسجّل كل مرة يزيد أو يقل فيها اشتراك المشترك بالأمبير
 */
@Entity(
    tableName = "ampere_change_logs",
    foreignKeys = [
        ForeignKey(
            entity = Subscription::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Subscriber::class,
            parentColumns = ["id"],
            childColumns = ["subscriberId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AmpereChangeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subscriptionId: Long,
    val subscriberId: Long,
    val oldAmperes: Double,
    val newAmperes: Double,
    val changeDate: Long,
    val note: String = ""
)
