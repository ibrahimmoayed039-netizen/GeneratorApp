package com.example.generatorapp.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.generatorapp.data.AppDatabase
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.messaging.MessageComposer
import com.example.generatorapp.messaging.SmsSender
import com.example.generatorapp.util.DateUtils
import kotlinx.coroutines.flow.first

/**
 * مهمة خلفية (WorkManager) تتحقق يوميًا من عدد المشتركين المتأخرين بالدفع
 * وتُظهر إشعارًا للمستخدم إن كان هناك متأخرون. إذا كان "الإرسال التلقائي بـ SMS"
 * مفعّلًا من الإعدادات وصلاحية SEND_SMS ممنوحة، يرسل أيضًا رسالة تذكير فعلية
 * لكل مشترك متأخر تلقائيًا بدون أي تدخل من المستخدم.
 */
class LatePaymentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val allSubscribers = db.subscriberDao().getAll().first()
            val lastPayments = db.invoiceDao().getLastPaymentPerSubscriber().associateBy { it.subscriberId }
            val (monthStart, _) = DateUtils.monthRange(DateUtils.currentYear(), DateUtils.currentMonth())

            val lateSubscribers: List<Subscriber> = allSubscribers.filter { sub ->
                val last = lastPayments[sub.id]?.lastDate
                last == null || last < monthStart
            }

            if (lateSubscribers.isNotEmpty()) {
                NotificationHelper.createChannel(applicationContext)
                val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                if (hasNotificationPermission) {
                    NotificationHelper.showLateSubscribersNotification(applicationContext, lateSubscribers.size)
                }

                if (MessageSettings.isSmsFeatureEnabled(applicationContext) &&
                    MessageSettings.isAutoSmsEnabled(applicationContext) &&
                    SmsSender.hasPermission(applicationContext)
                ) {
                    val template = MessageSettings.getLateTemplate(applicationContext)
                    lateSubscribers.forEach { sub ->
                        SmsSender.sendSms(applicationContext, sub.phone, MessageComposer.lateReminder(sub, template))
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
