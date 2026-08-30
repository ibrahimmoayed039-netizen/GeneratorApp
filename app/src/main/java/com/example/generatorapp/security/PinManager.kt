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
