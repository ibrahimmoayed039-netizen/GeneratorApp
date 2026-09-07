package com.example.generatorapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.printing.ReceiptData
import com.example.generatorapp.printing.ReceiptPrintManager
import com.example.generatorapp.security.PinManager
import com.example.generatorapp.security.PinSession
import com.example.generatorapp.ui.components.PinDialog
import com.example.generatorapp.ui.components.ReceiptPreview
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.util.Formatters
import com.example.generatorapp.viewmodel.MainViewModel
import com.example.generatorapp.viewmodel.PaymentStatusInfo
import kotlinx.coroutines.launch

/** أخضر لطيف مخصص لبطاقة عدد المدفوعين (نفس --ok-green في معاينة HTML) */
private val PaidGreen = Color(0xFF2F6B4F)

/**
 * شاشة تعرض كل العملاء وحالة الدفع لهذا الشهر: العميل الذي دفع (له فاتورة مسجّلة
 * هذا الشهر) يظهر بلون أحمر مميّز، بينما العميل غير المدفوع يبقى بلونه الطبيعي.
 * الضغط على أي عميل يفتح نافذة دفع سريعة (دفع لهذا الشهر + طباعة الوصل مباشرة).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentStatusScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    var statusList by remember { mutableStateOf<List<PaymentStatusInfo>?>(null) }
    var payingSubscriber by remember { mutableStateOf<Subscriber?>(null) }
    var alreadyPaidInfo by remember { mutableStateOf<PaymentStatusInfo?>(null) }

    // فلترة حسب المنطقة/الحي — تسهّل على المُحصِّل الميداني رؤية عملاء منطقته فقط
    var selectedAreaFilter by remember { mutableStateOf<String?>(null) }
    val areas = remember(statusList) {
        statusList.orEmpty().map { it.subscriber.area }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visibleList = remember(statusList, selectedAreaFilter) {
        statusList.orEmpty().filter { selectedAreaFilter == null || it.subscriber.area == selectedAreaFilter }
    }

    fun refresh() {
        viewModel.loadMonthlyPaymentStatus { statusList = it }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حالة الدفع الشهرية") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "حالة العملاء لشهر ${DateUtils.monthName(DateUtils.currentMonth())} — اضغط على أي عميل لتسجيل دفعته وطباعة الوصل",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val paidCount = visibleList.count { it.paid }
                    val total = visibleList.size
                    Text(
                        if (selectedAreaFilter != null)
                            "مدفوع في \"${selectedAreaFilter}\": $paidCount من أصل $total"
                        else
                            "مدفوع: $paidCount من أصل $total",
                        style = MaterialTheme.typography.titleMedium,
                        color = PaidGreen
                    )
                }
            }

            if (areas.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedAreaFilter == null,
                        onClick = { selectedAreaFilter = null },
                        label = { Text("كل المناطق") }
                    )
                    areas.forEach { a ->
                        FilterChip(
                            selected = selectedAreaFilter == a,
                            onClick = { selectedAreaFilter = if (selectedAreaFilter == a) null else a },
                            label = { Text(a) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            statusList?.let {
                if (visibleList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (it.isEmpty()) "لا يوجد عملاء بعد" else "لا يوجد عملاء في هذه المنطقة")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(visibleList) { info ->
                            PaymentStatusRow(
                                info,
                                onClick = {
                                    // العميل مدفوع هذا الشهر أصلًا: نعرض رسالة تأكيد + إعادة طباعة
                                    // بدل السماح بتسجيل دفعة ثانية عن طريق الخطأ.
                                    if (info.paid && info.lastInvoice != null) {
                                        alreadyPaidInfo = info
                                    } else {
                                        payingSubscriber = info.subscriber
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    payingSubscriber?.let { subscriber ->
        PaySubscriberDialog(
            subscriber = subscriber,
            viewModel = viewModel,
            onDismiss = { payingSubscriber = null },
            onPaid = { refresh() }
        )
    }

    alreadyPaidInfo?.let { info ->
        AlreadyPaidDialog(
            info = info,
            onDismiss = { alreadyPaidInfo = null }
        )
    }
}

/**
 * تُعرض عند الضغط على عميل دفع بالفعل هذا الشهر: تؤكد أنه مدفوع (بتاريخ الدفع)
 * وتتيح إعادة طباعة نفس الوصل المسجَّل للتأكد، دون إنشاء فاتورة/دفعة جديدة.
 */
