package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * موظف يستلم المبالغ من المشتركين. لكل موظف رمز PIN شخصي يُحدّد هويته عند تسجيل أي دفعة.
 * (المدير/صاحب المحل له رمز PIN الرئيسي من الإعدادات ويظهر باسم "المدير" ولا يُخزَّن هنا.)
 */
@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val pin: String,
    /** الموظف المعطَّل لا يستطيع تسجيل دفعات، لكن تبقى كل فواتيره القديمة باسمه */
    val active: Boolean = true
)
