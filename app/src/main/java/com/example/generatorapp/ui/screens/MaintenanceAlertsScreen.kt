package com.example.generatorapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.viewmodel.MainViewModel
import com.example.generatorapp.viewmodel.MaintenanceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceAlertsScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenGenerator: (Long) -> Unit = {}
) {
    var alerts by remember { mutableStateOf<List<MaintenanceStatus>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadMaintenanceAlerts { alerts = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنبيهات الصيانة") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "بنود الصيانة المستحقة الآن أو القريبة من الاستحقاق حسب ساعات تشغيل كل مولد أو عدد الأيام منذ آخر خدمة",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "عدد التنبيهات: ${alerts?.size ?: "..."}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            alerts?.let { list ->
                if (list.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد بنود صيانة مستحقة حاليًا 🎉")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(list) { status ->
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = if (status.isDue) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.tertiary
                                    )
                                },
                                headlineContent = { Text("${status.generatorName} - ${status.item.type}") },
                                supportingContent = {
                                    Column {
                                        Text(
                                            if (status.hoursRemaining <= 0.0)
                                                "مستحقة بالساعات (تجاوزت بـ ${status.hoursRemaining.unaryMinus().toLong()} ساعة)"
                                            else
                                                "متبقٍ ${status.hoursRemaining.toLong()} ساعة"
                                        )
                                        if (status.hasDaySchedule) {
                                            Text(
                                                if (status.daysRemaining <= 0)
                                                    "مستحقة بالأيام (تجاوزت بـ ${-status.daysRemaining} يوم)"
                                                else
                                                    "متبقٍ ${status.daysRemaining} يوم",
                                                color = if (status.daysRemaining <= 0)
                                                    MaterialTheme.colorScheme.error
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { onOpenGenerator(status.item.generatorId) }
                            )
                            Divider()
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.loadMaintenanceAlerts { alerts = it } },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("تحديث القائمة") }
        }
    }
}