@Composable
private fun AlreadyPaidDialog(info: PaymentStatusInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val invoice = info.lastInvoice ?: return
    val receipt = remember(invoice.id) {
        buildReceiptFromExistingInvoice(invoice = invoice, subscriber = info.subscriber, context = context)
    }
    var thermalWidth by remember { mutableStateOf(32) }

    fun printThermal() {
        val device = com.example.generatorapp.printing.findPreferredThermalPrinter(context)
        if (device == null) {
            Toast.makeText(
                context,
                "ما فيه طابعة حرارية محفوظة. روح للإعدادات > فحص الطابعة، واختر طابعتك أول مرة.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        scope.launch {
            try {
                ReceiptPrintManager.printViaThermal(context, device, receipt, thermalWidth)
                Toast.makeText(context, "تم إرسال الوصل للطابعة", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "فشل الاتصال بالطابعة", Toast.LENGTH_LONG).show()
            }
        }
    }

    var pendingThermalPrint by remember { mutableStateOf(false) }
    val requestBluetoothPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val wasPending = pendingThermalPrint
        pendingThermalPrint = false
        if (results.values.all { it } && wasPending) {
            printThermal()
        } else if (wasPending) {
            Toast.makeText(
                context,
                "لازم تسمح بصلاحية البلوتوث حتى تقدر تطبع على الطابعة الحرارية",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestThermalPrint() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyList()
        }
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            printThermal()
        } else {
            pendingThermalPrint = true
            requestBluetoothPermissions.launch(missing.toTypedArray())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تم الدفع مسبقًا") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${info.subscriber.name} دفع بالفعل هذا الشهر بتاريخ " +
                            DateUtils.formatDate(invoice.date) +
                            ". يمكنك إعادة طباعة نفس الوصل بالأسفل للتأكد.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    ReceiptPreview(receipt = receipt, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = thermalWidth == 32,
                        onClick = { thermalWidth = 32 },
                        label = { Text("طابعة 58مم") }
                    )
                    FilterChip(
                        selected = thermalWidth == 48,
                        onClick = { thermalWidth = 48 },
                        label = { Text("طابعة 80مم") }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { ReceiptPrintManager.printViaSystem(context, receipt) },
                        modifier = Modifier.weight(1f)
                    ) { Text("طباعة عادية") }

                    Button(
                        onClick = { requestThermalPrint() },
                        modifier = Modifier.weight(1f)
                    ) { Text("طباعة حرارية") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun PaymentStatusRow(info: PaymentStatusInfo, onClick: () -> Unit) {
    val paid = info.paid
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (paid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    info.subscriber.name,
                    fontWeight = FontWeight.Bold,
                    color = if (paid) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${info.subscriber.phone} - عداد: ${info.subscriber.meterNumber} - ${info.subscriber.subscriberType}" +
                        if (info.subscriber.area.isNotBlank()) " - ${info.subscriber.area}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (paid)
                        MaterialTheme.colorScheme.onError.copy(alpha = 0.85f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                if (paid && info.lastInvoiceThisMonthMillis != null) {
                    Text(
                        "دُفع بتاريخ: ${DateUtils.formatDate(info.lastInvoiceThisMonthMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }

            if (paid) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "مدفوع",
                    tint = MaterialTheme.colorScheme.onError
                )
            } else {
                AssistChip(onClick = onClick, label = { Text("دفع الآن") })
            }
        }
    }
}

/**
 * نافذة دفع سريعة لعميل معيّن: تعبئ تلقائيًا آخر اشتراك فعّال له (المولد وعدد الأمبيرات
 * وسعر الأمبير)، تطلب رمز PIN، تنشئ الفاتورة (= دفع لهذا الشهر)، ثم تعرض الوصل
 * وتتيح طباعته مباشرة (عادية أو حرارية) دون الحاجة للخروج من الشاشة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySubscriberDialog(
    subscriber: Subscriber,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onPaid: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val generators by viewModel.generators.collectAsState(initial = emptyList())
    val subscriptions by viewModel.subscriptionsForSubscriber(subscriber.id).collectAsState(initial = emptyList())
    val activeSubscriptions = subscriptions.filter { it.active }

    var selectedSubscription by remember(activeSubscriptions) {
        mutableStateOf(activeSubscriptions.firstOrNull())
    }
    var amperes by remember { mutableStateOf("") }
    var pricePerAmpere by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var thermalWidth by remember { mutableStateOf(32) }

    // تعبئة تلقائية لعدد الأمبيرات وسعر الأمبير عند اختيار الاشتراك (قابلة للتعديل)
    LaunchedEffect(selectedSubscription, generators) {
        selectedSubscription?.let { sub ->
            amperes = sub.amperes.toString()
            val gen = generators.firstOrNull { it.id == sub.generatorId }
            if (gen != null) pricePerAmpere = gen.sellPriceFor(subscriber.subscriberType).toString()
        }
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var currentReceipt by remember { mutableStateOf<ReceiptData?>(null) }

    // الطباعة الحرارية عبر البلوتوث الكلاسيكي تحتاج صلاحية BLUETOOTH_CONNECT وقت التشغيل
    // بدءًا من أندرويد 12 (API 31)، وإلا فإن قراءة قائمة الأجهزة المقترنة تفشل بصمت (استثناء
    // SecurityException) ويبدو للمستخدم أن الزر "لا يعمل".
    fun printThermal(receipt: ReceiptData) {
        val device = com.example.generatorapp.printing.findPreferredThermalPrinter(context)
        if (device == null) {
            Toast.makeText(
                context,
                "ما فيه طابعة حرارية محفوظة. روح للإعدادات > فحص الطابعة، واختر طابعتك أول مرة.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        scope.launch {
            try {
                ReceiptPrintManager.printViaThermal(context, device, receipt, thermalWidth)
                Toast.makeText(context, "تم إرسال الوصل للطابعة", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "فشل الاتصال بالطابعة", Toast.LENGTH_LONG).show()
            }
        }
    }

    var pendingThermalReceipt by remember { mutableStateOf<ReceiptData?>(null) }
    val requestBluetoothPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val receipt = pendingThermalReceipt
        pendingThermalReceipt = null
        if (results.values.all { it } && receipt != null) {
            printThermal(receipt)
        } else if (receipt != null) {
            Toast.makeText(
                context,
                "لازم تسمح بصلاحية البلوتوث حتى تقدر تطبع على الطابعة الحرارية",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestThermalPrint(receipt: ReceiptData) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyList()
        }
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            printThermal(receipt)
        } else {
            pendingThermalReceipt = receipt
            requestBluetoothPermissions.launch(missing.toTypedArray())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentReceipt == null) "دفع - ${subscriber.name}" else "تم الدفع - ${subscriber.name}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentReceipt == null) {
                    if (activeSubscriptions.isEmpty()) {
                        Text(
                            "ما يوجد اشتراك فعّال مسجّل لهذا العميل. أضف اشتراك من شاشة المشتركين أولاً.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        if (activeSubscriptions.size > 1) {
                            DropdownSelector(
                                label = "اختر الاشتراك (المولد)",
                                items = activeSubscriptions,
                                selected = selectedSubscription,
                                itemLabel = { sub ->
                                    generators.firstOrNull { it.id == sub.generatorId }?.name ?: "مولد"
                                },
                                onSelected = { selectedSubscription = it }
                            )
                        } else {
                            val gen = generators.firstOrNull { it.id == selectedSubscription?.generatorId }
                            Text("المولد: ${gen?.name ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                        }
                        // عدد الأمبيرات ثابت حسب اشتراك العميل، يُستخدم تلقائيًا بدون تعديل
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("عدد الأمبيرات (ثابت)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${amperes.toDoubleOrNull()?.let { "%.0f".format(it) } ?: amperes} A",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        OutlinedTextField(
                            value = pricePerAmpere, onValueChange = { pricePerAmpere = it },
                            label = { Text("سعر الأمبير") }, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = discount, onValueChange = { discount = it },
                            label = { Text("خصم على المجموع (اختياري)") }, modifier = Modifier.fillMaxWidth()
                        )
                        // السعر الإجمالي المتوقع = (عدد الأمبيرات × سعر الأمبير) - الخصم، يتحدّث فورًا مع أي تعديل
                        val subtotalPrice = (amperes.toDoubleOrNull() ?: 0.0) * (pricePerAmpere.toDoubleOrNull() ?: 0.0)
                        val safeDiscount = (discount.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotalPrice.coerceAtLeast(0.0))
                        val totalPrice = subtotalPrice - safeDiscount
                        if (safeDiscount > 0.0) {
                            Text(
                                "الإجمالي قبل الخصم: ${Formatters.formatMoney(subtotalPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "الخصم: ${Formatters.formatMoney(safeDiscount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("السعر الإجمالي", fontWeight = FontWeight.SemiBold)
                                Text(
                                    Formatters.formatMoney(totalPrice),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        OutlinedTextField(
                            value = note, onValueChange = { note = it },
                            label = { Text("ملاحظة (اختياري)") }, modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = thermalWidth == 32,
                                onClick = { thermalWidth = 32 },
                                label = { Text("طابعة 58مم") }
                            )
                            FilterChip(
                                selected = thermalWidth == 48,
                                onClick = { thermalWidth = 48 },
                                label = { Text("طابعة 80مم") }
                            )
                        }
                    }
                } else {
                    val receipt = currentReceipt!!
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ReceiptPreview(receipt = receipt, modifier = Modifier.fillMaxWidth())
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { ReceiptPrintManager.printViaSystem(context, receipt) },
                            modifier = Modifier.weight(1f)
                        ) { Text("طباعة عادية") }

                        Button(
                            onClick = { requestThermalPrint(receipt) },
                            modifier = Modifier.weight(1f)
                        ) { Text("طباعة حرارية") }
                    }
                }
            }
        },
        confirmButton = {
            if (currentReceipt == null) {
                TextButton(
                    enabled = activeSubscriptions.isNotEmpty(),
                    onClick = {
                        val sub = selectedSubscription
                        val gen = generators.firstOrNull { it.id == sub?.generatorId }
                        if (sub == null || gen == null) {
                            Toast.makeText(context, "اختر اشتراك أولاً", Toast.LENGTH_SHORT).show()
                        } else if (PinSession.unlocked) {
                            // الجلسة مفتوحة أصلاً — سجّل الدفعة مباشرة بدون طلب الرمز مرة ثانية
                            createInvoiceAndBuildReceipt(
                                viewModel = viewModel,
                                context = context,
                                subscriber = subscriber,
                                generatorName = gen.name,
                                amperesText = amperes,
                                priceText = pricePerAmpere,
                                discountText = discount,
                                note = note,
                                costPricePerAmpere = gen.costPricePerAmpere
                            ) { receipt ->
                                currentReceipt = receipt
                                onPaid()
                            }
                        } else {
                            pinError = null
                            showPinDialog = true
                        }
                    }
                ) { Text("الدفع الآن") }
            } else {
                TextButton(onClick = { onPaid(); onDismiss() }) { Text("تم") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (currentReceipt == null) "إلغاء" else "إغلاق") }
        }
    )

    if (showPinDialog) {
        PinDialog(
            title = "تسجيل دفعة",
            errorMessage = pinError,
            onDismiss = { showPinDialog = false; pinError = null },
            onConfirm = { enteredPin ->
                if (PinManager.verifyPin(context, enteredPin)) {
                    showPinDialog = false
                    pinError = null
                    PinSession.unlock()

                    val sub = selectedSubscription
                    val gen = generators.firstOrNull { it.id == sub?.generatorId }
                    if (sub == null || gen == null) {
                        Toast.makeText(context, "بيانات الاشتراك غير مكتملة", Toast.LENGTH_SHORT).show()
                    } else {
                        createInvoiceAndBuildReceipt(
                            viewModel = viewModel,
                            context = context,
                            subscriber = subscriber,
                            generatorName = gen.name,
                            amperesText = amperes,
                            priceText = pricePerAmpere,
                            discountText = discount,
                            note = note,
                            costPricePerAmpere = gen.costPricePerAmpere
                        ) { receipt ->
                            currentReceipt = receipt
                            // يحدّث القائمة فورًا حتى يتلوّن العميل بالأحمر بمجرد الدفع
                            onPaid()
                        }
                    }
                } else {
                    pinError = "رمز PIN غير صحيح، حاول مرة أخرى"
                }
            }
        )
    }
}

