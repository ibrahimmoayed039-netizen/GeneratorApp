package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل مشترك واحد لدى صاحب المولد
 */
@Entity(tableName = "subscribers")
data class Subscriber(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String,
    val meterNumber: String,
    /** المنطقة أو الحي — تُستخدم لتصنيف المشتركين وتسهيل التحصيل الميداني */
    val area: String = "",
    /** نوع المشترك: منزلي أو تجاري — يحدد سعر بيع الأمبير المطبَّق عليه (انظر SubscriberType) */
    val subscriberType: String = SubscriberType.RESIDENTIAL
)
