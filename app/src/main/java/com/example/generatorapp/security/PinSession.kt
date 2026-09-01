package com.example.generatorapp.security

/**
 * جلسة PIN بسيطة تعيش بالذاكرة فقط (ما تُحفظ على القرص عمدًا لأسباب أمنية).
 * بعد أول مرة يدخل فيها الموظف رمز PIN صحيح، تبقى الجلسة "مفتوحة" ولا يُطلب الرمز
 * مرة ثانية إلا بعد إغلاق التطبيق بالكامل (تصفير العملية Process) أو الضغط على "قفل الجلسة"
 * يدويًا من شاشة الإعدادات.
 */
object PinSession {
    var unlocked: Boolean = false
        private set

    fun unlock() {
        unlocked = true
    }

    /** يقفل الجلسة يدويًا (مثلاً عند تسليم الوردية لموظف آخر) */
    fun lock() {
        unlocked = false
    }
}
