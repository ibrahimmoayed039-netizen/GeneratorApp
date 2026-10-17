package com.example.generatorapp.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * يعيد محاولة رفع آخر نسخة إلى Drive عندما فشل الرفع (مثلاً لا يوجد إنترنت).
 * يُجدول بشرط توفّر اتصال، فيعمل تلقائيًا حالما يرجع الإنترنت حتى لو أُغلق التطبيق.
 */
class DriveUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!DriveBackup.isEnabled(applicationContext)) return Result.success()
        val ok = BackupManager.uploadLatestToDrive(applicationContext)
        return when {
            ok -> Result.success()
            runAttemptCount >= 5 -> Result.failure()
            else -> Result.retry()
        }
    }
}
