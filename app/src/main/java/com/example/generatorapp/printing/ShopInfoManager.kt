package com.example.generatorapp.printing

import android.content.Context

/**
 * يدير حفظ اسم المحل/المولدة ورقم الهاتف بشكل دائم (SharedPreferences) ليظهرا
 * أعلى كل وصل بجانب الشعار — تمامًا مثل ترويسة فاتورة حقيقية (شعار + اسم + هاتف).
 * القيم تبقى محفوظة حتى بعد إغلاق التطبيق، ويقرأها ReceiptData عند بناء كل وصل جديد.
 */
object ShopInfoManager {
    private const val PREFS_NAME = "shop_info"
    private const val KEY_SHOP_NAME = "shop_name"
    private const val KEY_SHOP_PHONE = "shop_phone"

    const val DEFAULT_SHOP_NAME = "مدير المولدات"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** اسم المحل/المولدة الذي يظهر بخط بارز أعلى الوصل بجانب الشعار */
    fun getShopName(context: Context): String =
        prefs(context).getString(KEY_SHOP_NAME, DEFAULT_SHOP_NAME)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SHOP_NAME

    fun setShopName(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SHOP_NAME, value).apply()
    }

    /** رقم هاتف المحل/المولدة الذي يظهر أسفل الاسم أعلى الوصل (اختياري) */
    fun getShopPhone(context: Context): String =
        prefs(context).getString(KEY_SHOP_PHONE, "") ?: ""

    fun setShopPhone(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SHOP_PHONE, value).apply()
    }
}
