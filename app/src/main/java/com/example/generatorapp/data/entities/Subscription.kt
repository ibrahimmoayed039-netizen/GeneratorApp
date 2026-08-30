package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * يربط مشترك معيّن بمولد معيّن مع عدد الأمبيرات المشترك بها
 */
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = Subscriber::class,
            parentColumns = ["id"],
            childColumns = ["subscriberId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Generator::class,
            parentColumns = ["id"],
            childColumns = ["generatorId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Subscription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subscriberId: Long,
    val generatorId: Long,
    val amperes: Double,
    val startDate: Long,
    val active: Boolean = true
)
