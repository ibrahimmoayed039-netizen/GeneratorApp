package com.example.generatorapp.ui.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.printing.LogoManager
import com.example.generatorapp.printing.ReceiptData
import com.example.generatorapp.printing.ReceiptPrintManager
import com.example.generatorapp.security.PinManager
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

            DropdownSelector(
                label = "اختر المشترك",
                items = subscribers,
                selected = selectedSubscriber,
                itemLabel = { it.name },
                onSelected = { selectedSubscriber = it }
            )

            DropdownSelector(
                label = "اختر المولد",
                items = generators,
                selected = selectedGenerator,
                itemLabel = { it.name },
                onSelected = {
                    selectedGenerator = it
                    // تعبئة تلقائية لسعر الأمبير من بيانات المولد (قابلة للتعديل)
                    pricePerAmpere = it.pricePerAmpere.toString()
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
                    // قبل تسجيل أي دفعة، يجب على الموظف إدخال رمز PIN الصحيح أولاً
                    pinError = null
                    showPinDialog = true
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
                        onClick = {
                            val device = findFirstPairedThermalPrinter(context)
                            if (device == null) {
                                Toast.makeText(
                                    context,
                                    "لم يتم العثور على طابعة حرارية مقترنة عبر البلوتوث",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                scope.launch {
                                    try {
                                        ReceiptPrintManager.printViaThermal(device, receipt, thermalWidth)
                                        Toast.makeText(context, "تم إرسال الوصل للطابعة", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "فشل الاتصال بالطابعة", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
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

                    val subscriber = selectedSubscriber
                    val generator = selectedGenerator
                    if (subscriber == null || generator == null) {
                        Toast.makeText(context, "اختر المشترك والمولد أولاً", Toast.LENGTH_SHORT).show()
                        return@onConfirm
                    }
                    val amp = amperes.toDoubleOrNull() ?: 0.0
                    val price = pricePerAmpere.toDoubleOrNull() ?: 0.0

                    // يحفظ الفاتورة فعليًا في قاعدة البيانات (مرتبطة بالمشترك)
                    // حتى تشتغل عليها لاحقًا: كشف الحساب، تقرير الأرباح، حالة الدفع، والمتأخرين بالدفع.
                    viewModel.createInvoice(
                        subscriberId = subscriber.id,
                        subscriberName = subscriber.name,
                        generatorName = generator.name,
                        amperes = amp,
                        pricePerAmpere = price,
                        note = note
                    ) { invoice ->
                        currentReceipt = ReceiptData(
                            subscriberName = invoice.subscriberName,
                            generatorName = invoice.generatorName,
                            amperes = invoice.amperes,
                            pricePerAmpere = invoice.pricePerAmpere,
                            amount = invoice.amount,
                            dateMillis = invoice.date,
                            note = invoice.note,
                            logo = LogoManager.loadLogo(context)
                        )
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
 * يبحث عن أول جهاز بلوتوث مقترن (Paired) لاستخدامه كطابعة حرارية.
 * في تطبيق فعلي يُفضّل عرض قائمة بكل الأجهزة المقترنة ليختار المستخدم منها.
 */
@Suppress("MissingPermission")
private fun findFirstPairedThermalPrinter(context: android.content.Context): BluetoothDevice? {
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
    if (!adapter.isEnabled) return null
    return adapter.bondedDevices?.firstOrNull()
}
