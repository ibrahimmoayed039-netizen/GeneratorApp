package com.example.generatorapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.data.entities.Employee
import com.example.generatorapp.security.PinManager
import com.example.generatorapp.ui.components.OwnerGate
import com.example.generatorapp.viewmodel.MainViewModel

/** إدارة الموظفين (للمدير فقط): إضافة موظف برمز PIN خاص، تعطيل/حذف، وخيار طلب الرمز عند كل دفعة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(viewModel: MainViewModel = viewModel(), onBack: () -> Unit) {
    OwnerGate(onBack = onBack) {
        val context = LocalContext.current
        val employees by viewModel.employees.collectAsState(initial = emptyList())
        var name by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var message by remember { mutableStateOf<String?>(null) }
        var messageIsError by remember { mutableStateOf(false) }
        var everyPayment by remember { mutableStateOf(PinManager.requirePinEveryPayment(context)) }
        var toDelete by remember { mutableStateOf<Employee?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("الموظفون") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "كل موظف له رمز PIN خاص به. عند تسجيل أي دفعة يُدخل الموظف رمزه، فيُسجَّل اسمه " +
                        "على الفاتورة والوصل، وتعرف لاحقًا من تقرير \"من استلم المال\" كم استلم كل موظف. " +
                        "رمز المدير الرئيسي (من الإعدادات) يظهر باسم \"${PinManager.OWNER_NAME}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("طلب الرمز عند كل دفعة", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "مفعّل: يُطلب الرمز لكل دفعة (أدق). غير مفعّل: يُطلب مرة واحدة وتبقى الجلسة " +
                                    "باسم صاحبها حتى قفلها، فلو سلّم الجهاز لزميله تُسجَّل دفعاته باسمه.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = everyPayment,
                            onCheckedChange = {
                                everyPayment = it
                                PinManager.setRequirePinEveryPayment(context, it)
                            }
                        )
                    }
                }

                Text("إضافة موظف", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الموظف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it },
                    label = { Text("رمز PIN للموظف (4 على الأقل)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.addEmployee(name, pin) { error ->
                            if (error == null) {
                                message = "تمت إضافة الموظف $name"
                                messageIsError = false
                                name = ""
                                pin = ""
                            } else {
                                message = error
                                messageIsError = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إضافة الموظف") }
                message?.let {
                    Text(
                        it,
                        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text("الموظفون (${employees.size})", style = MaterialTheme.typography.titleMedium)
                if (employees.isEmpty()) {
                    Text(
                        "لا يوجد موظفون بعد. الدفعات الحالية تُسجَّل باسم \"${PinManager.OWNER_NAME}\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                employees.forEach { emp ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(emp.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "الرمز: ${emp.pin}" + if (emp.active) "" else "  (معطّل)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = emp.active,
                                onCheckedChange = { viewModel.setEmployeeActive(emp, it) }
                            )
                            TextButton(onClick = { toDelete = emp }) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        toDelete?.let { emp ->
            AlertDialog(
                onDismissRequest = { toDelete = null },
                title = { Text("حذف الموظف") },
                text = {
                    Text("سيُحذف ${emp.name} ولن يستطيع تسجيل دفعات. فواتيره السابقة تبقى باسمه في التقرير. للإيقاف المؤقت استخدم مفتاح التعطيل بدل الحذف.")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteEmployee(emp); toDelete = null }) { Text("حذف") }
                },
                dismissButton = { TextButton(onClick = { toDelete = null }) { Text("إلغاء") } }
            )
        }
    }
}
