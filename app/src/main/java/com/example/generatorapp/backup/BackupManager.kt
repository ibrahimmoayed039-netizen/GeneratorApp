package com.example.generatorapp.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.OpenableColumns
import android.content.ContentUris
import android.database.sqlite.SQLiteDatabase
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
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
    /** اسم ملف النسخة التلقائية (تُستبدل بعد كل عملية بدل ما تتراكم عشرات النسخ) */
    private const val AUTO_FILE_NAME = "auto_latest.db"
    /** ننتظر هذه المدة بعد آخر تعديل قبل أخذ النسخة، حتى لا نكرر النسخ مع عدة عمليات متتالية */
    private const val AUTO_DEBOUNCE_MS = 1500L
    private val snapshotLock = Any()
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
    suspend fun createBackup(context: Context): File = withContext(Dispatchers.IO) {
        val fileName = "backup_${fileNameFormat.format(Date())}.db"
        val destFile = File(backupsDir(context), fileName)
        snapshotDatabase(context, destFile)

        cleanupOldBackups(context)

        // ننسخها أيضًا لمجلد "التنزيلات" العام تلقائيًا حتى يقدر المستخدم يوصلها من أي
        // تطبيق ملفات عادي على الجهاز. فشل هذه الخطوة لا يوقف عملية النسخ الاحتياطي نفسها.
        copyToDownloads(context, destFile)
        writeToExternalTarget(context, destFile)
        uploadToDrive(context, destFile)

        destFile
    }

    /**
     * يأخذ لقطة كاملة وسليمة من قاعدة البيانات الحالية داخل [dest].
     * نعمل على نسخة مؤقتة (الملف الرئيسي + ملف الـ WAL إن وُجد) ثم نفتحها ونفرّغ الـ WAL بداخلها
     * ونحوّلها لوضع DELETE، فيكون الناتج ملفًا واحدًا مكتملًا يحوي كل البيانات.
     * هذا يضمن عدم ضياع أحدث العمليات حتى لو فشل الـ checkpoint على القاعدة الحيّة (بسبب قراءات جارية).
     */
    private fun snapshotDatabase(context: Context, dest: File) {
        synchronized(snapshotLock) {
            // محاولة تفريغ الـ WAL على القاعدة الحيّة (لا تضر لو فشلت؛ اللقطة أدناه تعالجها)
            try {
                AppDatabase.getInstance(context).query("PRAGMA wal_checkpoint(FULL)", null)
                    .use { cursor -> cursor.moveToFirst() }
            } catch (_: Exception) { }

            val dbFile = context.getDatabasePath(DB_NAME)
            val tmpDir = File(context.cacheDir, "backup_tmp")
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            val tmpDb = File(tmpDir, DB_NAME)
            try {
                dbFile.copyTo(tmpDb, overwrite = true)
                val wal = File(dbFile.path + "-wal")
                if (wal.exists()) wal.copyTo(File(tmpDb.path + "-wal"), overwrite = true)

                val sq = SQLiteDatabase.openDatabase(tmpDb.path, null, SQLiteDatabase.OPEN_READWRITE)
                try {
                    sq.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    sq.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
                } finally {
                    sq.close()
                }
                tmpDb.copyTo(dest, overwrite = true)
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }

    // ======================= النسخ التلقائي بعد كل عملية =======================

    private val autoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    @Volatile private var autoStarted = false
    @Volatile private var autoPaused = false

    /**
     * يراقب أي تعديل على أي جدول (إضافة/تعديل/حذف مشترك، فاتورة، مصروف...) وبعد كل تعديل
     * (بعد مهلة قصيرة تجمع العمليات المتلاحقة) يحدّث النسخة التلقائية [AUTO_FILE_NAME]
     * داخل مجلد النسخ وفي التنزيلات. يُستدعى مرة واحدة من [com.example.generatorapp.GeneratorApp].
     */
    @OptIn(FlowPreview::class)
    fun startAutoBackup(context: Context) {
        if (autoStarted) return
        autoStarted = true
        val app = context.applicationContext

        autoScope.launch {
            try {
                AppDatabase.getInstance(app).invalidationTracker.addObserver(
                    object : InvalidationTracker.Observer(
                        "subscribers", "generators", "subscriptions", "invoices", "expenses",
                        "ampere_change_logs", "generator_hour_logs", "maintenance_items",
                        "fault_logs", "price_change_logs", "employees"
                    ) {
                        override fun onInvalidated(tables: Set<String>) {
                            changeSignal.tryEmit(Unit)
                        }
                    }
                )
            } catch (_: Exception) { }
        }

        autoScope.launch {
            changeSignal.debounce(AUTO_DEBOUNCE_MS).collect {
                if (autoPaused) return@collect
                try {
                    createAutoBackup(app)
                } catch (_: Exception) { }
            }
        }
    }

    /** يحدّث ملف النسخة التلقائية الوحيد (يُستبدل في كل مرة) */
    private fun createAutoBackup(context: Context) {
        val dest = File(backupsDir(context), AUTO_FILE_NAME)
        snapshotDatabase(context, dest)
        copyToDownloads(context, dest, overwrite = true)
        writeToExternalTarget(context, dest)
        uploadToDrive(context, dest)
    }

    // ======================= الرفع إلى Google Drive (عبر Drive API) =======================

    /** يرفع ملف النسخة لـ Drive (لو الربط مفعّل). لو فشل (غالبًا بسبب الإنترنت) تُجدول إعادة محاولة. */
    private fun uploadToDrive(context: Context, file: File) {
        if (!DriveBackup.isEnabled(context)) return
        val bytes = synchronized(snapshotLock) { file.readBytes() }
        if (!DriveBackup.upload(context, bytes)) scheduleDriveRetry(context)
    }

    /** يرفع آخر نسخة تلقائية موجودة (يستخدمه Worker إعادة المحاولة) */
    fun uploadLatestToDrive(context: Context): Boolean {
        val file = File(backupsDir(context), AUTO_FILE_NAME)
        if (!file.exists()) return true
        val bytes = synchronized(snapshotLock) { file.readBytes() }
        return DriveBackup.upload(context, bytes)
    }

    private fun scheduleDriveRetry(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("drive_upload_retry", ExistingWorkPolicy.REPLACE, request)
        } catch (_: Exception) { }
    }

    /** ملف داخل مجلد النسخ لنسخة نُزّلت من Drive قبل تأكيد استعادتها */
    fun newDriveRestoreFile(context: Context): File =
        File(backupsDir(context), "drive_${fileNameFormat.format(Date())}.db")

    // ================= ملف الحفظ التلقائي المختار (Google Drive أو أي مكان) =================

    private const val PREFS = "backup_external_target"
    private const val KEY_URI = "uri"
    private const val KEY_STATUS = "status"

    fun getExternalTarget(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URI, null)?.let { Uri.parse(it) }

    /** يحفظ الملف الذي اختاره المستخدم (مثلًا داخل Drive) كوجهة لكل نسخة تلقائية. null = إلغاء الربط */
    fun setExternalTarget(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        getExternalTarget(context)?.let { old ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
        }
        if (uri == null) {
            prefs.edit().remove(KEY_URI).remove(KEY_STATUS).apply()
            return
        }
        // إذن دائم حتى يبقى التطبيق قادرًا على الكتابة بعد إغلاقه وإعادة تشغيل الجهاز
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) { }
        prefs.edit().putString(KEY_URI, uri.toString()).remove(KEY_STATUS).apply()
    }

    /** اسم الملف المختار للعرض بالواجهة */
    fun externalTargetName(context: Context): String? {
        val uri = getExternalTarget(context) ?: return null
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment
        } catch (_: Exception) { uri.lastPathSegment }
    }

    /** آخر نتيجة كتابة على الملف المختار (نجاح بوقته أو سبب الفشل) */
    fun externalTargetStatus(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATUS, null)

    /** يستبدل محتوى الملف المختار بآخر نسخة. فشله لا يوقف باقي عملية النسخ. */
    fun writeToExternalTarget(context: Context, source: File): Boolean {
        val uri = getExternalTarget(context) ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("تعذّر فتح الملف")
            prefs.edit().putString(KEY_STATUS, "آخر حفظ ناجح: " + displayFormat.format(Date())).apply()
            true
        } catch (e: Exception) {
            prefs.edit().putString(KEY_STATUS, "فشل الحفظ في الملف المختار، اختر الملف من جديد (" + (e.message ?: "") + ")").apply()
            false
        }
    }

    private fun isAuto(file: File) = file.name == AUTO_FILE_NAME

    private fun cleanupOldBackups(context: Context) {
        val backups = listBackups(context).filter { !isAuto(it) }
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
    fun displayName(file: File): String =
        (if (isAuto(file)) "تلقائية - " else "") + displayFormat.format(Date(file.lastModified()))

    /**
     * يستبدل قاعدة البيانات الحالية بنسخة احتياطية معيّنة.
     * يجب إعادة تشغيل التطبيق بالكامل بعد الاستعادة (نُعيد فتح النشاط تلقائيًا من الواجهة).
     */
    fun restoreBackup(context: Context, backupFile: File): Boolean {
        // نوقف النسخ التلقائي أثناء الاستعادة حتى لا يكتب فوق النسخة أو يعيد فتح القاعدة
        autoPaused = true
        return try {
            synchronized(snapshotLock) {
                AppDatabase.closeInstance()
                val dbFile = context.getDatabasePath(DB_NAME)
                // نحذف ملفات الـ WAL/SHM/journal القديمة قبل وضع الملف المستعاد حتى لا تتعارض معه
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                File(dbFile.path + "-journal").delete()
                backupFile.copyTo(dbFile, overwrite = true)
            }
            true
        } catch (e: Exception) {
            autoPaused = false
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
    fun copyToDownloads(context: Context, backupFile: File, overwrite: Boolean = false): Boolean {
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
                var existingId: Long? = null
                existing?.use { if (it.moveToFirst()) existingId = it.getLong(0) }
                if (existingId != null) {
                    if (!overwrite) return true // نفس النسخة محفوظة أصلاً بالتنزيلات
                    // نستبدل محتوى الملف الموجود بدل إنشاء نسخة جديدة في كل مرة
                    val existingUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, existingId!!)
                    val replaced = try {
                        resolver.openOutputStream(existingUri, "wt")?.use { out ->
                            backupFile.inputStream().use { input -> input.copyTo(out) }
                            true
                        } ?: false
                    } catch (e: Exception) { false }
                    if (replaced) return true
                    // لو الملف تابع لتثبيت قديم ولا نقدر نعدّله، نكمل وننشئ ملفًا جديدًا
                }

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
