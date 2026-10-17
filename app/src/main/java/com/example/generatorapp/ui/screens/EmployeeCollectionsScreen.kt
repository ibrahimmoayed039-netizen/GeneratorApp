package com.example.generatorapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.ui.components.OwnerGate
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.util.Formatters
import com.example.generatorapp.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class CollectionPeriod(val label: String) {
    TODAY("اليوم"), MONTH("هذا الشهر"), LAST_MONTH("الشهر الماضي"), YEAR("هذه السنة"), ALL("الكل")
}

private const val UNKNOWN_EMPLOYEE = "غير محدد (قبل إضافة الموظفين)"

private fun periodRange(p: CollectionPeriod): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    return when (p) {
        CollectionPeriod.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            start to (start + 24L * 60 * 60 * 1000 - 1)
        }
        CollectionPeriod.MONTH -> DateUtils.monthRange(year, month)
        CollectionPeriod.LAST_MONTH ->
            if (month == 1) DateUtils.monthRange(year - 1, 12) else DateUtils.monthRange(year, month - 1)
        CollectionPeriod.YEAR -> DateUtils.yearRange(year)
        CollectionPeriod.ALL -> 0L to Long.MAX_VALUE
    }
}

/** تقرير: من استلم المال — إجمالي ما استلمه كل موظف وتفاصيل كل دفعة، مع تصفية بالفترة وبالموظف */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeCollectionsScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    OwnerGate(onBack = onBack) {
        var period by remember { mutableStateOf(CollectionPeriod.MONTH) }
        var selectedEmployee by remember { mutableStateOf<String?>(null) }
        val range = remember(period) { periodRange(period) }
        val invoices by viewModel.invoicesInPeriod(range.first, range.second)
            .collectAsState(initial = emptyList<Invoice>())

        fun nameOf(i: Invoice) = i.receivedByName.ifBlank { UNKNOWN_EMPLOYEE }
        val totals = invoices.groupBy { nameOf(it) }
            .map { (n, list) -> Triple(n, list.size, list.sumOf { it.amount }) }
            .sortedByDescending { it.third }
        val shown = invoices.filter { selectedEmployee == null || nameOf(it) == selectedEmployee }
        val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar")) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("من استلم المال") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // صفين من الشرائح حتى لا تخرج عن عرض الشاشة
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(CollectionPeriod.TODAY, CollectionPeriod.MONTH, CollectionPeriod.LAST_MONTH).forEach { p ->
                                    FilterChip(
                                        selected = period == p,
                                        onClick = { period = p; selectedEmployee = null },
                                        label = { Text(p.label) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(CollectionPeriod.YEAR, CollectionPeriod.ALL).forEach { p ->
                                    FilterChip(
                                        selected = period == p,
                                        onClick = { period = p; selectedEmployee = null },
                                        label = { Text(p.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("إجمالي المبالغ المستلمة", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                Formatters.formatMoney(invoices.sumOf { it.amount }),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${invoices.size} دفعة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item { Text("حسب الموظف (اضغط لتصفية الدفعات)", style = MaterialTheme.typography.titleSmall) }
                if (totals.isEmpty()) {
                    item { Text("لا توجد دفعات في هذه الفترة", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(totals) { (empName, count, sum) ->
                    val selected = selectedEmployee == empName
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedEmployee = if (selected) null else empName
                        },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(empName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "$count دفعة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                Formatters.formatMoney(sum),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        if (selectedEmployee == null) "كل الدفعات (${shown.size})"
                        else "دفعات $selectedEmployee (${shown.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                items(shown) { inv ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(inv.subscriberName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "استلمها: ${nameOf(inv)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    dateFormat.format(inv.date) +
                                        if (inv.paidForMonth.isNotBlank()) " • ${inv.paidForMonth}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                Formatters.formatMoney(inv.amount),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
