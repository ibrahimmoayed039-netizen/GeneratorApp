package com.example.generatorapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.messaging.MessageComposer
import com.example.generatorapp.messaging.SmsSender
import com.example.generatorapp.messaging.WhatsAppSender
import com.example.generatorapp.notifications.MessageSettings
import com.example.generatorapp.viewmodel.LateSubscriberInfo
import com.example.generatorapp.viewmodel.MainViewModel
import com.example.generatorapp.viewmodel.SubscriberPriceInfo

private enum class ReminderTab { LATE, PRICE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(ReminderTab.LATE) }

    var lateList by remember { mutableStateOf<List<LateSubscriberInfo>?>(null) }
    var priceList by remember { mutableStateOf<List<SubscriberPriceInfo>?>(null) }

    val lateTemplate = remember { MessageSettings.getLateTemplate(context) }
    val priceTemplate = remember { MessageSettings.getPriceTemplate(context) }
    val smsFeatureEnabled = remember { MessageSettings.isSmsFeatureEnabled(context) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var sendingInProgress by remember { mutableStateOf(false) }

    // قائمة انتظار إرسال واتساب المتتابع: تفتح محادثة كل مشترك بالتسلسل، والمستخدم يضغط
    // إرسال بواتساب بنفسه (قيد من واتساب نفسه، انظر تعليق WhatsAppSender)
    var whatsAppQueue by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var whatsAppQueueIndex by remember { mutableStateOf(0) }

    fun loadLate() { viewModel.loadLateSubscribers { lateList = it } }
    fun loadPrices() { viewModel.loadSubscriberPricingInfo { priceList = it } }

    LaunchedEffect(Unit) {
        loadLate()
        loadPrices()
    }

    val requestSmsPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            statusMessage = "لازم تسمح بصلاحية الرسائل النصية (SMS) لإرسال التذكيرات"
        }
    }

    fun ensureSmsPermission(onGranted: () -> Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            onGranted()
        } else {
            requestSmsPermission.launch(Manifest.permission.SEND_SMS)
        }
    }

    fun sendBulkSmsLate() {
        if (!smsFeatureEnabled) return
        val list = lateList.orEmpty()
        if (list.isEmpty()) {
            statusMessage = "لا يوجد مشتركون متأخرون حاليًا"
            return
        }
        ensureSmsPermission {
            sendingInProgress = true
            val items = list.map { it.subscriber to MessageComposer.lateReminder(it.subscriber, lateTemplate) }
            val result = SmsSender.sendBulk(context, items)
            sendingInProgress = false
            statusMessage = "تم إرسال ${result.successCount} رسالة" +
                if (result.failed.isNotEmpty()) "، وفشل الإرسال لـ ${result.failed.size} (تحقق من أرقامهم)" else ""
        }
    }

    fun sendBulkSmsPrice() {
        if (!smsFeatureEnabled) return
        val list = priceList.orEmpty()
        if (list.isEmpty()) {
            statusMessage = "لا يوجد مشتركون لديهم اشتراك فعّال"
            return
        }
        ensureSmsPermission {
            sendingInProgress = true
            val items = list.map {
                it.subscriber to MessageComposer.priceMessage(it.subscriber, it.generator, priceTemplate)
            }
            val result = SmsSender.sendBulk(context, items)
            sendingInProgress = false
            statusMessage = "تم إرسال ${result.successCount} رسالة" +
                if (result.failed.isNotEmpty()) "، وفشل الإرسال لـ ${result.failed.size} (تحقق من أرقامهم)" else ""
        }
    }

    fun startWhatsAppQueueLate() {
        val list = lateList.orEmpty()
        if (list.isEmpty()) {
            statusMessage = "لا يوجد مشتركون متأخرون حاليًا"
            return
        }
        whatsAppQueue = list.map { it.subscriber.phone to MessageComposer.lateReminder(it.subscriber, lateTemplate) }
        whatsAppQueueIndex = 0
    }

    fun startWhatsAppQueuePrice() {
        val list = priceList.orEmpty()
        if (list.isEmpty()) {
            statusMessage = "لا يوجد مشتركون لديهم اشتراك فعّال"
            return
        }
        whatsAppQueue = list.map {
            it.subscriber.phone to MessageComposer.priceMessage(it.subscriber, it.generator, priceTemplate)
        }
        whatsAppQueueIndex = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رسائل وتذكيرات المشتركين") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == ReminderTab.LATE,
                    onClick = { tab = ReminderTab.LATE },
                    text = { Text("تذكير المتأخرين") }
                )
                Tab(
                    selected = tab == ReminderTab.PRICE,
                    onClick = { tab = ReminderTab.PRICE },
                    text = { Text("سعر الأمبير الشهري") }
                )
            }

            statusMessage?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (tab) {
                ReminderTab.LATE -> LateReminderTab(
                    lateList = lateList,
                    template = lateTemplate,
                    sendingInProgress = sendingInProgress,
                    smsFeatureEnabled = smsFeatureEnabled,
                    onSendAllSms = ::sendBulkSmsLate,
                    onSendAllWhatsApp = ::startWhatsAppQueueLate,
                    onSendOneSms = { sub ->
                        ensureSmsPermission {
                            val ok = SmsSender.sendSms(context, sub.phone, MessageComposer.lateReminder(sub, lateTemplate))
                            statusMessage = if (ok) "تم إرسال الرسالة لـ ${sub.name}" else "فشل إرسال الرسالة لـ ${sub.name}"
                        }
                    },
                    onSendOneWhatsApp = { sub ->
                        val ok = WhatsAppSender.openChat(context, sub.phone, MessageComposer.lateReminder(sub, lateTemplate))
                        if (!ok) statusMessage = "تعذّر فتح واتساب — تأكد أنه مثبّت والرقم صحيح"
                    }
                )
                ReminderTab.PRICE -> PriceMessageTab(
                    priceList = priceList,
                    template = priceTemplate,
                    sendingInProgress = sendingInProgress,
                    smsFeatureEnabled = smsFeatureEnabled,
                    onSendAllSms = ::sendBulkSmsPrice,
                    onSendAllWhatsApp = ::startWhatsAppQueuePrice,
                    onSendOneSms = { info ->
                        ensureSmsPermission {
                            val text = MessageComposer.priceMessage(info.subscriber, info.generator, priceTemplate)
                            val ok = SmsSender.sendSms(context, info.subscriber.phone, text)
                            statusMessage = if (ok) "تم إرسال الرسالة لـ ${info.subscriber.name}" else "فشل إرسال الرسالة لـ ${info.subscriber.name}"
                        }
                    },
                    onSendOneWhatsApp = { info ->
                        val text = MessageComposer.priceMessage(info.subscriber, info.generator, priceTemplate)
                        val ok = WhatsAppSender.openChat(context, info.subscriber.phone, text)
                        if (!ok) statusMessage = "تعذّر فتح واتساب — تأكد أنه مثبّت والرقم صحيح"
                    }
                )
            }
        }
    }

    // حوار قائمة إرسال واتساب المتتابعة (مشترك واحد كل مرة)
    whatsAppQueue?.let { queue ->
        if (whatsAppQueueIndex < queue.size) {
            val (phone, text) = queue[whatsAppQueueIndex]
            AlertDialog(
                onDismissRequest = { whatsAppQueue = null },
                title = { Text("إرسال واتساب (${whatsAppQueueIndex + 1} من ${queue.size})") },
                text = {
                    Text("سيتم فتح واتساب برسالة جاهزة لهذا الرقم، اضغط زر الإرسال داخل واتساب ثم ارجع هنا واضغط \"التالي\".")
                },
                confirmButton = {
                    TextButton(onClick = {
                        WhatsAppSender.openChat(context, phone, text)
                    }) { Text("فتح واتساب") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { whatsAppQueueIndex++ }) { Text("التالي") }
                        TextButton(onClick = { whatsAppQueue = null }) { Text("إنهاء") }
                    }
                }
            )
        } else {
            LaunchedEffect(Unit) {
                statusMessage = "تم الانتهاء من قائمة إرسال واتساب"
                whatsAppQueue = null
            }
        }
    }
}

