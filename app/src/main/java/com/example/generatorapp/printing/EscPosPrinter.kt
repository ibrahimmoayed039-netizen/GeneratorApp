package com.example.generatorapp.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * طباعة على طابعة حرارية (58مم أو 80مم) تدعم أوامر ESC/POS عبر البلوتوث الكلاسيكي (SPP).
 * هذا المسار لا يحتاج مكتبات خارجية — يرسل أوامر ESC/POS مباشرة كبايتات.
 *
 * ملاحظة: عرض السطر يختلف حسب حجم الورق:
 *  - 58مم  ≈ 32 حرف/سطر (خط عادي)
 *  - 80مم  ≈ 48 حرف/سطر (خط عادي)
 */
class EscPosPrinter(private val paperWidthChars: Int = 32) {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // أوامر ESC/POS الأساسية
        private val INIT = byteArrayOf(0x1B, 0x40)
        private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        private val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
        private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        private val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x00)
        private val LINE_FEED = byteArrayOf(0x0A)
    }

    /**
     * الاتصال بالطابعة عبر جهاز بلوتوث مقترن مسبقًا (Paired) وإرسال الوصل.
     * يجب طلب صلاحيات BLUETOOTH_CONNECT في وقت التشغيل قبل استدعاء هذه الدالة.
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceipt(device: BluetoothDevice, receipt: ReceiptData) {
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val output = socket.outputStream
                writeReceipt(output, receipt)
            } catch (e: IOException) {
                throw IOException("تعذّر الاتصال بالطابعة الحرارية: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun writeReceipt(output: OutputStream, receipt: ReceiptData) {
        output.write(INIT)

        output.write(ALIGN_CENTER)
        receipt.logo?.let { logo ->
            printLogo(output, logo)
            output.write(LINE_FEED)
        }

        output.write(BOLD_ON)
        writeArabicLine(output, receipt.shopName)
        output.write(BOLD_OFF)
        writeArabicLine(output, "=".repeat(paperWidthChars))

        output.write(ALIGN_RIGHT)
        for (line in receipt.toLines().drop(1)) {
            writeArabicLine(output, line)
        }

        output.write(ALIGN_CENTER)
        output.write(LINE_FEED)
        output.write(LINE_FEED)
        output.write(CUT_PAPER)
        output.flush()
    }

    /** ترميز CP864/UTF-8 حسب دعم الطابعة؛ نستخدم UTF-8 كافتراضي متوافق مع أغلب الطابعات الحديثة */
    private fun writeArabicLine(output: OutputStream, text: String) {
        output.write(text.toByteArray(Charsets.UTF_8))
        output.write(LINE_FEED)
    }

    /**
     * يحوّل شعار المحل إلى صورة أحادية اللون (Monochrome) ويطبعها عبر أمر
     * GS v 0 (Raster Bit Image) المدعوم في أغلب الطابعات الحرارية ESC/POS.
     *
     * عرض الطباعة بالنقاط يُحسب تلقائيًا من عدد أحرف السطر:
     *  - 32 حرف (58مم)  ≈ 384 نقطة
     *  - 48 حرف (80مم)  ≈ 576 نقطة
     */
    private fun printLogo(output: OutputStream, bitmap: Bitmap) {
        val printerWidthDots = if (paperWidthChars >= 48) 576 else 384

        // تصغير الصورة لعرض الطابعة مع الحفاظ على النسبة
        val ratio = printerWidthDots.toFloat() / bitmap.width
        val targetWidth = printerWidthDots
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        val widthBytes = (targetWidth + 7) / 8
        val raster = ByteArray(widthBytes * targetHeight)

        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val pixel = scaled.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                // اعتبر الخلفية الشفافة بيضاء؛ اطبع النقطة (أسود) إن كانت داكنة بما فيه الكفاية
                val isBlack = alpha > 128 && gray < 160
                if (isBlack) {
                    val byteIndex = y * widthBytes + (x / 8)
                    val bitIndex = 7 - (x % 8)
                    raster[byteIndex] = (raster[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }

        val xL = widthBytes and 0xFF
        val xH = (widthBytes shr 8) and 0xFF
        val yL = targetHeight and 0xFF
        val yH = (targetHeight shr 8) and 0xFF

        val command = ByteArrayOutputStream()
        command.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00)) // GS v 0 m(=0)
        command.write(byteArrayOf(xL.toByte(), xH.toByte(), yL.toByte(), yH.toByte()))
        command.write(raster)

        output.write(command.toByteArray())
    }
}
