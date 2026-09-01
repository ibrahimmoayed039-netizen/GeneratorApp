package com.example.generatorapp.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * حوار يعرض قائمة أجهزة البلوتوث المقترنة (Paired) بالجهاز، ليختار المستخدم منها
 * الطابعة الحرارية المطلوبة بدل الاعتماد التلقائي على أول جهاز.
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceDialog(
    title: String = "اختر الطابعة",
    devices: List<BluetoothDevice>,
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (devices.isEmpty()) {
                Text(
                    "لا توجد أجهزة بلوتوث مقترنة. اذهب لإعدادات البلوتوث بالجهاز " +
                        "واقرن الطابعة أولاً، ثم أعد المحاولة."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(devices) { device ->
                        ListItem(
                            headlineContent = { Text(device.name ?: "جهاز غير معروف") },
                            supportingContent = { Text(device.address) },
                            modifier = Modifier.clickable { onDeviceSelected(device) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
