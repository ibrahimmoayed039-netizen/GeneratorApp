package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.util.Formatters
import com.example.generatorapp.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    onOpenSubscribers: () -> Unit,
    onOpenGenerators: () -> Unit,
    onOpenBilling: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenLatePayments: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenProfitReport: () -> Unit,
    onOpenPaymentStatus: () -> Unit,
    onOpenMaintenanceAlerts: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val subscribers by viewModel.subscribers.collectAsState(initial = emptyList())
    var monthRevenue by remember { mutableStateOf<Double?>(null) }
    var lateCount by remember { mutableStateOf<Int?>(null) }
    var maintenanceCount by remember { mutableStateOf<Int?>(null) }
    var paidCount by remember { mutableStateOf<Int?>(null) }
    var notPaidCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadProfitSummary(DateUtils.currentYear(), DateUtils.currentMonth()) {
            monthRevenue = it.totalRevenue
        }
        viewModel.loadLateSubscribers { lateCount = it.size }
        viewModel.loadMaintenanceAlerts { maintenanceCount = it.size }
        viewModel.loadMonthlyPaymentStatus { statuses ->
            paidCount = statuses.count { it.paid }
            notPaidCount = statuses.count { !it.paid }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("مدير المولدات") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------- لوحة سريعة: أهم الأرقام دفعة واحدة ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "المشتركون",
                    value = subscribers.size.toString(),
                    icon = Icons.Filled.People,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSubscribers
                )
                StatCard(
                    title = "إيرادات الشهر",
                    value = monthRevenue?.let { Formatters.formatMoney(it) } ?: "...",
                    icon = Icons.Filled.AttachMoney,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenProfitReport
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "دفعوا هذا الشهر",
                    value = paidCount?.toString() ?: "...",
                    icon = Icons.Filled.CheckCircle,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPaymentStatus
                )
                StatCard(
                    title = "لم يدفعوا بعد",
                    value = notPaidCount?.toString() ?: "...",
                    icon = Icons.Filled.Cancel,
                    color = if ((notPaidCount ?: 0) > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPaymentStatus
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "متأخرون بالدفع",
                    value = lateCount?.toString() ?: "...",
                    icon = Icons.Filled.Warning,
                    color = if ((lateCount ?: 0) > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLatePayments
                )
                StatCard(
                    title = "تنبيهات الصيانة",
                    value = maintenanceCount?.toString() ?: "...",
                    icon = Icons.Filled.Build,
                    color = if ((maintenanceCount ?: 0) > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenMaintenanceAlerts
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            HomeCard("المشتركون", Icons.Filled.People, onOpenSubscribers)
            HomeCard("المولدات", Icons.Filled.Bolt, onOpenGenerators)
            HomeCard("تنبيهات الصيانة", Icons.Filled.Build, onOpenMaintenanceAlerts)
            HomeCard("الفواتير والطباعة", Icons.Filled.Receipt, onOpenBilling)
            HomeCard("حالة الدفع الشهرية", Icons.Filled.AttachMoney, onOpenPaymentStatus)
            HomeCard("كشف حساب شهري", Icons.Filled.Description, onOpenStatement)
            HomeCard("المتأخرون بالدفع", Icons.Filled.Warning, onOpenLatePayments)
            HomeCard("رسائل وتذكيرات (واتساب/SMS)", Icons.Filled.Sms, onOpenReminders)
            HomeCard("المصروفات (ديزل/صيانة)", Icons.Filled.MoneyOff, onOpenExpenses)
            HomeCard("تقرير الأرباح", Icons.Filled.TrendingUp, onOpenProfitReport)
            HomeCard("إعدادات المحل (الشعار)", Icons.Filled.Settings, onOpenSettings)
            HomeCard("حول البرنامج", Icons.Filled.Info, onOpenAbout)
        }
    }
}

/** بطاقة رقم سريع أعلى الشاشة الرئيسية (عدد/مبلغ + عنوان)، تنقل عند الضغط لشاشتها التفصيلية */
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = title)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
