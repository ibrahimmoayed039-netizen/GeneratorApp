package com.example.generatorapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.generatorapp.notifications.LatePaymentWorker
import com.example.generatorapp.notifications.NotificationHelper
import com.example.generatorapp.printing.LogoManager
import com.example.generatorapp.security.PinManager
import java.util.concurrent.TimeUnit

private const val LATE_PAYMENT_WORK_NAME = "late_payment_daily_check"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logoBitmap by remember { mutableStateOf<Bitmap?>(LogoManager.loadLogo(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var notificationsEnabled by remember { mutableStateOf(false) }

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
                .fillMaxSize(),
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

            Text("رمز PIN للموظف (تسجيل الدفعات)", style = MaterialTheme.typography.titleMedium)
            Text(
                "هذا الرمز يطلبه التطبيق من أي موظف قبل تسجيل دفعة (إنشاء فاتورة وطباعة وصل). الرمز الحالي: $currentPin",
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
