package com.example.generatorapp.activation

import android.content.Context
import kotlin.random.Random

/**
 * نظام تفعيل بسيط يعمل بالكامل دون إنترنت (offline)، مبني على "رقم جهاز" فريد يُنشأ محليًا
 * عند أول تشغيل، وكود تفعيل يُولَّد خارجيًا (عبر أداة HTML منفصلة يستخدمها المطوّر) بناءً على
 * هذا الرقم. يدعم نوعين من التفعيل:
 *  - تفعيل دائم (كود أيامه "000").
 *  - تفعيل مؤقت لعدد أيام محدد (كود أيامه من "001" حتى "999")، ويُحسب تاريخ انتهاء تلقائيًا.
 *
 * ملاحظة أمنية: هذا ليس تشفيرًا معياريًا (لا يحتاج مكتبات خارجية ويعمل بنفس الخوارزمية
 * تمامًا على جافاسكربت في أداة التوليد)، لكنه كافٍ لمنع الاستخدام العرضي بدون كود صحيح
 * لتطبيق محلي يُوزَّع يدويًا على المحلات.
 */
object ActivationManager {
    private const val PREFS_NAME = "activation_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_PERMANENT = "permanent"
    private const val KEY_EXPIRY_MILLIS = "expiry_millis"

    /**
     * المفتاح السري المشترك بين التطبيق وأداة توليد الأكواد (activation_code_generator.html).
     * يجب أن يبقى مطابقًا تمامًا في الملفين حتى تتطابق الأكواد الناتجة.
     */
    private const val SECRET_KEY = "GOLDEN-TRACK-2026"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** رقم الجهاز الفريد (6 أرقام) الذي يُرسله صاحب المحل للمطوّر للحصول على كود تفعيل */
    fun getDeviceId(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_DEVICE_ID, null)?.let { return it }
        val generated = (100000..999999).random(Random(System.nanoTime())).toString()
        p.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    /** خوارزمية checksum بسيطة، مطابقة تمامًا لنفس الدالة في أداة HTML لتوليد الأكواد */
    private fun computeChecksum(deviceId: String, daysPart: String): Int {
        val combined = deviceId + daysPart + SECRET_KEY
        var hash = 7L
        for ((i, ch) in combined.withIndex()) {
            hash = (hash * 31 + ch.code + i) % 1_000_000L
        }
        return (hash % 100_000L).toInt()
    }

    sealed class ActivationResult {
        data class Success(val message: String) : ActivationResult()
        data class Error(val message: String) : ActivationResult()
    }

    /**
     * يتحقق من كود التفعيل الذي أدخله المستخدم مقابل رقم جهازه الحالي، ويفعّل التطبيق
     * محليًا عند التطابق (دائم أو لعدد أيام محسوب من الكود نفسه).
     */
    fun validateAndActivate(context: Context, rawCode: String): ActivationResult {
        val digits = rawCode.filter { it.isDigit() }
        if (digits.length != 8) {
            return ActivationResult.Error("كود التفعيل غير صالح (يجب أن يتكون من 8 أرقام)")
        }
        val daysPart = digits.substring(0, 3)
        val checksumPart = digits.substring(3, 8)
        val deviceId = getDeviceId(context)
        val expectedChecksum = computeChecksum(deviceId, daysPart).toString().padStart(5, '0')

        if (checksumPart != expectedChecksum) {
            return ActivationResult.Error("كود التفعيل غير صحيح لهذا الجهاز")
        }

        val days = daysPart.toIntOrNull() ?: 0
        val p = prefs(context)
        if (days == 0) {
            p.edit()
                .putBoolean(KEY_ACTIVATED, true)
                .putBoolean(KEY_PERMANENT, true)
                .putLong(KEY_EXPIRY_MILLIS, 0L)
                .apply()
            return ActivationResult.Success("تم التفعيل الدائم بنجاح")
        }

        val expiry = System.currentTimeMillis() + days.toLong() * 24L * 60L * 60L * 1000L
        p.edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putBoolean(KEY_PERMANENT, false)
            .putLong(KEY_EXPIRY_MILLIS, expiry)
            .apply()
        return ActivationResult.Success("تم التفعيل بنجاح لمدة $days يومًا")
    }

    /**
     * هل التطبيق مفعّل حاليًا؟ في حال كان التفعيل مؤقتًا وانتهت مدته، يُلغى التفعيل تلقائيًا
     * (فيُطلب من المستخدم كود جديد) عند هذا الاستدعاء نفسه.
     */
    fun isActivated(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVATED, false)) return false
        if (p.getBoolean(KEY_PERMANENT, false)) return true

        val expiry = p.getLong(KEY_EXPIRY_MILLIS, 0L)
        if (expiry > System.currentTimeMillis()) return true

        // انتهت مدة التفعيل المؤقت: نلغي التفعيل حتى يظهر للمستخدم طلب كود جديد
        p.edit().putBoolean(KEY_ACTIVATED, false).apply()
        return false
    }

    fun isPermanent(context: Context): Boolean = prefs(context).getBoolean(KEY_PERMANENT, false)

    /** عدد الأيام المتبقية للتفعيل المؤقت (0 لو دائم أو غير مفعّل) */
    fun remainingDays(context: Context): Int {
        val p = prefs(context)
        if (p.getBoolean(KEY_PERMANENT, false)) return 0
        val expiry = p.getLong(KEY_EXPIRY_MILLIS, 0L)
        val remainingMillis = expiry - System.currentTimeMillis()
        if (remainingMillis <= 0) return 0
        return (remainingMillis / (24L * 60L * 60L * 1000L)).toInt() + 1
    }

    /** نص وصفي لحالة التفعيل الحالية، لعرضه في شاشة "حول البرنامج" مثلًا */
    fun statusText(context: Context): String {
        if (!isActivated(context)) return "غير مفعّل"
        return if (isPermanent(context)) "مفعّل بشكل دائم" else "مفعّل — متبقي ${remainingDays(context)} يوم"
    }
}
