package com.example.generatorapp.printing

import android.graphics.Bitmap
import com.example.generatorapp.util.Formatters
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
    /** رقم هاتف المحل/المولدة (اختياري) — يظهر أسفل اسم المحل أعلى الوصل بجانب الشعار */
    val shopPhone: String = "",
    val subscriberName: String,
    val meterNumber: String = "",
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

    /** أسطر الوصل كنص عادي مسطّح — تُستخدم فقط عند الحاجة لنص خام (مثل رسالة مشاركة) */
    fun toLines(): List<String> = listOf(
        shopName,
        if (shopPhone.isNotBlank()) "هاتف: $shopPhone" else "",
        "----------------------------",
        "وصل دفع",
        "التاريخ: ${formattedDate()}",
        "المشترك: $subscriberName",
        if (meterNumber.isNotBlank()) "رقم العداد: $meterNumber" else "",
        "المولد: $generatorName",
        "عدد الأمبيرات: ${Formatters.formatMoney(amperes)} أمبير",
        "سعر الأمبير: ${Formatters.formatMoney(pricePerAmpere)}",
        "----------------------------",
        "المبلغ الإجمالي: ${Formatters.formatMoney(amount)}",
        if (note.isNotBlank()) "ملاحظة: $note" else "",
        "----------------------------",
        "شكراً لتعاملكم معنا"
    ).filter { it.isNotBlank() }

    /**
     * تمثيل بنيوي (لا نص مسطّح) لمحتوى الوصل، يستخدمه كل من الطباعة الحرارية
     * (EscPosPrinter) والطباعة العادية (SystemPrintAdapter) لرسم كل حقل في عمودين
     * منفصلين (تسمية على جهة، قيمة محاذاة على الجهة الأخرى) — بدل سطر نصي واحد ملتصق —
     * فيصبح شكل الوصل أقرب لفاتورة احترافية حقيقية بدل نص عادي متلاحق.
     */
    fun toReceiptLines(): List<ReceiptLine> = buildList {
        add(ReceiptLine.Header(shopName, shopPhone))
        add(ReceiptLine.Badge("وصل دفع"))
        add(ReceiptLine.Divider(double = true))
        add(ReceiptLine.Field("التاريخ", formattedDate()))
        add(ReceiptLine.Field("المشترك", subscriberName))
        if (meterNumber.isNotBlank()) add(ReceiptLine.Field("رقم العداد", meterNumber))
        add(ReceiptLine.Field("المولد", generatorName))
        add(ReceiptLine.Field("عدد الأمبيرات", "${Formatters.formatMoney(amperes)} أمبير"))
        add(ReceiptLine.Field("سعر الأمبير", Formatters.formatMoney(pricePerAmpere)))
        add(ReceiptLine.Divider())
        add(ReceiptLine.Total("المبلغ الإجمالي", Formatters.formatMoney(amount)))
        if (note.isNotBlank()) add(ReceiptLine.Note("ملاحظة: $note"))
        add(ReceiptLine.Divider())
        add(ReceiptLine.Footer("شكراً لتعاملكم معنا"))
    }
}

/**
 * سطر واحد من الوصل بحسب دوره (عنوان / حقل تسمية-قيمة / إجمالي / فاصل ...)، ليقرر كل
 * محوّل طباعة (حراري أو نظامي) كيف يرسمه بالشكل المناسب له بدل التعامل مع نص مسطّح.
 */
sealed class ReceiptLine {
    data class Header(val text: String, val phone: String = "") : ReceiptLine()
    data class Badge(val text: String) : ReceiptLine()
    data class Field(val label: String, val value: String) : ReceiptLine()
    data class Total(val label: String, val value: String) : ReceiptLine()
    data class Note(val text: String) : ReceiptLine()
    data class Footer(val text: String) : ReceiptLine()
    data class Divider(val double: Boolean = false) : ReceiptLine()
}
