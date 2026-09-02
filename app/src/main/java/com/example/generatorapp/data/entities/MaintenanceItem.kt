package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل بند صيانة دورية لمولد معيّن (تغيير زيت، فلتر، صيانة عامة...).
 * يُحدَّد موعد الاستحقاق التالي بمقارنة ساعات التشغيل الحالية للمولد
 * ([com.example.generatorapp.data.entities.Generator.currentHours]) مع
 * [lastServiceHours] + [intervalHours]، وأيضًا (اختياريًا) بعدد الأيام منذ
 * [lastServiceDate] مقارنة بـ [intervalDays] — أيهما يصل أولًا يُعتبر البند مستحقًا.
 * مفيد لبند مثل "تغيير الزيت كل 250 ساعة أو كل 90 يوم أيهما أقرب".
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
    /** الحد الأقصى بالأيام قبل الاستحقاق، بغض النظر عن ساعات التشغيل (0 = تنبيه بالساعات فقط، بدون تنبيه بالأيام) */
    val intervalDays: Int = 0,
    val note: String = ""
)
