package com.example.generatorapp.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.generatorapp.printing.BluetoothConnectionManager
import com.example.generatorapp.printing.BluetoothPrinterScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * حوار يبحث فعليًا (Scan) عن أجهزة بلوتوث قريبة قابلة للاكتشاف، سواء كانت مقترنة
 * (Paired) بالهاتف من قبل أو لا. الأجهزة المقترنة تظهر فورًا، والأجهزة الجديدة
 * تظهر تباعًا أثناء البحث (حوالي 12 ثانية). عند اختيار جهاز غير مقترن، يحاول
 * التطبيق قرنه (Pairing) تلقائيًا، وبمجرد نجاح القرن يُختار مباشرة كطابعة.
 *
 * بعد نجاح الاقتران (أو عند اختيار جهاز مقترن مسبقًا)، يفتح التطبيق اتصالاً دائمًا
 * بالطابعة عبر [BluetoothConnectionManager] فورًا، فتبقى "متصلة دائمًا" ولا تحتاج
 * إعادة اتصال قبل كل عملية طباعة لاحقة.
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDiscoveryDialog(
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val devices = remember { mutableStateMapOf<String, BluetoothDevice>() }
    var isScanning by remember { mutableStateOf(true) }
    var pairingAddress by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun selectAndStayConnected(device: BluetoothDevice) {
        onDeviceSelected(device)
        // فتح الاتصال الدائم في الخلفية فور الاختيار، دون انتظار أول طباعة
        coroutineScope.launch(Dispatchers.IO) {
            BluetoothConnectionManager.connectQuietly(device)
        }
    }

    DisposableEffect(Unit) {
        val scanner = BluetoothPrinterScanner(context)
        scanner.watchBonding { device, bondState ->
            devices[device.address] = device // لإعادة رسم القائمة بحالة الاقتران الجديدة
            if (device.address == pairingAddress) {
                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        pairingAddress = null
                        selectAndStayConnected(device)
                    }
                    BluetoothDevice.BOND_NONE -> {
                        pairingAddress = null
                        errorMessage = "فشل الاقتران بالجهاز، حاول مرة أخرى"
                    }
                }
            }
        }
        scanner.startScan(
            onDeviceFound = { device -> devices[device.address] = device },
            onFinished = { isScanning = false }
        )
        onDispose {
            scanner.stopScan()
            scanner.stopWatchingBonding()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بحث عن طابعات قريبة") },
        text = {
            Column {
                if (isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جارٍ البحث عن أجهزة قريبة...", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (devices.isEmpty()) {
                    Text(
                        if (isScanning) "لم يظهر أي جهاز بعد..."
                        else "لم يتم العثور على أي جهاز. تأكد أن البلوتوث مفعّل بالطابعة وأنها قريبة، ثم أعد المحاولة."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(devices.values.sortedBy { it.name ?: it.address }) { device ->
                            val isPaired = device.bondState == BluetoothDevice.BOND_BONDED
                            val isPairingThis = pairingAddress == device.address
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        if (isPaired) Icons.Filled.CheckCircle else Icons.Filled.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isPaired) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                headlineContent = { Text(device.name ?: "جهاز غير معروف") },
                                supportingContent = {
                                    Text(
                                        when {
                                            isPairingThis -> "${device.address} - جارٍ الاقتران..."
                                            isPaired -> "${device.address} - مقترن مسبقًا"
                                            else -> "${device.address} - غير مقترن (اضغط للاقتران والاختيار)"
                                        }
                                    )
                                },
                                modifier = Modifier.clickable(enabled = pairingAddress == null) {
                                    errorMessage = null
                                    if (isPaired) {
                                        selectAndStayConnected(device)
                                    } else {
                                        pairingAddress = device.address
                                        val paired = BluetoothPrinterScanner(context).pairDevice(device)
                                        if (!paired) {
                                            pairingAddress = null
                                            errorMessage = "تعذّر بدء الاقتران بهذا الجهاز"
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
