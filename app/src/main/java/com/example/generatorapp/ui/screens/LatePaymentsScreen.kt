package com.example.generatorapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

    // فلترة المتأخرين حسب المنطقة — يسهّل تنظيم جولات التحصيل الميداني منطقة تلو أخرى
    var selectedAreaFilter by remember { mutableStateOf<String?>(null) }
    val areas = remember(lateList) {
        lateList.orEmpty().map { it.subscriber.area }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filteredLateList = remember(lateList, selectedAreaFilter) {
        lateList.orEmpty().filter { selectedAreaFilter == null || it.subscriber.area == selectedAreaFilter }
    }

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
                        "عدد المتأخرين: ${filteredLateList.size}" +
                            if (selectedAreaFilter != null) " (من أصل ${lateList?.size ?: 0})" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (areas.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedAreaFilter == null,
                        onClick = { selectedAreaFilter = null },
                        label = { Text("كل المناطق") }
                    )
                    areas.forEach { a ->
                        FilterChip(
                            selected = selectedAreaFilter == a,
                            onClick = { selectedAreaFilter = if (selectedAreaFilter == a) null else a },
                            label = { Text(a) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            lateList?.let { list ->
                if (list.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد مشتركون متأخرون هذا الشهر 🎉")
                    }
                } else if (filteredLateList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد متأخرون بهذه المنطقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredLateList) { info ->
                            ListItem(
                                headlineContent = { Text(info.subscriber.name) },
                                supportingContent = {
                                    Text(
                                        (if (info.lastPaymentMillis != null)
                                            "آخر دفعة: ${DateUtils.formatDate(info.lastPaymentMillis)}"
                                        else
                                            "لم يسبق له أي دفعة") +
                                            if (info.subscriber.area.isNotBlank()) " - ${info.subscriber.area}" else ""
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
