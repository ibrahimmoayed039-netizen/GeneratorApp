package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل بند صيانة دورية لمولد معيّن (تغيير زيت، فلتر، صيانة عامة...).
 * يُحدَّد موعد الاستحقاق التالي بمقارنة ساعات التشغيل الحالية للمولد
 * ([com.example.generatorapp.data.entities.Generator.currentHours]) مع
 * [lastServiceHours] + [intervalHours].
 */
@Entity(tableName = "maintenance_items")
data class MaintenanceItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val generatorId: Long,
    val type: String, // مثال: "تغيير الزيت" | "فلتر الهواء" | "صيانة دورية"
    val intervalHours: Double,
    val lastServiceHours: Double = 0.0,
    val lastServiceDate: Long = System.currentTimeMillis(),
    val note: String = ""
)
