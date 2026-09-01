package com.example.generatorapp.messaging

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.generatorapp.notifications.MessageSettings

/**
 * فتح محادثة واتساب لرقم معيّن مع نص الرسالة جاهزًا بخانة الكتابة.
 *
 * ملاحظة مهمة: واتساب لا يسمح لأي تطبيق خارجي بإرسال الرسالة تلقائيًا بدون ضغط
 * المستخدم لزر الإرسال بنفسه (هذا قيد من واتساب نفسه لمنع السبام، ولا يوجد حل
 * برمجي عادي لتجاوزه إلا عبر واتساب بزنس API الرسمي المدفوع). لذلك هذه الأداة
 * "تفتح المحادثة بالرسالة جاهزة" لكل مشترك، ويبقى ضغط زر الإرسال بواتساب يدويًا.
 */
object WhatsAppSender {

    /** يطبّع رقم الهاتف: يشيل المسافات والرموز، ويستبدل الصفر بالبداية برمز الدولة إن وُجد */
    fun normalizePhone(context: Context, rawPhone: String): String {
        var digits = rawPhone.filter { it.isDigit() }
        if (digits.isBlank()) return digits

        val countryCode = MessageSettings.getCountryCode(context).filter { it.isDigit() }
        if (countryCode.isNotBlank() && digits.startsWith("0")) {
            digits = countryCode + digits.removePrefix("0")
        }
        return digits
    }

    /** يفتح تطبيق واتساب على محادثة الرقم مع الرسالة جاهزة، ويرجع true إن نجح فتح التطبيق */
    fun openChat(context: Context, rawPhone: String, message: String): Boolean {
        val phone = normalizePhone(context, rawPhone)
        if (phone.isBlank()) return false
        return try {
            val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
