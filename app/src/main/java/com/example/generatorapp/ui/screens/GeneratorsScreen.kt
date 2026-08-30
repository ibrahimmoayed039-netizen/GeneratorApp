package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorsScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    val generators by viewModel.generators.collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المولدات") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) { Text("+") }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(generators) { generator ->
                ListItem(
                    headlineContent = { Text(generator.name) },
                    supportingContent = {
                        Text("القدرة: ${generator.capacityKva} كيلو فولت أمبير - سعر الأمبير: ${generator.pricePerAmpere}")
                    }
                )
                Divider()
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة مولد جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم/رقم المولد") })
                    OutlinedTextField(
                        value = capacity,
                        onValueChange = { capacity = it },
                        label = { Text("القدرة (KVA)") }
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("سعر الأمبير الواحد") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cap = capacity.toDoubleOrNull() ?: 0.0
                    val pr = price.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        viewModel.addGenerator(name, cap, pr)
                        name = ""; capacity = ""; price = ""
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
