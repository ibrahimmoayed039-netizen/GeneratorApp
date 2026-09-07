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
    private const val KEY_LINE_SPACING = "receipt_line_spacing_dots"

    const val DEFAULT_SHOP_NAME = "مدير المولدات"

    /**
     * تباعد الأسطر بالفاتورة الحرارية (بالنقاط) — يُرسَل كأمر ESC/POS "ESC 3 n" فيتحكم
     * بالمسافة العمودية بين كل سطر والذي يليه على الوصل. القيمة الافتراضية (30) تقارب
     * التباعد المعتاد لأغلب الطابعات الحرارية 203dpi. تقليل الرقم يقرّب الأسطر من بعضها
     * (فاتورة أكثر تراصًّا وتوفيرًا بالورق)، وزيادته يباعد بينها.
     */
    const val LINE_SPACING_COMPACT = 18
    const val LINE_SPACING_NORMAL = 30
    const val LINE_SPACING_WIDE = 45
    const val DEFAULT_LINE_SPACING = LINE_SPACING_NORMAL

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

    /** تباعد أسطر الفاتورة الحرارية بالنقاط (راجع الشرح أعلاه). افتراضيًا [LINE_SPACING_NORMAL] */
    fun getLineSpacing(context: Context): Int =
        prefs(context).getInt(KEY_LINE_SPACING, DEFAULT_LINE_SPACING)

    fun setLineSpacing(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_LINE_SPACING, value.coerceIn(0, 255)).apply()
    }
}
