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
    private const val KEY_TRIAL_START = "trial_start_millis"
    private const val KEY_LAST_SEEN = "last_seen_millis"

    /** مدة الفترة التجريبية المجانية بعد تنصيب البرنامج (بالأيام) */
    const val TRIAL_DAYS = 5
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

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

        val expiry = safeNow(context) + days.toLong() * DAY_MILLIS
        p.edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putBoolean(KEY_PERMANENT, false)
            .putLong(KEY_EXPIRY_MILLIS, expiry)
            .apply()
        return ActivationResult.Success("تم التفعيل بنجاح لمدة $days يومًا")
    }

    /**
     * الوقت الحالي بعد حماية بسيطة من إرجاع ساعة الجهاز للخلف: لا نسمح للوقت بأن يقل عن
     * آخر وقت سُجّل سابقًا، فلا يمكن إطالة الفترة التجريبية بتغيير تاريخ الجهاز.
     */
    private fun safeNow(context: Context): Long {
        val p = prefs(context)
        val now = maxOf(System.currentTimeMillis(), p.getLong(KEY_LAST_SEEN, 0L))
        p.edit().putLong(KEY_LAST_SEEN, now).apply()
        return now
    }

    /** يسجّل بداية الفترة التجريبية عند أول تشغيل بعد التنصيب (مرة واحدة فقط) */
    private fun ensureTrialStarted(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_TRIAL_START)) {
            p.edit().putLong(KEY_TRIAL_START, safeNow(context)).apply()
        }
    }

    private fun trialEndMillis(context: Context): Long {
        ensureTrialStarted(context)
        return prefs(context).getLong(KEY_TRIAL_START, 0L) + TRIAL_DAYS * DAY_MILLIS
    }

    /** هل الفترة التجريبية (5 أيام) ما زالت سارية؟ */
    fun isTrialActive(context: Context): Boolean = safeNow(context) < trialEndMillis(context)

    /** عدد الأيام المتبقية من الفترة التجريبية (0 إذا انتهت) */
    fun trialRemainingDays(context: Context): Int {
        val remaining = trialEndMillis(context) - safeNow(context)
        if (remaining <= 0) return 0
        return (remaining / DAY_MILLIS).toInt() + 1
    }

    /** هل التفعيل الفعلي (بكود) ساري؟ — بدون احتساب الفترة التجريبية */
    private fun isCodeActivated(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVATED, false)) return false
        if (p.getBoolean(KEY_PERMANENT, false)) return true

        val expiry = p.getLong(KEY_EXPIRY_MILLIS, 0L)
        if (expiry > safeNow(context)) return true

        // انتهت مدة التفعيل المؤقت: نلغي التفعيل حتى يظهر للمستخدم طلب كود جديد
        p.edit().putBoolean(KEY_ACTIVATED, false).apply()
        return false
    }

    /**
     * هل يُسمح بتشغيل التطبيق حاليًا؟ نعم إذا كان مفعّلًا بكود صالح، أو ما زالت الفترة
     * التجريبية (5 أيام من أول تشغيل) سارية. بعد انتهائها يُطلب كود التفعيل.
     */
    fun isActivated(context: Context): Boolean {
        ensureTrialStarted(context)
        return isCodeActivated(context) || isTrialActive(context)
    }

    fun isPermanent(context: Context): Boolean = prefs(context).getBoolean(KEY_PERMANENT, false)

    /** عدد الأيام المتبقية للتفعيل المؤقت (0 لو دائم أو غير مفعّل) */
    fun remainingDays(context: Context): Int {
        val p = prefs(context)
        if (p.getBoolean(KEY_PERMANENT, false)) return 0
        val expiry = p.getLong(KEY_EXPIRY_MILLIS, 0L)
        val remainingMillis = expiry - safeNow(context)
        if (remainingMillis <= 0) return 0
        return (remainingMillis / (24L * 60L * 60L * 1000L)).toInt() + 1
    }

    /** نص وصفي لحالة التفعيل الحالية، لعرضه في شاشة "حول البرنامج" مثلًا */
    fun statusText(context: Context): String {
        if (isCodeActivated(context)) {
            return if (isPermanent(context)) "مفعّل بشكل دائم" else "مفعّل — متبقي ${remainingDays(context)} يوم"
        }
        if (isTrialActive(context)) return "فترة تجريبية — متبقي ${trialRemainingDays(context)} يوم"
        return "غير مفعّل"
    }
}
