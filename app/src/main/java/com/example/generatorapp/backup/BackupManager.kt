package com.example.generatorapp.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.generatorapp.data.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يدير النسخ الاحتياطي الكامل لقاعدة بيانات التطبيق:
 * - إنشاء نسخة جديدة (يدويًا أو تلقائيًا عبر [com.example.generatorapp.backup.BackupWorker])
 * - عرض كل النسخ المخزّنة محليًا
 * - استعادة نسخة قديمة
 * - مشاركة نسخة عبر أي تطبيق (واتساب، بريد، درايف، تيليجرام...) لحفظها خارج الجهاز
 * - حذف النسخ الزائدة تلقائيًا (يحتفظ بآخر [MAX_BACKUPS] فقط)
 *
 * تُخزَّن النسخ داخل تخزين التطبيق الخاص بالمساحة الخارجية
 * (Android/data/<package>/files/backups) — لا يحتاج أي صلاحية وصول للتخزين.
 */
object BackupManager {
    private const val DB_NAME = "generator_app.db"
    private const val BACKUP_DIR = "backups"
    private const val MAX_BACKUPS = 10
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale("ar"))

    private fun backupsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * ينشئ نسخة احتياطية جديدة من قاعدة البيانات الحالية.
     * يُفرَّغ الـ WAL بالكامل داخل الملف الرئيسي أولًا لضمان عدم فقدان أحدث البيانات.
     */
    suspend fun createBackup(context: Context): File {
        val db = AppDatabase.getInstance(context)
        db.query("PRAGMA wal_checkpoint(FULL)", null).use { }

        val dbFile = context.getDatabasePath(DB_NAME)
        val fileName = "backup_${fileNameFormat.format(Date())}.db"
        val destFile = File(backupsDir(context), fileName)
        dbFile.copyTo(destFile, overwrite = true)

        cleanupOldBackups(context)
        return destFile
    }

    private fun cleanupOldBackups(context: Context) {
        val backups = listBackups(context)
        if (backups.size > MAX_BACKUPS) {
            backups.drop(MAX_BACKUPS).forEach { it.delete() }
        }
    }

    /** كل النسخ الاحتياطية المخزّنة محليًا، الأحدث أولًا */
    fun listBackups(context: Context): List<File> =
        backupsDir(context).listFiles { f -> f.isFile && f.extension == "db" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** يحوّل اسم ملف النسخة إلى تاريخ ووقت مقروء للعرض بالواجهة */
    fun displayName(file: File): String = displayFormat.format(Date(file.lastModified()))

    /**
     * يستبدل قاعدة البيانات الحالية بنسخة احتياطية معيّنة.
     * يجب إعادة تشغيل التطبيق بالكامل بعد الاستعادة (نُعيد فتح النشاط تلقائيًا من الواجهة).
     */
    fun restoreBackup(context: Context, backupFile: File): Boolean {
        return try {
            AppDatabase.closeInstance()
            val dbFile = context.getDatabasePath(DB_NAME)
            backupFile.copyTo(dbFile, overwrite = true)
            // نحذف ملفات الـ WAL/SHM القديمة حتى لا تتعارض مع الملف المستعاد
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteBackup(file: File): Boolean = file.delete()

    /** يفتح قائمة "مشاركة" النظام لإرسال ملف النسخة الاحتياطية لأي تطبيق آخر (لحفظها خارج الجهاز) */
    fun shareBackup(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
    }
}
