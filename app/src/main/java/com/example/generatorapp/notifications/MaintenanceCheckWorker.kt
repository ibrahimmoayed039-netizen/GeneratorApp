package com.example.generatorapp.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.generatorapp.data.AppDatabase
import kotlinx.coroutines.flow.first

/**
 * مهمة خلفية (WorkManager) تتحقق يوميًا من بنود الصيانة (تغيير زيت / صيانة دورية) المستحقة
 * أو القريبة من الاستحقاق بناءً على ساعات تشغيل كل مولد وعدد الأيام منذ آخر خدمة (أيهما أسبق)،
 * وتُظهر إشعارًا إن وجدت.
 */
class MaintenanceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val generators = db.generatorDao().getAll().first().associateBy { it.id }
            val items = db.maintenanceItemDao().getAll().first()

            var dueCount = 0
            var soonCount = 0
            val now = System.currentTimeMillis()
            items.forEach { item ->
                val currentHours = generators[item.generatorId]?.currentHours ?: return@forEach
                val hoursSinceService = (currentHours - item.lastServiceHours).coerceAtLeast(0.0)
                val hoursRemaining = item.intervalHours - hoursSinceService

                val hasDaySchedule = item.intervalDays > 0
                val daysSinceService = (now - item.lastServiceDate).coerceAtLeast(0) / (24L * 60 * 60 * 1000)
                val daysRemaining = if (hasDaySchedule) item.intervalDays - daysSinceService else Long.MAX_VALUE

                val isDue = hoursRemaining <= 0.0 || (hasDaySchedule && daysRemaining <= 0)
                val isSoon = !isDue && (
                    hoursRemaining <= (item.intervalHours * 0.1).coerceAtMost(25.0) ||
                        (hasDaySchedule && daysRemaining <= (item.intervalDays * 0.1).coerceAtMost(7.0))
                    )

                when {
                    isDue -> dueCount++
                    isSoon -> soonCount++
                }
            }

            if (dueCount > 0 || soonCount > 0) {
                NotificationHelper.createMaintenanceChannel(applicationContext)
                val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    NotificationHelper.showMaintenanceDueNotification(applicationContext, dueCount, soonCount)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
