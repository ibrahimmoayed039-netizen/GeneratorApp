package com.example.generatorapp.notifications

import android.content.Context

/**
 * إعدادات رسائل التذكير (واتساب/SMS) — قوالب النصوص، تفعيل الإرسال التلقائي، ورمز الدولة
 * المستخدم لتطبيع أرقام الهواتف عند فتح واتساب. يُخزَّن كل شيء بـ SharedPreferences بسيط
 * حتى يقدر صاحب المحل يعدّل نص الرسالة بدون الحاجة لتعديل الكود.
 */
object MessageSettings {
    private const val PREFS_NAME = "message_settings"
    private const val KEY_LATE_TEMPLATE = "late_template"
    private const val KEY_PRICE_TEMPLATE = "price_template"
    private const val KEY_AUTO_SMS_ENABLED = "auto_sms_enabled"
    private const val KEY_SMS_FEATURE_ENABLED = "sms_feature_enabled"
    private const val KEY_COUNTRY_CODE = "country_code"

    /**
     * القوالب المتاحة: {name} اسم المشترك، {month} اسم الشهر الحالي، {price} سعر الأمبير،
     * {generator} اسم المولدة، {type} نوع الاشتراك (منزلي/تجاري)
     */
    const val DEFAULT_LATE_TEMPLATE =
        "مرحبًا {name}، نود تذكيركم بأن اشتراك المولدة لشهر {month} لم يُسجَّل بعد كمدفوع. " +
            "نرجو تسديد المستحقات في أقرب وقت ممكن. شاكرين تعاونكم معنا."

    const val DEFAULT_PRICE_TEMPLATE =
        "مرحبًا {name}، نعلمكم بأن سعر الأمبير لشهر {month} هو {price} لكل أمبير " +
            "(مولدة {generator} - اشتراك {type}). شكرًا لكم."

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLateTemplate(context: Context): String =
        prefs(context).getString(KEY_LATE_TEMPLATE, DEFAULT_LATE_TEMPLATE) ?: DEFAULT_LATE_TEMPLATE

    fun setLateTemplate(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LATE_TEMPLATE, value).apply()
    }

    fun getPriceTemplate(context: Context): String =
        prefs(context).getString(KEY_PRICE_TEMPLATE, DEFAULT_PRICE_TEMPLATE) ?: DEFAULT_PRICE_TEMPLATE

    fun setPriceTemplate(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PRICE_TEMPLATE, value).apply()
    }

    /** هل يرسل التطبيق SMS تلقائيًا (بدون تدخل المستخدم) للمتأخرين عند الفحص اليومي */
    fun isAutoSmsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SMS_ENABLED, false)

    fun setAutoSmsEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SMS_ENABLED, value).apply()
    }

    /**
     * هل ميزة SMS مفعّلة أصلاً بالتطبيق. معطّلة افتراضيًا (اختيارية بالكامل) —
     * إذا معطّلة تختفي كل أزرار SMS من الواجهة، ولا يُطلب إذن SEND_SMS نهائيًا،
     * ويبقى واتساب فقط متاحًا للإرسال.
     */
    fun isSmsFeatureEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SMS_FEATURE_ENABLED, false)

    fun setSmsFeatureEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SMS_FEATURE_ENABLED, value).apply()
        // إذا عطّلنا ميزة SMS بالكامل، نوقف الإرسال التلقائي معها تلقائيًا
        if (!value) setAutoSmsEnabled(context, false)
    }

    /** رمز الدولة الدولي بدون علامة + (مثال: 964 للعراق) — يُستخدم لتطبيع الأرقام عند فتح واتساب */
    fun getCountryCode(context: Context): String =
        prefs(context).getString(KEY_COUNTRY_CODE, "") ?: ""

    fun setCountryCode(context: Context, value: String) {
        prefs(context).edit().putString(KEY_COUNTRY_CODE, value).apply()
    }
}
