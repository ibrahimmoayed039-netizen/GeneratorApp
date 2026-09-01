package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
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
    onOpenReminders: () -> Unit
) {
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
        }
    }
}

@Composable
private fun HomeCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
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
