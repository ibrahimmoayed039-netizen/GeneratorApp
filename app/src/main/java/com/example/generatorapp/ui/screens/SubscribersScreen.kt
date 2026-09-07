package com.example.generatorapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.SubscriberType
import com.example.generatorapp.data.entities.Subscription
import com.example.generatorapp.printing.PdfReportGenerator
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribersScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val subscribers by viewModel.subscribers.collectAsState(initial = emptyList())
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsState(initial = emptyList())

    // إجمالي عدد الأمبيرات المشترك بها كل مشترك (قد يكون مشتركًا بأكثر من مولد بنفس الوقت)
    val amperesBySubscriber = remember(activeSubscriptions) {
        activeSubscriptions.groupBy { it.subscriberId }.mapValues { (_, subs) -> subs.sumOf { it.amperes } }
    }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var meter by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var subscriberType by remember { mutableStateOf(SubscriberType.RESIDENTIAL) }
    var showDialog by remember { mutableStateOf(false) }

    // المشترك المختار حاليًا لعرض تفاصيله وإدارة اشتراكاته
    var selectedSubscriber by remember { mutableStateOf<Subscriber?>(null) }

    // نص البحث بالاسم أو رقم العداد
    var searchQuery by remember { mutableStateOf("") }

    // قائمة المناطق الموجودة فعليًا لدى المشتركين (لأغراض الفلترة)
    val areas = remember(subscribers) {
        subscribers.map { it.area }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var selectedAreaFilter by remember { mutableStateOf<String?>(null) }

    // تجميع القائمة حسب المنطقة بدل عرضها كقائمة مسطّحة — يسهّل حصر عدد المشتركين بكل منطقة
    var groupByArea by remember { mutableStateOf(false) }

    val filteredSubscribers = remember(subscribers, searchQuery, selectedAreaFilter) {
        subscribers
            .filter { selectedAreaFilter == null || it.area == selectedAreaFilter }
            .filter {
                searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.meterNumber.contains(searchQuery, ignoreCase = true)
            }
    }

    // نفس القائمة المفلترة لكن مجمّعة حسب المنطقة (المشتركون بلا منطقة يوضعون تحت "بدون تصنيف")
    val groupedSubscribers = remember(filteredSubscribers) {
        filteredSubscribers
            .groupBy { it.area.ifBlank { "بدون تصنيف" } }
            .toSortedMap(compareBy { if (it == "بدون تصنيف") "\uFFFF" else it })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المشتركون") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("رجوع") }
                },
                actions = {
                    TextButton(onClick = {
                        val file = PdfReportGenerator.generateSubscribersListPdf(context, filteredSubscribers)
                        PdfReportGenerator.openOrShare(context, file)
                    }) { Text("تصدير PDF") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) { Text("+") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث بالاسم أو رقم العداد") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تجميع حسب المنطقة", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = groupByArea, onCheckedChange = { groupByArea = it })
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (filteredSubscribers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (subscribers.isEmpty()) "لا يوجد مشتركون بعد" else "لا توجد نتائج مطابقة للبحث",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (groupByArea) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groupedSubscribers.forEach { (areaName, subs) ->
                        item {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    "$areaName  (${subs.size})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        items(subs) { subscriber ->
                            SubscriberRow(subscriber, amperesBySubscriber[subscriber.id]) { selectedSubscriber = subscriber }
                            Divider()
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredSubscribers) { subscriber ->
                        SubscriberRow(subscriber, amperesBySubscriber[subscriber.id]) { selectedSubscriber = subscriber }
                        Divider()
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة مشترك جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("الهاتف") })
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") })
                    OutlinedTextField(value = meter, onValueChange = { meter = it }, label = { Text("رقم العداد") })
                    SubscriberTypeSelector(
                        selected = subscriberType,
                        onSelected = { subscriberType = it }
                    )
                    AreaAutocompleteField(
                        value = area,
                        onValueChange = { area = it },
                        existingAreas = areas,
                        supportingText = "تُستخدم لتصنيف المشتركين وتسهيل التحصيل الميداني — اختر من القائمة أو أضف منطقة جديدة"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addSubscriber(name, phone, address, meter, area.trim(), subscriberType)
                        name = ""; phone = ""; address = ""; meter = ""; area = ""; subscriberType = SubscriberType.RESIDENTIAL
                        showDialog = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }

    selectedSubscriber?.let { subscriber ->
        SubscriberDetailDialog(
            subscriber = subscriber,
            viewModel = viewModel,
            existingAreas = areas,
            onDismiss = { selectedSubscriber = null }
        )
    }
}

/** صف يعرض بيانات مشترك واحد بقائمة المشتركين (مع نوعه ومنطقته إن وُجدت) */
@Composable
private fun SubscriberRow(subscriber: Subscriber, amperes: Double?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(subscriber.name) },
        supportingContent = {
            Text(
                "${subscriber.phone} - عداد: ${subscriber.meterNumber} - ${subscriber.subscriberType}" +
                    (if (amperes != null && amperes > 0) " - ${com.example.generatorapp.util.Formatters.formatMoney(amperes)} أمبير" else "") +
                    if (subscriber.area.isNotBlank()) " - ${subscriber.area}" else ""
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** مفتاح اختيار نوع المشترك: منزلي أو تجاري — يحدد سعر بيع الأمبير المطبَّق عليه */
@Composable
fun SubscriberTypeSelector(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("نوع المشترك", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubscriberType.ALL.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelected(type) },
                    label = { Text(type) }
                )
            }
        }
    }
}

