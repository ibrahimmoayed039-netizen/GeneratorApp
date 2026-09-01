package com.example.generatorapp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "late_payments_channel"
    const val MAINTENANCE_CHANNEL_ID = "maintenance_channel"
    private const val NOTIFICATION_ID = 1001
    private const val MAINTENANCE_NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنبيهات المتأخرين بالدفع",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيه يومي بعدد المشتركين المتأخرين عن الدفع هذا الشهر"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun createMaintenanceChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MAINTENANCE_CHANNEL_ID,
                "تنبيهات صيانة المولدات",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيه يومي بمواعيد تغيير الزيت والصيانة الدورية المستحقة أو القريبة"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    fun showLateSubscribersNotification(context: Context, count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("مشتركون متأخرون بالدفع")
            .setContentText("يوجد $count مشترك بدون فاتورة مسجّلة هذا الشهر")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    fun showMaintenanceDueNotification(context: Context, dueCount: Int, soonCount: Int) {
        val text = buildString {
            if (dueCount > 0) append("$dueCount بند صيانة مستحق الآن")
            if (dueCount > 0 && soonCount > 0) append(" - ")
            if (soonCount > 0) append("$soonCount قريب من الاستحقاق")
        }
        val notification = NotificationCompat.Builder(context, MAINTENANCE_CHANNEL_ID)
            .setContentTitle("تنبيه صيانة مولدات")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(MAINTENANCE_NOTIFICATION_ID, notification)
    }
}
