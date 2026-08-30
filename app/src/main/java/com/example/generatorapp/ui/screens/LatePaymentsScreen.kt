package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.LateSubscriberInfo
import com.example.generatorapp.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatePaymentsScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    var lateList by remember { mutableStateOf<List<LateSubscriberInfo>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadLateSubscribers { lateList = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المتأخرون بالدفع") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "مشترك يُعتبر متأخرًا إذا ما له فاتورة مسجّلة خلال الشهر الحالي (${DateUtils.monthName(DateUtils.currentMonth())})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "عدد المتأخرين: ${lateList?.size ?: "..."}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            lateList?.let { list ->
                if (list.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد مشتركون متأخرون هذا الشهر 🎉")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(list) { info ->
                            ListItem(
                                headlineContent = { Text(info.subscriber.name) },
                                supportingContent = {
                                    Text(
                                        if (info.lastPaymentMillis != null)
                                            "آخر دفعة: ${DateUtils.formatDate(info.lastPaymentMillis)}"
                                        else
                                            "لم يسبق له أي دفعة"
                                    )
                                },
                                trailingContent = { Text(info.subscriber.phone) }
                            )
                            Divider()
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.loadLateSubscribers { lateList = it } },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("تحديث القائمة") }
        }
    }
}
