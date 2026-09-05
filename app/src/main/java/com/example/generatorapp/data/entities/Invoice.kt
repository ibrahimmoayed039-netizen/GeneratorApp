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
    val note: String = "",
    /** نوع المشترك وقت إصدار الفاتورة (منزلي/تجاري) — يُحفظ هنا حتى لو تغيّر نوعه لاحقًا */
    val subscriberType: String = "",
    /** سعر تكلفة الأمبير وقت إصدار الفاتورة (لحساب الربح لاحقًا حتى لو تغيّرت التكلفة بالمستقبل) */
    val costPricePerAmpere: Double = 0.0,
    /** الربح الصافي لهذه الفاتورة = المبلغ - (سعر التكلفة × عدد الأمبيرات) */
    val profit: Double = 0.0,
    /** الخصم المطبّق على إجمالي هذه الفاتورة (اختياري، صفر = بدون خصم). "amount" هو المبلغ بعد خصم هذه القيمة */
    val discount: Double = 0.0
)
