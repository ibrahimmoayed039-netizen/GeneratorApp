package com.example.generatorapp.printing

import android.graphics.Bitmap
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * نموذج بيانات موحّد للوصل — يُستخدم في:
 * 1) معاينة الوصل على الشاشة (ReceiptPreview.kt)
 * 2) الطباعة الحرارية ESC/POS (EscPosPrinter.kt)
 * 3) الطباعة العادية عبر نظام أندرويد (SystemPrintAdapter.kt)
 * بحيث يكون الشكل المعروض في المعاينة مطابقًا تمامًا لما يُطبع فعليًا.
 */
data class ReceiptData(
    val shopName: String = "مدير المولدات",
    val subscriberName: String,
    val generatorName: String,
    val amperes: Double,
    val pricePerAmpere: Double,
    val amount: Double,
    val dateMillis: Long,
    val note: String = "",
    /** شعار المحل (اختياري) — يُحمَّل من LogoManager ويُستخدم في المعاينة والطباعتين */
    val logo: Bitmap? = null
) {
    fun formattedDate(): String =
        SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar")).format(dateMillis)

    /** أسطر الوصل كنص عادي، تُستخدم في المعاينة والطباعة الحرارية معًا */
    fun toLines(): List<String> = listOf(
        shopName,
        "----------------------------",
        "وصل دفع",
        "التاريخ: ${formattedDate()}",
        "المشترك: $subscriberName",
        "المولد: $generatorName",
        "عدد الأمبيرات: $amperes A",
        "سعر الأمبير: ${"%.2f".format(pricePerAmpere)}",
        "----------------------------",
        "المبلغ الإجمالي: ${"%.2f".format(amount)}",
        if (note.isNotBlank()) "ملاحظة: $note" else "",
        "----------------------------",
        "شكراً لتعاملكم معنا"
    ).filter { it.isNotBlank() }
}
