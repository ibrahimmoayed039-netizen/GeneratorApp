package com.example.generatorapp.printing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * يبحث فعليًا (Scan/Discovery) عن أجهزة بلوتوث قريبة قابلة للاكتشاف — سواء كانت
 * مقترنة (Paired) بالهاتف من قبل أو لا. هذا يختلف عن [getPairedBluetoothDevices]
 * التي تعرض فقط الأجهزة المقترنة مسبقًا، ولا تكتشف طابعة جديدة لم تُقرن بعد.
 *
 * يُستخدم أيضًا لقرن (Pair) طابعة جديدة تلقائيًا عند اختيارها من نتائج البحث.
 */
class BluetoothPrinterScanner(private val context: Context) {

    private var discoveryReceiver: BroadcastReceiver? = null
    private var bondReceiver: BroadcastReceiver? = null

    /**
     * يبدأ بحثًا فعليًا عن أجهزة قريبة. يستدعي [onDeviceFound] فورًا لكل الأجهزة
     * المقترنة سابقًا (حتى لا تختفي من القائمة)، ثم لكل جهاز جديد يُكتشف أثناء البحث.
     * يستدعي [onFinished] عند انتهاء البحث (يستغرق عادة حوالي 12 ثانية).
     */
    @Suppress("MissingPermission")
    fun startScan(onDeviceFound: (BluetoothDevice) -> Unit, onFinished: () -> Unit) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            onFinished()
            return
        }

        stopScan() // احتياطًا لو كان هناك بحث سابق لم يُنظَّف بعد

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> extractDevice(intent)?.let(onDeviceFound)
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onFinished()
                }
            }
        }
        discoveryReceiver = receiver
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // الأجهزة المقترنة مسبقًا تظهر فورًا (البحث الفعلي لا يكتشف الأجهزة المقترنة بنفس الطريقة)
        adapter.bondedDevices?.forEach(onDeviceFound)

        adapter.cancelDiscovery()
        adapter.startDiscovery()
    }

    /** يوقف البحث الحالي ويلغي تسجيل المستقبِل (Receiver) لتفادي تسريب الذاكرة */
    @Suppress("MissingPermission")
    fun stopScan() {
        discoveryReceiver?.let(::safeUnregister)
        discoveryReceiver = null
        BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
            if (adapter.isDiscovering) adapter.cancelDiscovery()
        }
    }

    /** يراقب تغيّر حالة الاقتران (Pairing) لأي جهاز، لتحديث الواجهة عند نجاح/فشل القرن */
    fun watchBonding(onBondStateChanged: (BluetoothDevice, Int) -> Unit) {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val device = extractDevice(intent) ?: return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                onBondStateChanged(device, state)
            }
        }
        bondReceiver = receiver
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun stopWatchingBonding() {
        bondReceiver?.let(::safeUnregister)
        bondReceiver = null
    }

    /** يطلب قرن (Pair) جهاز غير مقترن بعد؛ النتيجة الفعلية تصل عبر [watchBonding] */
    @Suppress("MissingPermission")
    fun pairDevice(device: BluetoothDevice): Boolean =
        try {
            device.createBond()
        } catch (e: SecurityException) {
            false
        }

    private fun extractDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun safeUnregister(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // كان غير مسجَّل أصلًا، تجاهل
        }
    }
}
