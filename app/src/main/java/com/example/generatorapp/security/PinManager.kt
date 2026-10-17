package com.example.generatorapp.security

import android.content.Context

/**
 * يدير رمز PIN الموحّد الذي يدخله أي موظف قبل تسجيل دفعة (إنشاء فاتورة).
 * يُحفظ في SharedPreferences الخاصة بالتطبيق، ويبقى محفوظًا بعد إغلاق التطبيق.
 * الرمز الافتراضي عند أول تشغيل هو "1234"، ويمكن لصاحب المحل تغييره من شاشة الإعدادات.
 */
object PinManager {

    private const val PREFS_NAME = "app_security_prefs"
    private const val KEY_PIN = "employee_pin"
    const val DEFAULT_PIN = "1234"
    /** الاسم الذي يظهر على الفواتير عندما يدخل صاحب المحل برمزه الرئيسي */
    const val OWNER_NAME = "المدير"
    private const val KEY_PIN_EVERY_PAYMENT = "pin_every_payment"

    /** لو مفعّل: تُقفل الجلسة بعد كل دفعة فيُطلب رمز الموظف لكل دفعة (أدق لمعرفة من استلم المال) */
    fun requirePinEveryPayment(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PIN_EVERY_PAYMENT, false)

    fun setRequirePinEveryPayment(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PIN_EVERY_PAYMENT, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPin(context: Context): String =
        prefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(context: Context, newPin: String) {
        prefs(context).edit().putString(KEY_PIN, newPin).apply()
    }

    fun verifyPin(context: Context, entered: String): Boolean =
        entered.isNotBlank() && entered == getPin(context)
}
