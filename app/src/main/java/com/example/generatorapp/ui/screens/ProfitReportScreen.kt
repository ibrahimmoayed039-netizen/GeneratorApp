package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.MainViewModel
import com.example.generatorapp.viewmodel.ProfitSummary

private enum class ReportMode { MONTHLY, YEARLY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitReportScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    var mode by remember { mutableStateOf(ReportMode.MONTHLY) }
    var selectedMonth by remember { mutableStateOf(DateUtils.currentMonth()) }
    var selectedYear by remember { mutableStateOf(DateUtils.currentYear()) }
    var summary by remember { mutableStateOf<ProfitSummary?>(null) }

    fun refresh() {
        viewModel.loadProfitSummary(
            year = selectedYear,
            month = if (mode == ReportMode.MONTHLY) selectedMonth else null
        ) { summary = it }
    }

    LaunchedEffect(mode, selectedMonth, selectedYear) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تقرير الأرباح") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == ReportMode.MONTHLY,
                    onClick = { mode = ReportMode.MONTHLY },
                    label = { Text("شهري") }
                )
                FilterChip(
                    selected = mode == ReportMode.YEARLY,
                    onClick = { mode = ReportMode.YEARLY },
                    label = { Text("سنوي") }
                )
            }

            if (mode == ReportMode.MONTHLY) {
                DropdownSelector(
                    label = "الشهر",
                    items = (1..12).toList(),
                    selected = selectedMonth,
                    itemLabel = { DateUtils.monthName(it) },
                    onSelected = { selectedMonth = it }
                )
            }

            OutlinedTextField(
                value = selectedYear.toString(),
                onValueChange = { selectedYear = it.toIntOrNull() ?: selectedYear },
                label = { Text("السنة") },
                modifier = Modifier.fillMaxWidth()
            )

            summary?.let { s ->
                SummaryCard(
                    title = "إجمالي الإيرادات",
                    value = s.totalRevenue,
                    color = MaterialTheme.colorScheme.primary
                )
                SummaryCard(
                    title = "إجمالي المصروفات",
                    value = s.totalExpenses,
                    color = MaterialTheme.colorScheme.error
                )
                SummaryCard(
                    title = "صافي الربح",
                    value = s.netProfit,
                    color = if (s.netProfit >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    emphasized = true
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: Double, color: androidx.compose.ui.graphics.Color, emphasized: Boolean = false) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "%.2f".format(value),
                style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}
