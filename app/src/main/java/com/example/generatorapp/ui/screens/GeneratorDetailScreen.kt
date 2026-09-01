package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.FaultLog
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.MainViewModel

private enum class DetailTab(val title: String) {
    HOURS("ساعات التشغيل"),
    MAINTENANCE("الصيانة"),
    FAULTS("الأعطال")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorDetailScreen(generatorId: Long, viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val generators by viewModel.generators.collectAsState(initial = emptyList())
    val generator = generators.find { it.id == generatorId }
    val currentHours = generator?.currentHours ?: 0.0

    var tab by remember { mutableStateOf(DetailTab.HOURS) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(generator?.name ?: "المولد") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Text("+") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "ساعات التشغيل الحالية: ${formatHours(currentHours)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            TabRow(selectedTabIndex = tab.ordinal) {
                DetailTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.title) }
                    )
                }
            }

            when (tab) {
                DetailTab.HOURS -> HoursTab(generatorId, viewModel)
                DetailTab.MAINTENANCE -> MaintenanceTab(generatorId, currentHours, viewModel)
                DetailTab.FAULTS -> FaultsTab(generatorId, viewModel)
            }
        }
    }

    if (showAddDialog) {
        when (tab) {
            DetailTab.HOURS -> AddHourLogDialog(
                currentHours = currentHours,
                onDismiss = { showAddDialog = false },
                onSave = { hours, note ->
                    viewModel.addHourLog(generatorId, hours, note)
                    showAddDialog = false
                }
            )
            DetailTab.MAINTENANCE -> AddMaintenanceDialog(
                onDismiss = { showAddDialog = false },
                onSave = { type, interval, note ->
                    viewModel.addMaintenanceItem(generatorId, type, interval, note)
                    showAddDialog = false
                }
            )
            DetailTab.FAULTS -> AddFaultDialog(
                onDismiss = { showAddDialog = false },
                onSave = { description ->
                    viewModel.addFaultLog(generatorId, description)
                    showAddDialog = false
                }
            )
        }
    }
}

private fun formatHours(hours: Double): String =
    if (hours == hours.toLong().toDouble()) hours.toLong().toString() else hours.toString()

// ---------- ساعات التشغيل ----------

@Composable
private fun HoursTab(generatorId: Long, viewModel: MainViewModel) {
    val logs by viewModel.hourLogsForGenerator(generatorId).collectAsState(initial = emptyList())

    if (logs.isEmpty()) {
        EmptyHint("لا توجد قراءات مسجّلة بعد. اضغط + لإضافة قراءة عداد الساعات الحالية")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(logs) { log ->
            ListItem(
                headlineContent = { Text("${formatHours(log.hours)} ساعة") },
                supportingContent = {
                    Text(DateUtils.formatDate(log.date) + if (log.note.isNotBlank()) " - ${log.note}" else "")
                },
                trailingContent = {
                    TextButton(onClick = { viewModel.deleteHourLog(log) }) { Text("حذف") }
                }
            )
            Divider()
        }
    }
}

@Composable
private fun AddHourLogDialog(
    currentHours: Double,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var hours by remember { mutableStateOf(if (currentHours > 0) currentHours.toString() else "") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة قراءة عداد الساعات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("قراءة العداد (ساعة)") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة (اختياري)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hours.toDoubleOrNull()
                if (h != null) onSave(h, note)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// ---------- الصيانة الدورية ----------

@Composable
private fun MaintenanceTab(generatorId: Long, currentHours: Double, viewModel: MainViewModel) {
    val maintenanceItems by viewModel.maintenanceItemsForGenerator(generatorId).collectAsState(initial = emptyList())

    if (maintenanceItems.isEmpty()) {
        EmptyHint("لا توجد بنود صيانة مضافة بعد. اضغط + لإضافة بند (مثلاً: تغيير الزيت كل 250 ساعة)")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(maintenanceItems) { item ->
            val hoursSinceService = (currentHours - item.lastServiceHours).coerceAtLeast(0.0)
            val hoursRemaining = item.intervalHours - hoursSinceService
            val isDue = hoursRemaining <= 0.0

            ListItem(
                leadingContent = {
                    Icon(
                        if (isDue) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = { Text(item.type, fontWeight = FontWeight.Bold) },
                supportingContent = {
                    Text(
                        if (isDue) "مستحقة الآن (تجاوزت بـ ${formatHours(-hoursRemaining)} ساعة)"
                        else "متبقٍ ${formatHours(hoursRemaining)} ساعة (كل ${formatHours(item.intervalHours)} ساعة)"
                    )
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { viewModel.markMaintenanceServiced(item) }) { Text("تمّت") }
                        TextButton(onClick = { viewModel.deleteMaintenanceItem(item) }) { Text("حذف") }
                    }
                }
            )
            Divider()
        }
    }
}

@Composable
private fun AddMaintenanceDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit
) {
    var type by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة بند صيانة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("نوع الصيانة (مثال: تغيير الزيت)") }
                )
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text("كل كم ساعة تشغيل") }
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة (اختياري)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val i = interval.toDoubleOrNull()
                if (type.isNotBlank() && i != null && i > 0) onSave(type, i, note)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// ---------- سجل الأعطال والتصليحات ----------

@Composable
private fun FaultsTab(generatorId: Long, viewModel: MainViewModel) {
    val logs by viewModel.faultLogsForGenerator(generatorId).collectAsState(initial = emptyList())
    var editingLog by remember { mutableStateOf<FaultLog?>(null) }

    if (logs.isEmpty()) {
        EmptyHint("لا توجد أعطال مسجّلة. اضغط + لتسجيل عطل جديد")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                ListItem(
                    headlineContent = { Text(log.faultDescription) },
                    supportingContent = {
                        Column {
                            Text(DateUtils.formatDate(log.date))
                            if (log.repairDescription.isNotBlank()) {
                                Text("التصليح: ${log.repairDescription}")
                            }
                            if (log.cost > 0) Text("التكلفة: ${log.cost}")
                            Text(
                                if (log.resolved) "تم التصليح" else "بانتظار التصليح",
                                color = if (log.resolved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { editingLog = log }) { Text("تعديل") }
                            TextButton(onClick = { viewModel.deleteFaultLog(log) }) { Text("حذف") }
                        }
                    }
                )
                Divider()
            }
        }
    }

    editingLog?.let { log ->
        EditFaultDialog(
            log = log,
            onDismiss = { editingLog = null },
            onSave = { repair, cost, resolved ->
                viewModel.updateFaultLog(log, repair, cost, resolved)
                editingLog = null
            }
        )
    }
}

@Composable
private fun AddFaultDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل عطل جديد") },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("وصف العطل") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (description.isNotBlank()) onSave(description) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditFaultDialog(
    log: FaultLog,
    onDismiss: () -> Unit,
    onSave: (String, Double, Boolean) -> Unit
) {
    var repair by remember { mutableStateOf(log.repairDescription) }
    var cost by remember { mutableStateOf(if (log.cost > 0) log.cost.toString() else "") }
    var resolved by remember { mutableStateOf(log.resolved) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات العطل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(log.faultDescription, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = repair, onValueChange = { repair = it }, label = { Text("وصف التصليح") })
                OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("تكلفة التصليح") })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تم التصليح؟")
                    Switch(checked = resolved, onCheckedChange = { resolved = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(repair, cost.toDoubleOrNull() ?: 0.0, resolved)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
