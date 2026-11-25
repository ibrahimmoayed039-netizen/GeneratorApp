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
    /** الخصم المطبّق على إجمالي هذا الوصل (اختياري، صفر = بدون خصم) */
    val discount: Double = 0.0,
    /** المبلغ الصافي المطلوب من المشترك بعد خصم "discount" من (عدد الأمبيرات × سعر الأمبير) */
    val amount: Double,
    val dateMillis: Long,
    /** الشهر الذي يغطيه هذا الدفع (مثال: "سبتمبر 2026")، يظهر كسطر "تم الدفع لشهر: ..." */
    val paidForMonth: String = "",
    val note: String = "",
    /** اسم الموظف الذي استلم المبلغ (اختياري) — يظهر على الوصل */
    val receivedBy: String = "",
    /** شعار المحل (اختياري) — يُحمَّل من LogoManager ويُستخدم في المعاينة والطباعتين */
    val logo: Bitmap? = null,
    /** إجمالي المستحق للفاتورة (بعد الخصم) — يُستخدم فقط لعرض تفاصيل الدفع الجزئي */
    val totalDue: Double = 0.0,
    /** ما دُفع سابقًا عن نفس الشهر قبل هذا الوصل */
    val previouslyPaid: Double = 0.0,
    /** المتبقي على المشترك بعد هذا الوصل (0 = مسدَّد بالكامل) */
    val remaining: Double = 0.0
) {
    /** هل هذا الوصل جزء من مبلغ أكبر (دفعة جزئية أو دفعة تُكمل جزءًا سابقًا)؟ */
    val isPartial: Boolean get() = totalDue > 0.0 && (remaining > 0.005 || previouslyPaid > 0.005)

    /** عنوان المبلغ الرئيسي بالوصل: "المبلغ المدفوع" عند الدفع الجزئي، وإلا "المبلغ الإجمالي" */
    val amountLabel: String get() = if (isPartial) "المبلغ المدفوع" else "المبلغ الإجمالي"

    /** الإجمالي قبل الخصم = عدد الأمبيرات × سعر الأمبير (= amount + discount) */
    fun subtotal(): Double = amount + discount

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
        if (discount > 0) "الإجمالي قبل الخصم: ${Formatters.formatMoney(subtotal())}" else "",
        if (discount > 0) "الخصم: ${Formatters.formatMoney(discount)}" else "",
        if (isPartial) "إجمالي المستحق: ${Formatters.formatMoney(totalDue)}" else "",
        if (isPartial && previouslyPaid > 0.005) "المدفوع سابقًا: ${Formatters.formatMoney(previouslyPaid)}" else "",
        "$amountLabel: ${Formatters.formatMoney(amount)}",
        if (isPartial) "المتبقي: ${Formatters.formatMoney(remaining)}" else "",
        if (paidForMonth.isNotBlank()) "تم الدفع لشهر: $paidForMonth" else "",
        if (receivedBy.isNotBlank()) "استلم المبلغ: $receivedBy" else "",
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
        if (discount > 0) {
            add(ReceiptLine.Field("الإجمالي قبل الخصم", Formatters.formatMoney(subtotal())))
            add(ReceiptLine.Field("الخصم", "- ${Formatters.formatMoney(discount)}"))
        }
        if (isPartial) {
            add(ReceiptLine.Field("إجمالي المستحق", Formatters.formatMoney(totalDue)))
            if (previouslyPaid > 0.005) add(ReceiptLine.Field("المدفوع سابقًا", Formatters.formatMoney(previouslyPaid)))
        }
        add(ReceiptLine.Total(amountLabel, Formatters.formatMoney(amount)))
        if (isPartial) {
            add(ReceiptLine.Field("المتبقي", if (remaining > 0.005) Formatters.formatMoney(remaining) else "لا يوجد (مسدَّد)"))
        }
        if (paidForMonth.isNotBlank()) add(ReceiptLine.Note("تم الدفع لشهر: $paidForMonth"))
        if (receivedBy.isNotBlank()) add(ReceiptLine.Note("استلم المبلغ: $receivedBy"))
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