@Composable
private fun LateReminderTab(
    lateList: List<LateSubscriberInfo>?,
    template: String,
    sendingInProgress: Boolean,
    smsFeatureEnabled: Boolean,
    onSendAllSms: () -> Unit,
    onSendAllWhatsApp: () -> Unit,
    onSendOneSms: (com.example.generatorapp.data.entities.Subscriber) -> Unit,
    onSendOneWhatsApp: (com.example.generatorapp.data.entities.Subscriber) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        InfoCard(
            title = "تذكير المتأخرين بالدفع",
            description = "يرسل رسالة تذكير لكل مشترك ما له فاتورة مسجّلة هذا الشهر. عدد المتأخرين: ${lateList?.size ?: 0}",
            template = template
        )
        ActionButtonsRow(
            sendingInProgress = sendingInProgress,
            smsFeatureEnabled = smsFeatureEnabled,
            onSendAllSms = onSendAllSms,
            onSendAllWhatsApp = onSendAllWhatsApp
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lateList.orEmpty()) { info ->
                ReminderRow(
                    name = info.subscriber.name,
                    subtitle = info.subscriber.phone,
                    smsFeatureEnabled = smsFeatureEnabled,
                    onSms = { onSendOneSms(info.subscriber) },
                    onWhatsApp = { onSendOneWhatsApp(info.subscriber) }
                )
            }
        }
    }
}