/**
 * حقل إدخال المنطقة/الحي مع اقتراحات تلقائية من المناطق المستخدمة فعليًا.
 * يسمح باختيار منطقة موجودة (لتفادي الأخطاء الإملائية وتكرار نفس المنطقة بأسماء مختلفة)
 * أو كتابة منطقة جديدة كليًا إن لم تكن موجودة بعد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    existingAreas: List<String>,
    label: String = "المنطقة / الحي",
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value, existingAreas) {
        if (value.isBlank()) existingAreas
        else existingAreas.filter { it.contains(value, ignoreCase = true) && it != value }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = {
                if (existingAreas.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .onFocusEvent { if (it.isFocused) expanded = true }
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * حوار تفاصيل المشترك: يعرض اشتراكاته (مع إمكانية التفعيل/التعليق بدل الحذف النهائي)
 * وسجل تغييرات الأمبيرات الخاص فيه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriberDetailDialog(
    subscriber: Subscriber,
    viewModel: MainViewModel,
    existingAreas: List<String>,
    onDismiss: () -> Unit
) {
    val generators by viewModel.generators.collectAsState(initial = emptyList())
    val allSubscribers by viewModel.subscribers.collectAsState(initial = listOf(subscriber))
    // نأخذ أحدث نسخة من بيانات المشترك (بعد أي تعديل) بدل الاعتماد على النسخة القديمة الممرَّرة
    val currentSubscriber = allSubscribers.firstOrNull { it.id == subscriber.id } ?: subscriber
    val subscriptions by viewModel.subscriptionsForSubscriber(subscriber.id).collectAsState(initial = emptyList())

    var showAddSubscription by remember { mutableStateOf(false) }
    var editingSubscription by remember { mutableStateOf<Subscription?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showEditSubscriber by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currentSubscriber.name)
                IconButton(onClick = { showEditSubscriber = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل بيانات المشترك")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${currentSubscriber.phone} - عداد: ${currentSubscriber.meterNumber} - ${currentSubscriber.subscriberType}", style = MaterialTheme.typography.bodySmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الاشتراكات", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سجل الأمبيرات")
                    }
                }

                if (subscriptions.isEmpty()) {
                    Text("لا يوجد اشتراك مسجّل بعد لهذا المشترك.", style = MaterialTheme.typography.bodySmall)
                } else {
                    subscriptions.forEach { subscription ->
                        val generatorName = generators.firstOrNull { it.id == subscription.generatorId }?.name ?: "مولد محذوف"
                        SubscriptionRow(
                            generatorName = generatorName,
                            subscription = subscription,
                            onToggleActive = { viewModel.toggleSubscriptionActive(subscription) },
                            onEditAmperes = { editingSubscription = subscription }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showAddSubscription = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = generators.isNotEmpty()
                ) { Text(if (generators.isEmpty()) "أضف مولد أولاً لإنشاء اشتراك" else "+ اشتراك جديد") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )

    if (showAddSubscription) {
        AddSubscriptionDialog(
            generators = generators,
            onDismiss = { showAddSubscription = false },
            onConfirm = { generator, amperes ->
                viewModel.addSubscription(subscriber.id, generator.id, amperes)
                showAddSubscription = false
            }
        )
    }

    editingSubscription?.let { subscription ->
        EditAmperesDialog(
            currentAmperes = subscription.amperes,
            onDismiss = { editingSubscription = null },
            onConfirm = { newAmperes, note ->
                viewModel.changeSubscriptionAmperes(subscription, newAmperes, note)
                editingSubscription = null
            }
        )
    }

    if (showHistory) {
        AmpereHistoryDialog(
            subscriberId = subscriber.id,
            viewModel = viewModel,
            onDismiss = { showHistory = false }
        )
    }

    if (showEditSubscriber) {
        EditSubscriberDialog(
            subscriber = currentSubscriber,
            existingAreas = existingAreas,
            onDismiss = { showEditSubscriber = false },
            onConfirm = { name, phone, address, meter, area, subscriberType ->
                viewModel.updateSubscriber(
                    currentSubscriber.copy(
                        name = name,
                        phone = phone,
                        address = address,
                        meterNumber = meter,
                        area = area,
                        subscriberType = subscriberType
                    )
                )
                showEditSubscriber = false
            }
        )
    }
}

/** صف يعرض اشتراك واحد مع زر تفعيل/تعليق وزر تعديل الأمبير (بدل أي زر حذف نهائي) */
@Composable
private fun SubscriptionRow(
    generatorName: String,
    subscription: Subscription,
    onToggleActive: () -> Unit,
    onEditAmperes: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(generatorName, fontWeight = FontWeight.SemiBold)
                AssistChip(
                    onClick = {},
                    label = { Text(if (subscription.active) "فعّال" else "معلّق") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (subscription.active)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                )
            }
            Text("عدد الأمبيرات: ${subscription.amperes}")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onEditAmperes, modifier = Modifier.weight(1f)) {
                    Text("تعديل الأمبير")
                }
                Button(
                    onClick = onToggleActive,
                    modifier = Modifier.weight(1f),
                    colors = if (subscription.active)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else
                        ButtonDefaults.buttonColors()
                ) {
                    Text(if (subscription.active) "تعليق الاشتراك" else "تفعيل الاشتراك")
                }
            }
        }
    }
}

