package com.example.generatorapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.MainViewModel
import com.example.generatorapp.viewmodel.PaymentStatusInfo

/** أخضر لطيف مخصص لبطاقة عدد المدفوعين */
private val PaidGreen = Color(0xFF2E7D32)

/**
 * شاشة تعرض كل العملاء وحالة الدفع لهذا الشهر: العميل الذي دفع (له فاتورة مسجّلة
 * هذا الشهر) يظهر بلون أحمر مميّز، بينما العميل غير المدفوع يبقى بلونه الطبيعي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentStatusScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    var statusList by remember { mutableStateOf<List<PaymentStatusInfo>?>(null) }

    fun refresh() {
        viewModel.loadMonthlyPaymentStatus { statusList = it }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حالة الدفع الشهرية") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "حالة العملاء لشهر ${DateUtils.monthName(DateUtils.currentMonth())} — العميل الملوّن بالأحمر يعني أنه دفع",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val paidCount = statusList?.count { it.paid } ?: 0
                    val total = statusList?.size ?: 0
                    Text(
                        "مدفوع: $paidCount من أصل $total",
                        style = MaterialTheme.typography.titleMedium,
                        color = PaidGreen
                    )
                }
            }

            statusList?.let { list ->
                if (list.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد عملاء بعد")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(list) { info ->
                            PaymentStatusRow(info)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PaymentStatusRow(info: PaymentStatusInfo) {
    val paid = info.paid
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (paid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    info.subscriber.name,
                    fontWeight = FontWeight.Bold,
                    color = if (paid) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${info.subscriber.phone} - عداد: ${info.subscriber.meterNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (paid)
                        MaterialTheme.colorScheme.onError.copy(alpha = 0.85f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                if (paid && info.lastInvoiceThisMonthMillis != null) {
                    Text(
                        "دُفع بتاريخ: ${DateUtils.formatDate(info.lastInvoiceThisMonthMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }

            if (paid) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "مدفوع",
                    tint = MaterialTheme.colorScheme.onError
                )
            } else {
                AssistChip(onClick = {}, label = { Text("لم يدفع") })
            }
        }
    }
}
