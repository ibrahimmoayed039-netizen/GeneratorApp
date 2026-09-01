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
    return adapter.bondedDevices?.firstOrNull()
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
