package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.printing.PdfReportGenerator
import com.example.generatorapp.ui.components.CircularStat
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.util.Formatters
import com.example.generatorapp.viewmodel.MainViewModel
import com.example.generatorapp.viewmodel.ProfitReportDetails

private enum class ReportMode { MONTHLY, YEARLY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitReportScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ReportMode.MONTHLY) }
    var selectedMonth by remember { mutableStateOf(DateUtils.currentMonth()) }
    var selectedYear by remember { mutableStateOf(DateUtils.currentYear()) }
    var details by remember { mutableStateOf<ProfitReportDetails?>(null) }

    fun refresh() {
        viewModel.loadProfitReportDetails(
            year = selectedYear,
            month = if (mode == ReportMode.MONTHLY) selectedMonth else null
        ) { details = it }
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

            details?.let { d ->
                val s = d.summary
                val totalAmperes = d.invoices.sumOf { it.amperes }
                val netMarginPercent = if (s.totalRevenue > 0) (s.netProfit / s.totalRevenue * 100) else 0.0
                val ampereMarginPercent = if (s.totalRevenue > 0) (s.ampereMarginProfit / s.totalRevenue * 100) else 0.0

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CircularStat(
                            value = Formatters.formatMoney(totalAmperes),
                            label = "الأمبيرات المستخدمة",
                            progress = 1f,
                            color = MaterialTheme.colorScheme.primary
                        )
                        CircularStat(
                            value = "${Formatters.formatMoney(netMarginPercent)}%",
                            label = "نسبة صافي الربح",
                            progress = (netMarginPercent / 100).toFloat(),
                            color = if (netMarginPercent >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                        CircularStat(
                            value = "${Formatters.formatMoney(ampereMarginPercent)}%",
                            label = "نسبة ربح الأمبير",
                            progress = (ampereMarginPercent / 100).toFloat(),
                            color = if (ampereMarginPercent >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                }

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
                SummaryCard(
                    title = "ربح فرق سعر الأمبير (تكلفة ← بيع)",
                    value = s.ampereMarginProfit,
                    color = if (s.ampereMarginProfit >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )

                OutlinedButton(
                    onClick = {
                        val periodLabel = if (mode == ReportMode.MONTHLY)
                            "${DateUtils.monthName(selectedMonth)} $selectedYear"
                        else
                            "سنة $selectedYear"
                        val file = PdfReportGenerator.generateProfitReportPdf(
                            context = context,
                            periodLabel = periodLabel,
                            summary = d.summary,
                            invoices = d.invoices,
                            expenses = d.expenses
                        )
                        PdfReportGenerator.openOrShare(context, file)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تصدير PDF") }
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
                Formatters.formatMoney(value),
                style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}