@Composable
private fun PriceMessageTab(
    priceList: List<SubscriberPriceInfo>?,
    template: String,
    sendingInProgress: Boolean,
    smsFeatureEnabled: Boolean,
    onSendAllSms: () -> Unit,
    onSendAllWhatsApp: () -> Unit,
    onSendOneSms: (SubscriberPriceInfo) -> Unit,
    onSendOneWhatsApp: (SubscriberPriceInfo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        InfoCard(
            title = "رسالة سعر الأمبير الشهري",
            description = "يرسل لكل مشترك سعر الأمبير الحالي حسب مولدته ونوع اشتراكه. عدد المستلمين: ${priceList?.size ?: 0}",
            template = template
        )
        ActionButtonsRow(
            sendingInProgress = sendingInProgress,
            smsFeatureEnabled = smsFeatureEnabled,
            onSendAllSms = onSendAllSms,
            onSendAllWhatsApp = onSendAllWhatsApp
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(priceList.orEmpty()) { info ->
                ReminderRow(
                    name = info.subscriber.name,
                    subtitle = "${info.generator.name} - ${info.pricePerAmpere} / أمبير",
                    smsFeatureEnabled = smsFeatureEnabled,
                    onSms = { onSendOneSms(info) },
                    onWhatsApp = { onSendOneWhatsApp(info) }
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, description: String, template: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Text("معاينة القالب الحالي:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(template, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "لتعديل نص الرسالة: افتح الإعدادات ← قسم قوالب رسائل التذكير",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ActionButtonsRow(
    sendingInProgress: Boolean,
    smsFeatureEnabled: Boolean,
    onSendAllSms: () -> Unit,
    onSendAllWhatsApp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (smsFeatureEnabled) {
            Button(
                enabled = !sendingInProgress,
                onClick = onSendAllSms,
                modifier = Modifier.weight(1f)
            ) { Text(if (sendingInProgress) "جارٍ الإرسال..." else "📩 SMS للكل") }
        }

        OutlinedButton(
            onClick = onSendAllWhatsApp,
            modifier = Modifier.weight(1f)
        ) { Text("🟢 واتساب للكل") }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ReminderRow(
    name: String,
    subtitle: String,
    smsFeatureEnabled: Boolean,
    onSms: () -> Unit,
    onWhatsApp: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Row {
                if (smsFeatureEnabled) {
                    IconButton(onClick = onSms) {
                        Icon(Icons.Filled.Sms, contentDescription = "إرسال SMS")
                    }
                }
                TextButton(onClick = onWhatsApp) { Text("واتساب") }
            }
        }
    )
    Divider()
}
