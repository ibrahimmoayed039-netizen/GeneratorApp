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
    val discount: Double = 0.0,
    /** الشهر الذي يغطيه هذا الدفع (مثال: "سبتمبر 2026")، يظهر بالوصل كـ "تم الدفع لشهر: ..." */
    val paidForMonth: String = "",
    /** معرّف الموظف الذي استلم المبلغ (0 = المدير، أو فواتير قديمة قبل إضافة الموظفين) */
    val receivedById: Long = 0,
    /** اسم من استلم المبلغ وقت الدفع — يُحفظ نصًا حتى يبقى ظاهرًا لو حُذف الموظف لاحقًا */
    val receivedByName: String = "",
    /** إجمالي المبلغ المستحق لهذه الفاتورة (بعد الخصم). "amount" هو ما دُفع فعليًا بهذا الوصل (قد يكون جزءًا منه) */
    val totalDue: Double = 0.0,
    /** المتبقي على المشترك لهذا الشهر بعد هذه الدفعة (0 = مسدَّد بالكامل) */
    val remainingAfter: Double = 0.0
)

/** الفرق أقل من هذا يُعتبر صفرًا (لتجنّب أخطاء الفاصلة العشرية) */
const val MONEY_EPSILON = 0.005

/**
 * مجموع ما دفعه المشترك سابقًا عن شهر معيّن (من كل الوصولات المسجَّلة بنفس "تم الدفع لشهر").
 * يُستخدم لمعرفة المتبقي عند الدفع على دفعات.
 */
fun paidSoFarFor(invoices: List<Invoice>, subscriberId: Long, paidForMonth: String): Double =
    invoices.filter { it.subscriberId == subscriberId && it.paidForMonth == paidForMonth }
        .sumOf { it.amount }

/** ما دُفع سابقًا عن نفس الفاتورة قبل هذا الوصل (يُستنتج من المستحق والمتبقي وما دُفع الآن) */
fun Invoice.previouslyPaid(): Double =
    if (totalDue > 0.0) (totalDue - remainingAfter - amount).coerceAtLeast(0.0) else 0.0
