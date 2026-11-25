package com.example.generatorapp.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * جلسة PIN تعيش بالذاكرة فقط (لا تُحفظ على القرص عمدًا لأسباب أمنية).
 * بعد إدخال رمز صحيح (رمز المدير أو رمز موظف) تحفظ الجلسة هوية الشخص الذي دخل،
 * ويُسجَّل اسمه على كل دفعة/وصل ينشئها حتى القفل (يدويًا من الإعدادات، أو تلقائيًا بعد
 * كل دفعة إذا فُعّل خيار "طلب الرمز عند كل دفعة"، أو بإغلاق التطبيق).
 */
object PinSession {
    /**
     * هل سجّل المستخدم دخوله عند فتح التطبيق؟ (حالة Compose فتتغيّر الشاشة تلقائيًا عند تسجيل الخروج).
     * منفصلة عن [unlocked] عمدًا: خيار "طلب الرمز عند كل دفعة" يقفل [unlocked] فقط ولا يُخرج المستخدم من التطبيق.
     */
    var loggedIn by mutableStateOf(false)
        private set

    /** يُستدعى بعد نجاح إدخال الرمز في شاشة الدخول */
    fun markLoggedIn() {
        loggedIn = true
    }

    /** تسجيل خروج كامل: يقفل الجلسة ويعيد شاشة إدخال الرمز */
    fun logout() {
        lock()
        loggedIn = false
    }

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
