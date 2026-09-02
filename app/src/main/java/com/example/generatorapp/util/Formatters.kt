package com.example.generatorapp.util

/**
 * تنسيق موحّد لعرض المبالغ في كل الشاشات والفواتير والتقارير.
 * إذا كان الرقم صحيحًا (مثلاً 1450) يُعرض بدون كسور: "1450"
 * وإذا كان به كسور (مثلاً 1450.5) يُعرض بخانتين عشريتين: "1450.50"
 * هذا يمنع ظهور "1450.00" عند إدخال رقم صحيح.
 */
object Formatters {
    fun formatMoney(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)
}
