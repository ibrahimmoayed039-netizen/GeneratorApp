package com.example.generatorapp.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
    /** اسم المجلد الفرعي داخل "التنزيلات" العامة بحيث يقدر المستخدم يوصل لنسخه بأي تطبيق ملفات */
    private const val DOWNLOADS_SUBFOLDER = "GeneratorApp_Backups"
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
        // مهم: لازم نقرأ من الـ Cursor فعليًا (moveToFirst) وإلا أندرويد لا ينفّذ استعلام
        // الـ PRAGMA أصلًا، فيفضل الـ checkpoint ما يحصلش وتضيع أحدث البيانات (لسه بملف WAL
        // ولم تُنقل لملف .db الرئيسي) من النسخة الاحتياطية بصمت من غير أي خطأ ظاهر.
        db.query("PRAGMA wal_checkpoint(FULL)", null).use { cursor -> cursor.moveToFirst() }

        val dbFile = context.getDatabasePath(DB_NAME)
        val fileName = "backup_${fileNameFormat.format(Date())}.db"
        val destFile = File(backupsDir(context), fileName)
        dbFile.copyTo(destFile, overwrite = true)

        cleanupOldBackups(context)

        // ننسخها أيضًا لمجلد "التنزيلات" العام تلقائيًا حتى يقدر المستخدم يوصلها من أي
        // تطبيق ملفات عادي على الجهاز (تخزين التطبيق الخاص مخفي عن أغلب مديري الملفات
        // من أندرويد 11 فما فوق). فشل هذه الخطوة لا يوقف عملية النسخ الاحتياطي نفسها.
        copyToDownloads(context, destFile)

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

    /**
     * يستورد ملف نسخة احتياطية اختاره المستخدم يدويًا (عبر منتقي الملفات) إلى مجلد النسخ
     * الخاص بالتطبيق، حتى تعمل معه بقية الوظائف (استعادة/مشاركة/حذف) بشكل طبيعي.
     * ضروري في حالة مسح بيانات التطبيق أو إعادة تثبيته: عندها يصبح مجلد النسخ الداخلي فارغًا
     * تمامًا (حتى لو كانت هناك نسخة محفوظة سابقًا في "التنزيلات" أو مشاركة عبر واتساب مثلاً)،
     * فيحتاج المستخدم طريقة لاختيار ملف النسخة يدويًا من أي مكان على الجهاز بدل الاعتماد فقط
     * على قائمة النسخ الداخلية.
     */
    fun importBackupFromUri(context: Context, uri: android.net.Uri): File? {
        return try {
            val fileName = "imported_${fileNameFormat.format(Date())}.db"
            val destFile = File(backupsDir(context), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            destFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * ينسخ نسخة احتياطية موجودة (من تخزين التطبيق الخاص) إلى مجلد فرعي داخل "التنزيلات"
     * العامة على الجهاز (Download/GeneratorApp_Backups)، بحيث يقدر المستخدم يوصلها ويشوفها
     * من أي تطبيق ملفات عادي (مثل "الملفات" أو "My Files")، أو ينقلها بسهولة لجهاز آخر.
     * على أندرويد 10 فما فوق تُستخدم MediaStore (لا تحتاج أي صلاحية)، وعلى الإصدارات الأقدم
     * تُنسخ مباشرة لمجلد التنزيلات العام (يحتاج صلاحية WRITE_EXTERNAL_STORAGE وقت التشغيل).
     */
    fun copyToDownloads(context: Context, backupFile: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val existing = resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf("Download/$DOWNLOADS_SUBFOLDER/", backupFile.name),
                    null
                )
                val alreadyExists = existing?.use { it.count > 0 } ?: false
                if (alreadyExists) return true // نفس النسخة محفوظة أصلاً بالتنزيلات

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, backupFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$DOWNLOADS_SUBFOLDER")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    backupFile.inputStream().use { input -> input.copyTo(out) }
                } ?: return false
                true
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    DOWNLOADS_SUBFOLDER
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                backupFile.copyTo(File(downloadsDir, backupFile.name), overwrite = true)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /** المسار المعروض للمستخدم لمكان حفظ النسخ داخل التنزيلات، ليظهر برسالة إرشادية بالواجهة */
    fun downloadsFolderLabel(): String = "التنزيلات/$DOWNLOADS_SUBFOLDER"

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
