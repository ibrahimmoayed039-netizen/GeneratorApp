package com.example.generatorapp

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.generatorapp.notifications.MaintenanceCheckWorker
import com.example.generatorapp.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

/** اسم مهمة الفحص اليومي لبنود الصيانة (نفس الاسم المستخدم في شاشة الإعدادات) */
const val MAINTENANCE_WORK_NAME = "maintenance_daily_check"

private const val PREFS_NAME = "maintenance_alert_settings"
private const val KEY_USER_DISABLED = "maintenance_alerts_disabled_by_user"

/**
 * يتتبع هل المستخدم أوقف تنبيهات الصيانة يدويًا من شاشة الإعدادات، حتى لا تتم
 * إعادة تفعيلها تلقائيًا في كل مرة يفتح فيها التطبيق (نحترم اختياره).
 */
object MaintenanceAlertPrefs {
    fun setUserDisabled(context: Context, disabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USER_DISABLED, disabled).apply()
    }

    fun isUserDisabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_DISABLED, false)
}

/**
 * نقطة انطلاق التطبيق. تُستخدم هنا لجدولة فحص الصيانة اليومي تلقائيًا
 * من أول ما يفتح المستخدم التطبيق، بدون الحاجة للدخول لشاشة الإعدادات وتفعيل المفتاح يدويًا.
 *
 * ملاحظة: الجدولة هنا لا تحتاج صلاحية إشعارات، فقط عرض الإشعار نفسه يحتاجها،
 * وهذا يُتحقق منه داخل MaintenanceCheckWorker قبل عرض أي إشعار.
 * صلاحية الإشعارات (على أندرويد 13 فأعلى) يتم طلبها من المستخدم عند فتح الشاشة الرئيسية لأول مرة.
 */
class GeneratorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createMaintenanceChannel(this)
        if (!MaintenanceAlertPrefs.isUserDisabled(this)) {
            scheduleMaintenanceWorkerOnStartup()
        }
    }

    private fun scheduleMaintenanceWorkerOnStartup() {
        val request = PeriodicWorkRequestBuilder<MaintenanceCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MAINTENANCE_WORK_NAME,
            // KEEP: لو فيه مهمة مجدولة مسبقًا ما تنعاد جدولتها من الصفر في كل مرة يفتح فيها التطبيق
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
