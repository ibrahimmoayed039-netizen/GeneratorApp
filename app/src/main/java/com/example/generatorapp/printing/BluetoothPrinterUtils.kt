package com.example.generatorapp.printing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context

/**
 * يبحث عن أول جهاز بلوتوث مقترن (Paired) لاستخدامه كطابعة حرارية.
 * دالة مشتركة تُستخدم من أكثر من شاشة (الفواتير، الدفع السريع من قائمة العملاء).
 */
@Suppress("MissingPermission")
fun findFirstPairedThermalPrinter(context: Context): BluetoothDevice? {
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
    if (!adapter.isEnabled) return null
    return try {
        adapter.bondedDevices?.firstOrNull()
    } catch (e: SecurityException) {
        // لم يتم منح صلاحية BLUETOOTH_CONNECT (أندرويد 12+) — يجب طلبها من واجهة المستخدم
        // قبل استدعاء هذه الدالة؛ هنا فقط نتفادى تحطّم التطبيق ونعيد null بدلاً من ذلك.
        null
    }
}

/**
 * يرجّع كل أجهزة البلوتوث المقترنة (Paired) بالجهاز، لعرضها كقائمة يختار المستخدم منها
 * الطابعة الحرارية المطلوبة، بدل الاعتماد التلقائي على أول جهاز مقترن.
 */
@Suppress("MissingPermission")
fun getPairedBluetoothDevices(context: Context): List<BluetoothDevice> {
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
    if (!adapter.isEnabled) return emptyList()
    return adapter.bondedDevices?.toList() ?: emptyList()
}

private const val PRINTER_PREFS_NAME = "thermal_printer_prefs"
private const val KEY_PRINTER_ADDRESS = "saved_printer_address"

/**
 * يحفظ عنوان (MAC) الطابعة الحرارية التي اختارها المستخدم من شاشة الإعدادات
 * (فحص/اقتران الطابعة)، لتصبح هي الطابعة الافتراضية المستخدمة تلقائيًا في كل
 * أزرار "طباعة حرارية" بالتطبيق، بدل الاعتماد على أول جهاز بلوتوث مقترن عشوائيًا.
 */
fun savePreferredPrinterAddress(context: Context, address: String) {
    context.getSharedPreferences(PRINTER_PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_PRINTER_ADDRESS, address).apply()
}

fun getPreferredPrinterAddress(context: Context): String? =
    context.getSharedPreferences(PRINTER_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PRINTER_ADDRESS, null)

/**
 * يرجّع الطابعة الحرارية التي حفظها المستخدم مسبقًا من شاشة الإعدادات إن وُجدت
 * (وكانت لا تزال ضمن الأجهزة المقترنة)، وإلا يرجع null بدل التخمين بجهاز عشوائي.
 */
@Suppress("MissingPermission")
fun findPreferredThermalPrinter(context: Context): BluetoothDevice? {
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
    if (!adapter.isEnabled) return null
    val savedAddress = getPreferredPrinterAddress(context) ?: return null
    return try {
        adapter.bondedDevices?.firstOrNull { it.address == savedAddress }
    } catch (e: SecurityException) {
        null
    }
}
