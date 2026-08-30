package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.MainViewModel

private val categories = listOf("ديزل", "صيانة", "أخرى")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val expenses by viewModel.expenses.collectAsState(initial = emptyList())

    var category by remember { mutableStateOf(categories.first()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    val total = expenses.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المصروفات") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) { Text("+") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إجمالي كل المصروفات المسجّلة", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "%.2f".format(total),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(expenses) { expense ->
                    ExpenseRow(expense = expense, onDelete = { viewModel.deleteExpense(expense) })
                    Divider()
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة مصروف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("النوع", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("المبلغ") }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("ملاحظة (اختياري)") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        viewModel.addExpense(category, amt, note)
                        amount = ""; note = ""
                        showDialog = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text("${expense.category} - ${"%.2f".format(expense.amount)}") },
        supportingContent = {
            Text(DateUtils.formatDate(expense.date) + if (expense.note.isNotBlank()) " - ${expense.note}" else "")
        },
        trailingContent = {
            TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        }
    )
}
