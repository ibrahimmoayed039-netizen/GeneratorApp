package com.example.generatorapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.SubscriberType
import com.example.generatorapp.printing.LogoManager
import com.example.generatorapp.printing.ReceiptData
import com.example.generatorapp.printing.ReceiptPrintManager
import com.example.generatorapp.security.PinManager
import com.example.generatorapp.security.PinSession
import com.example.generatorapp.ui.components.PinDialog
import com.example.generatorapp.ui.components.ReceiptPreview
import com.example.generatorapp.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val subscribers by viewModel.subscribers.collectAsState(initial = emptyList())
    val generators by viewModel.generators.collectAsState(initial = emptyList())

    // فلترة المشترك حسب المنطقة أولاً — تسهّل إيجاده بسرعة عند وجود عدد كبير من المشتركين
    var billingAreaFilter by remember { mutableStateOf<String?>(null) }
    val billingAreas = remember(subscribers) {
        subscribers.map { it.area }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val subscribersForBilling = remember(subscribers, billingAreaFilter) {
        subscribers.filter { billingAreaFilter == null || it.area == billingAreaFilter }
    }

    var selectedSubscriber by remember { mutableStateOf<Subscriber?>(null) }
    var selectedGenerator by remember { mutableStateOf<Generator?>(null) }
    var amperes by remember { mutableStateOf("") }
    var pricePerAmpere by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var currentReceipt by remember { mutableStateOf<ReceiptData?>(null) }
    // اختر عرض الورق الحراري: 32 حرف لـ58مم أو 48 حرف لـ80مم
    var thermalWidth by remember { mutableStateOf(32) }

    // بوابة رمز PIN: يجب على الموظف إدخال الرمز الصحيح قبل تسجيل أي دفعة
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

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
                ReceiptPrintManager.printViaThermal(device, receipt, thermalWidth)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الفواتير والطباعة") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subscribers.isEmpty() || generators.isEmpty()) {
                Text(
                    "لازم تضيف مشترك واحد على الأقل ومولد واحد على الأقل قبل إنشاء فاتورة.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (billingAreas.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = billingAreaFilter == null,
                        onClick = { billingAreaFilter = null },
                        label = { Text("كل المناطق") }
                    )
                    billingAreas.forEach { a ->
                        FilterChip(
                            selected = billingAreaFilter == a,
                            onClick = {
                                billingAreaFilter = if (billingAreaFilter == a) null else a
                                // إن أصبح المشترك المختار خارج نطاق الفلتر الجديد، نلغي اختياره
                                if (selectedSubscriber != null && selectedSubscriber?.area != billingAreaFilter) {
                                    selectedSubscriber = null
                                }
                            },
                            label = { Text(a) }
                        )
                    }
                }
            }

            DropdownSelector(
                label = "اختر المشترك",
                items = subscribersForBilling,
                selected = selectedSubscriber,
                itemLabel = {
                    val base = if (it.area.isNotBlank()) "${it.name} - ${it.area}" else it.name
                    "$base (${it.subscriberType})"
                },
                onSelected = {
                    selectedSubscriber = it
                    // إعادة تعبئة سعر الأمبير حسب نوع المشترك (منزلي/تجاري) الجديد إن كان مولد مختارًا مسبقًا
                    selectedGenerator?.let { gen -> pricePerAmpere = gen.sellPriceFor(it.subscriberType).toString() }
                }
            )

            DropdownSelector(
                label = "اختر المولد",
                items = generators,
                selected = selectedGenerator,
                itemLabel = { it.name },
                onSelected = {
                    selectedGenerator = it
                    // تعبئة تلقائية لسعر الأمبير حسب نوع المشترك المختار (منزلي/تجاري) - قابلة للتعديل
                    val type = selectedSubscriber?.subscriberType ?: SubscriberType.RESIDENTIAL
                    pricePerAmpere = it.sellPriceFor(type).toString()
                }
            )

            OutlinedTextField(
                value = amperes, onValueChange = { amperes = it },
                label = { Text("عدد الأمبيرات") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pricePerAmpere, onValueChange = { pricePerAmpere = it },
                label = { Text("سعر الأمبير") }, modifier = Modifier.fillMaxWidth()
            )
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

            Button(
                onClick = {
                    val subscriber = selectedSubscriber
                    val generator = selectedGenerator
                    if (subscriber == null || generator == null) {
                        Toast.makeText(context, "اختر المشترك والمولد أولاً", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (PinSession.unlocked) {
                        // الجلسة مفتوحة أصلاً (تم إدخال الرمز سابقًا بنفس فتحة التطبيق) — سجّل الدفعة مباشرة
                        createInvoiceAndBuildReceipt(
                            viewModel = viewModel,
                            context = context,
                            subscriber = subscriber,
                            generatorName = generator.name,
                            amperesText = amperes,
                            priceText = pricePerAmpere,
                            note = note,
                            costPricePerAmpere = generator.costPricePerAmpere
                        ) { receipt -> currentReceipt = receipt }
                    } else {
                        // أول مرة بهذه الجلسة — يجب إدخال رمز PIN
                        pinError = null
                        showPinDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تسجيل دفعة (إنشاء وحفظ الوصل)") }

            currentReceipt?.let { receipt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ReceiptPreview(receipt = receipt, modifier = Modifier.fillMaxWidth())
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // طباعة عادية عبر نظام أندرويد (أي طابعة مسجّلة)
                    OutlinedButton(
                        onClick = { ReceiptPrintManager.printViaSystem(context, receipt) },
                        modifier = Modifier.weight(1f)
                    ) { Text("طباعة عادية") }

                    // طباعة حرارية عبر البلوتوث (يتطلب طابعة مقترنة مسبقًا)
                    Button(
                        onClick = { requestThermalPrint(receipt) },
                        modifier = Modifier.weight(1f)
                    ) { Text("طباعة حرارية") }
                }
            }
        }
    }

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

                    val subscriber = selectedSubscriber
                    val generator = selectedGenerator
                    if (subscriber == null || generator == null) {
                        Toast.makeText(context, "اختر المشترك والمولد أولاً", Toast.LENGTH_SHORT).show()
                    } else {
                        createInvoiceAndBuildReceipt(
                            viewModel = viewModel,
                            context = context,
                            subscriber = subscriber,
                            generatorName = generator.name,
                            amperesText = amperes,
                            priceText = pricePerAmpere,
                            note = note,
                            costPricePerAmpere = generator.costPricePerAmpere
                        ) { receipt -> currentReceipt = receipt }
                    }
                } else {
                    pinError = "رمز PIN غير صحيح، حاول مرة أخرى"
                }
            }
        )
    }
}

