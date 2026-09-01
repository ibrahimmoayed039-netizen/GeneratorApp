package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل عطلاً تم رصده على مولد معيّن، مع تفاصيل التصليح (إن وُجد) وتكلفته.
 */
@Entity(tableName = "fault_logs")
data class FaultLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val generatorId: Long,
    val date: Long,
    val faultDescription: String,
    val repairDescription: String = "",
    val cost: Double = 0.0,
    val resolved: Boolean = false,
    val resolvedDate: Long? = null
)
