package com.example.generatorapp.messaging

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.generatorapp.data.entities.Subscriber

/**
 * إرسال رسائل SMS فعلية وتلقائية عبر SmsManager (بدون فتح أي تطبيق آخر).
 * يتطلب صلاحية SEND_SMS، وتعمل فقط إن كان بالجهاز شريحة اتصال (SIM) فعّالة.
 */
object SmsSender {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** يرسل رسالة واحدة، ويُرجع true إن نجح الإرسال (لا يضمن الوصول، فقط أن الجهاز قَبِل الإرسال) */
    fun sendSms(context: Context, phone: String, message: String): Boolean {
        if (!hasPermission(context) || phone.isBlank()) return false
        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** نتيجة إرسال دفعة رسائل: عدد الناجحين وقائمة المشتركين اللي فشل إرسال رسالتهم */
    data class BulkResult(val successCount: Int, val failed: List<Subscriber>)

    /** يرسل رسالة لكل عنصر بالقائمة (مشترك + نص الرسالة الخاص فيه) بشكل متتابع */
    fun sendBulk(context: Context, items: List<Pair<Subscriber, String>>): BulkResult {
        var success = 0
        val failed = mutableListOf<Subscriber>()
        items.forEach { (subscriber, message) ->
            if (sendSms(context, subscriber.phone, message)) {
                success++
            } else {
                failed.add(subscriber)
            }
        }
        return BulkResult(success, failed)
    }
}
