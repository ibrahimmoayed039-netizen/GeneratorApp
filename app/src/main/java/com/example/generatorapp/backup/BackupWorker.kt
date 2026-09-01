package com.example.generatorapp.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * مهمة خلفية (WorkManager) تنشئ نسخة احتياطية تلقائية يوميًا من قاعدة البيانات،
 * تعمل حتى لو كان التطبيق مغلقًا تمامًا.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            BackupManager.createBackup(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
