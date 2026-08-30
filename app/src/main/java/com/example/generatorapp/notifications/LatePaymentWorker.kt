package com.example.generatorapp.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.generatorapp.data.AppDatabase
import com.example.generatorapp.util.DateUtils
import kotlinx.coroutines.flow.first

/**
 * مهمة خلفية (WorkManager) تتحقق يوميًا من عدد المشتركين المتأخرين بالدفع
 * وتُظهر إشعارًا للمستخدم إن كان هناك متأخرون.
 */
class LatePaymentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val allSubscribers = db.subscriberDao().getAll().first()
            val lastPayments = db.invoiceDao().getLastPaymentPerSubscriber().associateBy { it.subscriberId }
            val (monthStart, _) = DateUtils.monthRange(DateUtils.currentYear(), DateUtils.currentMonth())

            val lateCount = allSubscribers.count { sub ->
                val last = lastPayments[sub.id]?.lastDate
                last == null || last < monthStart
            }

            if (lateCount > 0) {
                NotificationHelper.createChannel(applicationContext)
                val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    NotificationHelper.showLateSubscribersNotification(applicationContext, lateCount)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
