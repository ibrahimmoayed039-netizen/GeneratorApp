package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.printing.ReceiptPrintManager
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val subscribers by viewModel.subscribers.collectAsState(initial = emptyList())

    var selectedSubscriber by remember { mutableStateOf<Subscriber?>(null) }
    var selectedMonth by remember { mutableStateOf(DateUtils.currentMonth()) }
    var selectedYear by remember { mutableStateOf(DateUtils.currentYear()) }

    var result by remember { mutableStateOf<List<Invoice>?>(null) }
    var total by remember { mutableStateOf(0.0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("كشف حساب شهري") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DropdownSelector(
                label = "اختر المشترك",
                items = subscribers,
                selected = selectedSubscriber,
                itemLabel = { it.name },
                onSelected = { selectedSubscriber = it; result = null }
            )

            DropdownSelector(
                label = "الشهر",
                items = (1..12).toList(),
                selected = selectedMonth,
                itemLabel = { DateUtils.monthName(it) },
                onSelected = { selectedMonth = it; result = null }
            )

            OutlinedTextField(
                value = selectedYear.toString(),
                onValueChange = { selectedYear = it.toIntOrNull() ?: selectedYear; result = null },
                label = { Text("السنة") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val sub = selectedSubscriber ?: return@Button
                    viewModel.loadSubscriberStatement(sub.id, selectedYear, selectedMonth) { list, sum ->
                        result = list
                        total = sum
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedSubscriber != null
            ) { Text("عرض كشف الحساب") }

            result?.let { list ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "${selectedSubscriber?.name} - ${DateUtils.monthName(selectedMonth)} $selectedYear",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("عدد الفواتير: ${list.size}")
                        Text(
                            "الإجمالي: %.2f".format(total),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(list) { invoice ->
                        ListItem(
                            headlineContent = { Text("${invoice.generatorName} - %.2f".format(invoice.amount)) },
                            supportingContent = { Text(DateUtils.formatDate(invoice.date)) }
                        )
                        Divider()
                    }
                }

                if (list.isNotEmpty()) {
                    Button(
                        onClick = {
                            ReceiptPrintManager.printStatement(
                                context = context,
                                subscriberName = selectedSubscriber?.name ?: "",
                                periodLabel = "${DateUtils.monthName(selectedMonth)} $selectedYear",
                                invoicesList = list,
                                total = total
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("طباعة كشف الحساب") }
                }
            }
        }
    }
}
