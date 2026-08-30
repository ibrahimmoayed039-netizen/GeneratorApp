package com.example.generatorapp.util

import java.util.Calendar

/**
 * أدوات مساعدة لحساب بدايات ونهايات الأشهر/السنوات بالمللي ثانية (Unix time)
 * تُستخدم في: كشف الحساب الشهري، تقرير الأرباح، والكشف عن المتأخرين بالدفع.
 */
object DateUtils {

    val arabicMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    /** الشهر الحالي بترقيم 1-12 */
    fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    fun monthName(month: Int): String = arabicMonths.getOrElse(month - 1) { "-" }

    /** يرجع (بداية الشهر، نهاية الشهر) بالمللي ثانية. month بترقيم 1-12 */
    fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val end = cal.timeInMillis

        return start to end
    }

    /** يرجع (بداية السنة، نهاية السنة) بالمللي ثانية */
    fun yearRange(year: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return start to end
    }

    fun formatDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        return "$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
    }
}
