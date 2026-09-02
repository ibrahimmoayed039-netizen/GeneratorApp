package com.example.generatorapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.SubscriberType
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.util.Formatters
import com.example.generatorapp.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorsScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenGenerator: (Long) -> Unit = {}
) {
    val generators by viewModel.generators.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingPricesFor by remember { mutableStateOf<Generator?>(null) }
    var showingHistoryFor by remember { mutableStateOf<Generator?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المولدات") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Text("+") }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(generators) { generator ->
                GeneratorRow(
                    generator = generator,
                    onOpen = { onOpenGenerator(generator.id) },
                    onEditPrices = { editingPricesFor = generator },
                    onShowHistory = { showingHistoryFor = generator }
                )
                Divider()
            }
        }
    }

    if (showAddDialog) {
        AddGeneratorDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, capacity, cost, residential, commercial ->
                viewModel.addGenerator(name, capacity, cost, residential, commercial)
                showAddDialog = false
            }
        )
    }

    editingPricesFor?.let { generator ->
        EditGeneratorPricesDialog(
            generator = generator,
            onDismiss = { editingPricesFor = null },
            onSave = { cost, residential, commercial, note ->
                viewModel.updateGeneratorPrices(generator, cost, residential, commercial, note)
                editingPricesFor = null
            }
        )
    }

    showingHistoryFor?.let { generator ->
        PriceHistoryDialog(
            generator = generator,
            viewModel = viewModel,
            onDismiss = { showingHistoryFor = null }
        )
    }
}

/** صف يعرض بيانات مولد واحد: القدرة، أسعار الأمبير الثلاثة، وهامش الربح لكل نوع مشترك */
@Composable
private fun GeneratorRow(
    generator: Generator,
    onOpen: () -> Unit,
    onEditPrices: () -> Unit,
    onShowHistory: () -> Unit
) {
    ListItem(
        headlineContent = { Text(generator.name) },
        supportingContent = {
            Column {
                Text("القدرة: ${generator.capacityKva} كيلو فولت أمبير - ساعات التشغيل: ${generator.currentHours}")
                Text("تكلفة الأمبير: ${formatPrice(generator.costPricePerAmpere)}")
                Text(
                    "بيع منزلي: ${formatPrice(generator.residentialPricePerAmpere)}" +
                        " (ربح ${formatPrice(generator.profitPerAmpereFor(SubscriberType.RESIDENTIAL))}/أمبير)"
                )
                Text(
                    "بيع تجاري: ${formatPrice(generator.commercialPricePerAmpere)}" +
                        " (ربح ${formatPrice(generator.profitPerAmpereFor(SubscriberType.COMMERCIAL))}/أمبير)"
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                IconButton(onClick = onEditPrices) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل الأسعار")
                }
                TextButton(onClick = onShowHistory) { Text("سجل الأسعار") }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen)
    )
}

private fun formatPrice(value: Double): String = Formatters.formatMoney(value)

/** حوار إضافة مولد جديد: يطلب تكلفة الأمبير وسعر البيع للمنزلي وللتجاري كل على حدة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGeneratorDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, capacity: Double, cost: Double, residential: Double, commercial: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var residentialPrice by remember { mutableStateOf("") }
    var commercialPrice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مولد جديد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم/رقم المولد") })
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it },
                    label = { Text("القدرة (KVA)") }
                )
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { Text("سعر تكلفة الأمبير") },
                    supportingText = { Text("ما يدفعه صاحب المولد لكل أمبير (وقود، صيانة...)") }
                )
                OutlinedTextField(
                    value = residentialPrice,
                    onValueChange = { residentialPrice = it },
                    label = { Text("سعر بيع الأمبير - منزلي") }
                )
                OutlinedTextField(
                    value = commercialPrice,
                    onValueChange = { commercialPrice = it },
                    label = { Text("سعر بيع الأمبير - تجاري") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cap = capacity.toDoubleOrNull() ?: 0.0
                val cost = costPrice.toDoubleOrNull() ?: 0.0
                val residential = residentialPrice.toDoubleOrNull() ?: 0.0
                val commercial = commercialPrice.toDoubleOrNull() ?: 0.0
                if (name.isNotBlank()) {
                    onSave(name.trim(), cap, cost, residential, commercial)
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/**
 * حوار تعديل أسعار مولد موجود. أي تعديل هنا يُسجَّل بسجل الأسعار وينعكس فورًا على كل
 * المشتركين المرتبطين بهذا المولد بأول فاتورة جديدة تُصدر لهم بعد التعديل.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGeneratorPricesDialog(
    generator: Generator,
    onDismiss: () -> Unit,
    onSave: (cost: Double, residential: Double, commercial: Double, note: String) -> Unit
) {
    var costPrice by remember { mutableStateOf(generator.costPricePerAmpere.toString()) }
    var residentialPrice by remember { mutableStateOf(generator.residentialPricePerAmpere.toString()) }
    var commercialPrice by remember { mutableStateOf(generator.commercialPricePerAmpere.toString()) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل أسعار ${generator.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "سيتم تطبيق السعر الجديد تلقائيًا على كل المشتركين المرتبطين بهذا المولد " +
                        "عند إصدار أي فاتورة جديدة لهم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { Text("سعر تكلفة الأمبير") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = residentialPrice,
                    onValueChange = { residentialPrice = it },
                    label = { Text("سعر بيع الأمبير - منزلي") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = commercialPrice,
                    onValueChange = { commercialPrice = it },
                    label = { Text("سعر بيع الأمبير - تجاري") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("سبب التغيير (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cost = costPrice.toDoubleOrNull()
                val residential = residentialPrice.toDoubleOrNull()
                val commercial = commercialPrice.toDoubleOrNull()
                if (cost != null && residential != null && commercial != null) {
                    onSave(cost, residential, commercial, note.trim())
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/** حوار يعرض سجل تغييرات أسعار مولد معيّن، من الأحدث للأقدم */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceHistoryDialog(
    generator: Generator,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val logs by viewModel.priceLogsForGenerator(generator.id).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل أسعار ${generator.name}") },
        text = {
            if (logs.isEmpty()) {
                Text("لا توجد تغييرات أسعار مسجّلة بعد.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    logs.forEach { log ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(DateUtils.formatDate(log.changeDate), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "التكلفة: ${formatPrice(log.oldCostPrice)} ← ${formatPrice(log.newCostPrice)}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("منزلي: ${formatPrice(log.oldResidentialPrice)} ← ${formatPrice(log.newResidentialPrice)}")
                                Text("تجاري: ${formatPrice(log.oldCommercialPrice)} ← ${formatPrice(log.newCommercialPrice)}")
                                if (log.note.isNotBlank()) {
                                    Text(log.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
