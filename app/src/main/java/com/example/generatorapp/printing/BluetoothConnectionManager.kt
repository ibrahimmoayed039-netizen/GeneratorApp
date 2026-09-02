package com.example.generatorapp.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * يحافظ على اتصال بلوتوث واحد **مفتوح ودائم** مع الطابعة الحرارية، بدل فتح
 * اتصال (Socket) جديد وإغلاقه مع كل عملية طباعة كما كان يحدث سابقًا.
 *
 * بمجرد أن يُقرن (Pair) المستخدم بطابعة من [com.example.generatorapp.ui.components.PrinterDiscoveryDialog]،
 * يُفتح الاتصال فورًا ويبقى محتفَظًا به هنا طوال عمل التطبيق، فتصبح الطابعة
 * "دائمًا متصلة" ولا تحتاج إعادة اتصال قبل كل طباعة. إن انقطع الاتصال فعليًا
 * (الطابعة أُطفئت أو خرجت من النطاق)، تُعاد المحاولة تلقائيًا عند أول طلب طباعة تالٍ.
 *
 * هذا الكائن مُشترك (Singleton) بمعزل عن دورة حياة أي شاشة، ليبقى الاتصال قائمًا
 * حتى عند التنقل بين الشاشات.
 */
object BluetoothConnectionManager {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var connectedAddress: String? = null

    /** هل هناك اتصال مفتوح وصالح حاليًا مع أي طابعة */
    val isConnected: Boolean
        get() = socket?.isConnected == true

    /** عنوان (MAC) الطابعة المتصلة حاليًا، أو null إن لم يكن هناك اتصال */
    val connectedDeviceAddress: String?
        get() = if (isConnected) connectedAddress else null

    /**
     * يعيد تيار الإخراج للاتصال الحالي إن كان مفتوحًا فعلاً مع نفس الجهاز،
     * وإلا يفتح اتصالاً جديدًا ويحتفظ به لإعادة استخدامه لاحقًا (لا يُغلق تلقائيًا).
     * يجب استدعاؤها من Thread خلفية (IO)، فهي تتصل بشكل متزامن (Blocking).
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(device: BluetoothDevice): OutputStream {
        if (isConnected && connectedAddress == device.address) {
            return socket!!.outputStream
        }
        closeQuietly()
        val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            newSocket.connect()
        } catch (e: IOException) {
            closeQuietly(newSocket)
            throw IOException("تعذّر الاتصال بالطابعة الحرارية: ${e.message}")
        }
        socket = newSocket
        connectedAddress = device.address
        return newSocket.outputStream
    }

    /**
     * تُستخدم عند فشل الإرسال على اتصال كان يُفترض أنه سليم (مثلاً الطابعة انقطعت
     * فعليًا دون أن يُلاحَظ ذلك بعد): تُغلق الاتصال القديم وتفتح اتصالاً جديدًا من الصفر.
     */
    fun reconnect(device: BluetoothDevice): OutputStream {
        closeQuietly()
        return connect(device)
    }

    /** يفتح الاتصال بشكل استباقي فور نجاح الاقتران، دون انتظار أول طلب طباعة */
    fun connectQuietly(device: BluetoothDevice): Boolean =
        try {
            connect(device)
            true
        } catch (_: IOException) {
            false
        }

    @Synchronized
    fun disconnect() {
        closeQuietly()
    }

    private fun closeQuietly() {
        socket?.let(::closeQuietly)
        socket = null
        connectedAddress = null
    }

    private fun closeQuietly(s: BluetoothSocket) {
        try {
            s.close()
        } catch (_: IOException) {
        }
    }
}
