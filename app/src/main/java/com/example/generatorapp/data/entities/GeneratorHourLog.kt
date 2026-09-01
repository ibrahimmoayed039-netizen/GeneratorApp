package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل قراءة عداد ساعات التشغيل لمولد معيّن بتاريخ معيّن.
 * [hours] هي القراءة التراكمية لعداد الساعات وقت التسجيل (وليست عدد ساعات تشغيل يومية).
 */
@Entity(tableName = "generator_hour_logs")
data class GeneratorHourLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val generatorId: Long,
    val hours: Double,
    val date: Long,
    val note: String = ""
)
