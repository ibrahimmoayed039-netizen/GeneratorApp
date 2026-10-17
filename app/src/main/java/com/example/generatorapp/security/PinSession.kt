package com.example.generatorapp.security

/**
 * جلسة PIN تعيش بالذاكرة فقط (لا تُحفظ على القرص عمدًا لأسباب أمنية).
 * بعد إدخال رمز صحيح (رمز المدير أو رمز موظف) تحفظ الجلسة هوية الشخص الذي دخل،
 * ويُسجَّل اسمه على كل دفعة/وصل ينشئها حتى القفل (يدويًا من الإعدادات، أو تلقائيًا بعد
 * كل دفعة إذا فُعّل خيار "طلب الرمز عند كل دفعة"، أو بإغلاق التطبيق).
 */
object PinSession {
    var unlocked: Boolean = false
        private set

    /** معرّف الموظف الحالي (0 = المدير) */
    var employeeId: Long = 0
        private set

    /** اسم من سجّل الدخول حاليًا ويظهر على الفواتير */
    var employeeName: String = ""
        private set

    fun unlock(id: Long, name: String) {
        unlocked = true
        employeeId = id
        employeeName = name
    }

    /** يقفل الجلسة (مثلاً عند تسليم الوردية لموظف آخر) */
    fun lock() {
        unlocked = false
        employeeId = 0
        employeeName = ""
    }
}