/** قائمة منسدلة عامة لاختيار عنصر من قائمة (مشترك أو مولد) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    items: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.let(itemLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * ينشئ فاتورة فعليًا بقاعدة البيانات ويبني منها بيانات الوصل (ReceiptData) الجاهزة
 * للمعاينة والطباعة. دالة مشتركة تُستخدم من شاشة الفواتير وشاشة حالة الدفع الشهرية
 * حتى لا يتكرر نفس المنطق مرتين.
 */
fun createInvoiceAndBuildReceipt(
    viewModel: MainViewModel,
    context: android.content.Context,
    subscriber: Subscriber,
    generatorName: String,
    amperesText: String,
    priceText: String,
    note: String,
    costPricePerAmpere: Double = 0.0,
    onReceiptReady: (ReceiptData) -> Unit
) {
    val amp = amperesText.toDoubleOrNull() ?: 0.0
    val price = priceText.toDoubleOrNull() ?: 0.0

    viewModel.createInvoice(
        subscriberId = subscriber.id,
        subscriberName = subscriber.name,
        generatorName = generatorName,
        amperes = amp,
        pricePerAmpere = price,
        note = note,
        subscriberType = subscriber.subscriberType,
        costPricePerAmpere = costPricePerAmpere
    ) { invoice ->
        onReceiptReady(
            ReceiptData(
                subscriberName = invoice.subscriberName,
                meterNumber = subscriber.meterNumber,
                generatorName = invoice.generatorName,
                amperes = invoice.amperes,
                pricePerAmpere = invoice.pricePerAmpere,
                amount = invoice.amount,
                dateMillis = invoice.date,
                note = invoice.note,
                logo = LogoManager.loadLogo(context)
            )
        )
    }
}

/**
 * يبحث عن أول جهاز بلوتوث مقترن (Paired) لاستخدامه كطابعة حرارية.
 * (الدالة موجودة الآن بشكل مشترك في printing/BluetoothPrinterUtils.kt)
 */