/** حوار تعديل بيانات المشترك (الاسم، الهاتف، العنوان، رقم العداد) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSubscriberDialog(
    subscriber: Subscriber,
    existingAreas: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, address: String, meter: String, area: String, subscriberType: String) -> Unit
) {
    var name by remember { mutableStateOf(subscriber.name) }
    var phone by remember { mutableStateOf(subscriber.phone) }
    var address by remember { mutableStateOf(subscriber.address) }
    var meter by remember { mutableStateOf(subscriber.meterNumber) }
    var area by remember { mutableStateOf(subscriber.area) }
    var subscriberType by remember { mutableStateOf(subscriber.subscriberType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات المشترك") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meter, onValueChange = { meter = it },
                    label = { Text("رقم العداد") }, modifier = Modifier.fillMaxWidth()
                )
                SubscriberTypeSelector(
                    selected = subscriberType,
                    onSelected = { subscriberType = it }
                )
                AreaAutocompleteField(
                    value = area,
                    onValueChange = { area = it },
                    existingAreas = existingAreas
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name.trim(), phone.trim(), address.trim(), meter.trim(), area.trim(), subscriberType)
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/** حوار اختيار مولد وإدخال أمبيرات لإنشاء اشتراك جديد لمشترك */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionDialog(
    generators: List<Generator>,
    onDismiss: () -> Unit,
    onConfirm: (Generator, Double) -> Unit
) {
    var selectedGenerator by remember { mutableStateOf<Generator?>(generators.firstOrNull()) }
    var amperes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اشتراك جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = "اختر المولد",
                    items = generators,
                    selected = selectedGenerator,
                    itemLabel = { it.name },
                    onSelected = { selectedGenerator = it }
                )
                OutlinedTextField(
                    value = amperes,
                    onValueChange = { amperes = it },
                    label = { Text("عدد الأمبيرات") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val generator = selectedGenerator
                val amp = amperes.toDoubleOrNull()
                if (generator != null && amp != null && amp > 0) {
                    onConfirm(generator, amp)
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/** حوار تعديل عدد الأمبيرات، مع ملاحظة اختيارية تُحفظ بسجل التغييرات */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAmperesDialog(
    currentAmperes: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amperes by remember { mutableStateOf(currentAmperes.toString()) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل عدد الأمبيرات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("القيمة الحالية: $currentAmperes أمبير", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = amperes,
                    onValueChange = { amperes = it },
                    label = { Text("عدد الأمبيرات الجديد") },
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
                val amp = amperes.toDoubleOrNull()
                if (amp != null && amp > 0) {
                    onConfirm(amp, note)
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/** حوار يعرض سجل تغييرات الأمبيرات (كل زيادة أو نقصان) لمشترك معيّن */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmpereHistoryDialog(
    subscriberId: Long,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val logs by viewModel.ampereLogsForSubscriber(subscriberId).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل تغييرات الأمبيرات") },
        text = {
            if (logs.isEmpty()) {
                Text("لا توجد تغييرات مسجّلة بعد.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    logs.forEach { log ->
                        val increased = log.newAmperes > log.oldAmperes
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${log.oldAmperes} ← ${log.newAmperes} أمبير",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (increased) "زيادة" else "نقصان",
                                        color = if (increased)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(DateUtils.formatDate(log.changeDate), style = MaterialTheme.typography.bodySmall)
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
