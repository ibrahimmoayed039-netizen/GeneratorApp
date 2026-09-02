package com.example.generatorapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.example.generatorapp.MaintenanceAlertPrefs
import com.example.generatorapp.backup.BackupManager
import com.example.generatorapp.backup.BackupWorker
import com.example.generatorapp.notifications.LatePaymentWorker
import com.example.generatorapp.notifications.MaintenanceCheckWorker
import com.example.generatorapp.notifications.MessageSettings
import com.example.generatorapp.notifications.NotificationHelper
import com.example.generatorapp.printing.LogoManager
import com.example.generatorapp.printing.ReceiptPrintManager
import com.example.generatorapp.security.PinManager
import com.example.generatorapp.security.PinSession
import com.example.generatorapp.ui.components.PrinterDiscoveryDialog
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

private const val LATE_PAYMENT_WORK_NAME = "late_payment_daily_check"
private const val BACKUP_WORK_NAME = "daily_auto_backup"
// اسم مهمة فحص الصيانة اليومي مُعرَّف مركزيًا في GeneratorApp.kt لأنه يُستخدم أيضًا
// عند جدولة المهمة تلقائيًا لحظة فتح التطبيق (وليس فقط من هذه الشاشة)
private val MAINTENANCE_WORK_NAME = com.example.generatorapp.MAINTENANCE_WORK_NAME

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logoBitmap by remember { mutableStateOf<Bitmap?>(LogoManager.loadLogo(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var maintenanceNotificationsEnabled by remember { mutableStateOf(false) }

    // ---------- رسائل التذكير (واتساب/SMS) ----------
    var smsFeatureEnabled by remember { mutableStateOf(MessageSettings.isSmsFeatureEnabled(context)) }
    var autoSmsEnabled by remember { mutableStateOf(MessageSettings.isAutoSmsEnabled(context)) }
    var lateTemplate by remember { mutableStateOf(MessageSettings.getLateTemplate(context)) }
    var priceTemplate by remember { mutableStateOf(MessageSettings.getPriceTemplate(context)) }
    var countryCode by remember { mutableStateOf(MessageSettings.getCountryCode(context)) }
    var messagingSettingsSaved by remember { mutableStateOf<String?>(null) }

    val requestSmsPermissionForAuto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            MessageSettings.setAutoSmsEnabled(context, true)
            autoSmsEnabled = true
            messagingSettingsSaved = "تم تفعيل الإرسال التلقائي بـ SMS للمتأخرين"
        } else {
            messagingSettingsSaved = "لازم تسمح بصلاحية الرسائل النصية (SMS) لتفعيل هذه الميزة"
        }
    }

    // نتحقق من الحالة الفعلية لمهمة تنبيهات الصيانة المجدولة عند فتح الشاشة
    LaunchedEffect(Unit) {
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(MAINTENANCE_WORK_NAME).await()
        maintenanceNotificationsEnabled = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    }

    // ---------- تشخيص الطابعة الحرارية (اختبار جداول الحروف) ----------
    var charsetTestWidth by remember { mutableStateOf(32) } // 32 لـ58مم، 48 لـ80مم
    var charsetTestInProgress by remember { mutableStateOf(false) }
    var charsetTestMessage by remember { mutableStateOf<String?>(null) }
    var showPrinterScan by remember { mutableStateOf(false) }

    fun runCharsetTest(device: android.bluetooth.BluetoothDevice) {
        showPrinterScan = false
        charsetTestInProgress = true
        charsetTestMessage = null
        scope.launch {
            try {
                ReceiptPrintManager.printCharsetTestPage(device, charsetTestWidth)
                charsetTestMessage = "تم إرسال صفحة اختبار جداول الحروف للطابعة"
            } catch (e: Exception) {
                charsetTestMessage = e.message ?: "فشل الاتصال بالطابعة"
            } finally {
                charsetTestInProgress = false
            }
        }
    }

    // صلاحيات البحث الفعلي عن أجهزة قريبة: BLUETOOTH_SCAN + BLUETOOTH_CONNECT بدءًا من أندرويد 12،
    // أو ACCESS_FINE_LOCATION فيما قبل ذلك (شرط أساسي لاكتشاف البلوتوث الكلاسيكي على أندرويد القديم)
    val requestScanPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            showPrinterScan = true
        } else {
            charsetTestMessage = "لازم تسمح بصلاحيات البلوتوث والموقع للبحث عن طابعات قريبة"
        }
    }

    fun openPrinterScan() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            showPrinterScan = true
        } else {
            requestScanPermissions.launch(missing.toTypedArray())
        }
    }

    // ---------- النسخ الاحتياطي ----------
    var autoBackupEnabled by remember { mutableStateOf(false) }
    var backupList by remember { mutableStateOf(BackupManager.listBackups(context)) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupInProgress by remember { mutableStateOf(false) }
    var confirmRestoreFile by remember { mutableStateOf<File?>(null) }

    fun refreshBackups() {
        backupList = BackupManager.listBackups(context)
    }

    // نتحقق من الحالة الفعلية لمهمة النسخ الاحتياطي المجدولة عند فتح الشاشة
    LaunchedEffect(Unit) {
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(BACKUP_WORK_NAME).await()
        autoBackupEnabled = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    }

    // رمز PIN الموحّد الذي يطلبه التطبيق من الموظف قبل تسجيل أي دفعة
    var currentPin by remember { mutableStateOf(PinManager.getPin(context)) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    val requestNotificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scheduleLatePaymentWorker(context)
            notificationsEnabled = true
            message = "تم تفعيل تنبيهات المتأخرين اليومية"
        } else {
            message = "لازم تسمح بالإشعارات لتفعيل هذه الميزة"
        }
    }

    val requestMaintenanceNotificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scheduleMaintenanceWorker(context)
            MaintenanceAlertPrefs.setUserDisabled(context, false)
            maintenanceNotificationsEnabled = true
            message = "تم تفعيل تنبيهات الصيانة اليومية"
        } else {
            message = "لازم تسمح بالإشعارات لتفعيل هذه الميزة"
        }
    }

    // منتقي صور حديث (Photo Picker) لا يحتاج أي صلاحية وقت التشغيل
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = LogoManager.saveLogo(context, uri)
            if (saved) {
                logoBitmap = LogoManager.loadLogo(context)
                message = "تم حفظ الشعار بنجاح"
            } else {
                message = "تعذّر حفظ الشعار، حاول بصورة أخرى"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات المحل") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("شعار المحل", style = MaterialTheme.typography.titleMedium)
            Text(
                "يظهر هذا الشعار أعلى كل وصل يُطبع، سواء عبر الطابعة الحرارية أو الطباعة العادية",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap!!.asImageBitmap(),
                        contentDescription = "الشعار الحالي",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Card(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا يوجد شعار", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    pickImage.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (logoBitmap == null) "اختيار شعار" else "تغيير الشعار") }

            if (logoBitmap != null) {
                OutlinedButton(
                    onClick = {
                        LogoManager.deleteLogo(context)
                        logoBitmap = null
                        message = "تم حذف الشعار"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حذف الشعار") }
            }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("تشخيص الطابعة الحرارية", style = MaterialTheme.typography.titleMedium)
            Text(
                "إذا كانت اللغة العربية تطبع بشكل مشوّه على الطابعة الحرارية، اضغط الزر بالأسفل: " +
                    "تطبع الطابعة ورقة طويلة فيها نفس الجملة العربية مكررة تحت كل رقم جدول حروف " +
                    "(Code Page) من CP0 إلى CP47 وأيضًا CP255. لاحظ الرقم الذي تظهر تحته الجملة " +
                    "سليمة، واستخدم هذا الرقم كقيمة codePage عند إعداد الطابعة.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = charsetTestWidth == 32,
                    onClick = { charsetTestWidth = 32 },
                    label = { Text("طابعة 58مم") }
                )
                FilterChip(
                    selected = charsetTestWidth == 48,
                    onClick = { charsetTestWidth = 48 },
                    label = { Text("طابعة 80مم") }
                )
            }

            Button(
                enabled = !charsetTestInProgress,
                onClick = { openPrinterScan() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (charsetTestInProgress) "جارٍ الطباعة..."
                    else "🔍 بحث عن طابعات قريبة واختبار جداول الحروف"
                )
            }

            charsetTestMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("تنبيهات المتأخرين بالدفع", style = MaterialTheme.typography.titleMedium)
            Text(
                "عند التفعيل، يفحص التطبيق يوميًا وجود مشتركين بدون فاتورة هذا الشهر ويرسل إشعارًا بذلك",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تفعيل التنبيهات اليومية")
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED

                            if (needsPermission) {
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                NotificationHelper.createChannel(context)
                                scheduleLatePaymentWorker(context)
                                notificationsEnabled = true
                                message = "تم تفعيل تنبيهات المتأخرين اليومية"
                            }
                        } else {
                            WorkManager.getInstance(context).cancelUniqueWork(LATE_PAYMENT_WORK_NAME)
                            notificationsEnabled = false
                            message = "تم إيقاف التنبيهات اليومية"
                        }
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("رسائل تذكير المتأخرين وسعر الأمبير (واتساب/SMS)", style = MaterialTheme.typography.titleMedium)
            Text(
                "واتساب متاح دائمًا (يفتح المحادثة برسالة جاهزة). ميزة SMS اختيارية بالكامل " +
                    "ومعطّلة افتراضيًا — فعّلها فقط إذا تريد إرسال رسائل نصية فعلية عبر شريحة الاتصال.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("تفعيل ميزة SMS")
                    Text(
                        "إذا معطّلة: تختفي كل أزرار SMS من التطبيق ولا يُطلب أي إذن رسائل نصية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = smsFeatureEnabled,
                    onCheckedChange = { checked ->
                        MessageSettings.setSmsFeatureEnabled(context, checked)
                        smsFeatureEnabled = checked
                        if (!checked) autoSmsEnabled = false
                        messagingSettingsSaved =
                            if (checked) "تم تفعيل ميزة SMS" else "تم تعطيل ميزة SMS بالكامل"
                    }
                )
            }

            Text(
                "عدّل نص الرسائل حسب أسلوبك، فيها متغيرات تُستبدل تلقائيًا: {name} اسم المشترك، " +
                    "{month} الشهر الحالي، {price} سعر الأمبير، {generator} اسم المولدة، {type} نوع الاشتراك. " +
                    "الإرسال والتحكم بالقوائم من شاشة \"رسائل وتذكيرات\" بالصفحة الرئيسية.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = lateTemplate,
                onValueChange = { lateTemplate = it },
                label = { Text("قالب رسالة تذكير المتأخرين") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = priceTemplate,
                onValueChange = { priceTemplate = it },
                label = { Text("قالب رسالة سعر الأمبير الشهري") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = countryCode,
                onValueChange = { countryCode = it.filter { c -> c.isDigit() } },
                label = { Text("رمز الدولة (بدون +) — لتطبيع الأرقام عند فتح واتساب، مثال: 964") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    MessageSettings.setLateTemplate(context, lateTemplate)
                    MessageSettings.setPriceTemplate(context, priceTemplate)
                    MessageSettings.setCountryCode(context, countryCode)
                    messagingSettingsSaved = "تم حفظ إعدادات الرسائل"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ إعدادات الرسائل") }

            if (smsFeatureEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إرسال SMS تلقائي يوميًا للمتأخرين")
                        Text(
                            "يرسل رسالة SMS فعلية (بدون فتح أي تطبيق) لكل مشترك متأخر عند الفحص اليومي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSmsEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val needsPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.SEND_SMS
                                ) != PackageManager.PERMISSION_GRANTED

                                if (needsPermission) {
                                    requestSmsPermissionForAuto.launch(Manifest.permission.SEND_SMS)
                                } else {
                                    MessageSettings.setAutoSmsEnabled(context, true)
                                    autoSmsEnabled = true
                                    messagingSettingsSaved = "تم تفعيل الإرسال التلقائي بـ SMS للمتأخرين"
                                }
                            } else {
                                MessageSettings.setAutoSmsEnabled(context, false)
                                autoSmsEnabled = false
                                messagingSettingsSaved = "تم إيقاف الإرسال التلقائي بـ SMS"
                            }
                        }
                    )
                }
            }

            Text(
                "ملاحظة: واتساب لا يسمح بإرسال تلقائي كامل دون فتح التطبيق وضغط زر الإرسال يدويًا " +
                    "(قيد من واتساب نفسه)، لذلك الإرسال التلقائي بالخلفية متاح فقط عبر SMS (اختياري). " +
                    "رسائل واتساب تُفتح جاهزة من شاشة \"رسائل وتذكيرات\" وتحتاج ضغطة إرسال واحدة لكل مشترك.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            messagingSettingsSaved?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("تنبيهات صيانة المولدات", style = MaterialTheme.typography.titleMedium)
            Text(
                "عند التفعيل، يفحص التطبيق يوميًا بنود الصيانة (تغيير زيت / صيانة دورية) المستحقة " +
                    "أو القريبة من الاستحقاق حسب ساعات تشغيل كل مولد، ويرسل إشعارًا بذلك",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تفعيل تنبيهات الصيانة اليومية")
                Switch(
                    checked = maintenanceNotificationsEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED

                            if (needsPermission) {
                                requestMaintenanceNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                NotificationHelper.createMaintenanceChannel(context)
                                scheduleMaintenanceWorker(context)
                                MaintenanceAlertPrefs.setUserDisabled(context, false)
                                maintenanceNotificationsEnabled = true
                                message = "تم تفعيل تنبيهات الصيانة اليومية"
                            }
                        } else {
                            WorkManager.getInstance(context).cancelUniqueWork(MAINTENANCE_WORK_NAME)
                            MaintenanceAlertPrefs.setUserDisabled(context, true)
                            maintenanceNotificationsEnabled = false
                            message = "تم إيقاف تنبيهات الصيانة اليومية"
                        }
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("رمز PIN للموظف (تسجيل الدفعات)", style = MaterialTheme.typography.titleMedium)
            Text(
                "هذا الرمز يطلبه التطبيق من أي موظف مرة واحدة فقط بعد فتح التطبيق " +
                    "(لأول عملية دفع)، وبعدها ما يُطلب مرة ثانية إلا بعد إغلاق التطبيق بالكامل " +
                    "أو الضغط على \"قفل الجلسة\" بالأسفل. الرمز الحالي: $currentPin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = newPin,
                onValueChange = { if (it.length <= 8) newPin = it },
                label = { Text("رمز PIN جديد") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 8) confirmPin = it },
                label = { Text("تأكيد الرمز الجديد") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    when {
                        newPin.isBlank() -> pinMessage = "أدخل رمز PIN جديد"
                        newPin.length < 4 -> pinMessage = "يفضّل أن يكون الرمز 4 أرقام على الأقل"
                        newPin != confirmPin -> pinMessage = "الرمزان غير متطابقين"
                        else -> {
                            PinManager.setPin(context, newPin)
                            currentPin = newPin
                            newPin = ""
                            confirmPin = ""
                            pinMessage = "تم تحديث رمز PIN بنجاح"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ رمز PIN") }

            pinMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedButton(
                onClick = {
                    PinSession.lock()
                    pinMessage = "تم قفل الجلسة، بيُطلب رمز PIN مرة ثانية بأول عملية دفع قادمة"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("قفل الجلسة الآن (طلب PIN من جديد)") }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
            Text(
                "يحفظ نسخة كاملة من بيانات التطبيق (المشتركون، الفواتير، المولدات، المصروفات) " +
                    "على الجهاز، ويمكن مشاركتها لأي تطبيق آخر (واتساب، بريد، درايف) لحفظها خارج الجهاز أيضًا.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("نسخ احتياطي تلقائي يومي")
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build()
                            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                                BACKUP_WORK_NAME,
                                ExistingPeriodicWorkPolicy.KEEP,
                                request
                            )
                            autoBackupEnabled = true
                            backupMessage = "تم تفعيل النسخ الاحتياطي التلقائي اليومي"
                        } else {
                            WorkManager.getInstance(context).cancelUniqueWork(BACKUP_WORK_NAME)
                            autoBackupEnabled = false
                            backupMessage = "تم إيقاف النسخ الاحتياطي التلقائي"
                        }
                    }
                )
            }

            Button(
                enabled = !backupInProgress,
                onClick = {
                    backupInProgress = true
                    backupMessage = null
                    scope.launch {
                        try {
                            val file = BackupManager.createBackup(context)
                            refreshBackups()
                            backupMessage = "تم إنشاء نسخة احتياطية: ${BackupManager.displayName(file)}"
                        } catch (e: Exception) {
                            backupMessage = "فشل إنشاء النسخة الاحتياطية: ${e.message ?: ""}"
                        } finally {
                            backupInProgress = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (backupInProgress) "جارٍ إنشاء النسخة..." else "نسخ احتياطي الآن") }

            backupMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            if (backupList.isNotEmpty()) {
                Text(
                    "النسخ المحفوظة (${backupList.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    backupList.forEach { file ->
                        BackupRow(
                            file = file,
                            onRestore = { confirmRestoreFile = file },
                            onShare = { BackupManager.shareBackup(context, file) },
                            onDelete = {
                                BackupManager.deleteBackup(file)
                                refreshBackups()
                            }
                        )
                    }
                }
            } else {
                Text(
                    "لا توجد نسخ احتياطية بعد",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    confirmRestoreFile?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmRestoreFile = null },
            title = { Text("استعادة نسخة احتياطية") },
            text = {
                Text(
                    "سيتم استبدال كل البيانات الحالية بنسخة ${BackupManager.displayName(file)}. " +
                        "هذا الإجراء لا يمكن التراجع عنه، وسيُغلق التطبيق تلقائيًا لإعادة تحميل البيانات."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val success = BackupManager.restoreBackup(context, file)
                    confirmRestoreFile = null
                    if (success) {
                        Toast.makeText(context, "تمت الاستعادة، يُرجى إعادة فتح التطبيق", Toast.LENGTH_LONG).show()
                        (context as? android.app.Activity)?.let {
                            it.finishAffinity()
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    } else {
                        backupMessage = "فشلت عملية الاستعادة"
                    }
                }) { Text("استعادة") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreFile = null }) { Text("إلغاء") }
            }
        )
    }

    if (showPrinterScan) {
        PrinterDiscoveryDialog(
            onDismiss = { showPrinterScan = false },
            onDeviceSelected = { device -> runCharsetTest(device) }
        )
    }
}

/** صف يعرض نسخة احتياطية واحدة مع أزرار استعادة/مشاركة/حذف */
@Composable
private fun BackupRow(
    file: File,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(BackupManager.displayName(file), fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("استعادة")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** يجدول فحصًا يوميًا (كل 24 ساعة) للمتأخرين بالدفع عبر WorkManager */
private fun scheduleLatePaymentWorker(context: android.content.Context) {
    val request = PeriodicWorkRequestBuilder<LatePaymentWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        LATE_PAYMENT_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

/** يجدول فحصًا يوميًا (كل 24 ساعة) لبنود الصيانة المستحقة عبر WorkManager */
private fun scheduleMaintenanceWorker(context: android.content.Context) {
    val request = PeriodicWorkRequestBuilder<MaintenanceCheckWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        MAINTENANCE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
