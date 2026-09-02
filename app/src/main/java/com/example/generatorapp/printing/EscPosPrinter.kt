package com.example.generatorapp.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

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
        // أوامر ESC/POS الأساسية
        private val INIT = byteArrayOf(0x1B, 0x40)
        private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        private val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
        private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        private val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x00)
        private val LINE_FEED = byteArrayOf(0x0A)

        /** جملة عربية قصيرة تُستخدم في صفحة اختبار جداول الحروف */
        private const val CHARSET_TEST_SENTENCE = "بسم الله الرحمن الرحيم"
    }

    /**
     * يطبع الوصل عبر الاتصال الدائم بالطابعة الذي يديره [BluetoothConnectionManager]
     * (يبقى الاتصال مفتوحًا من عملية الاقتران السابقة بدل فتحه وإغلاقه مع كل طباعة).
     * يجب طلب صلاحيات BLUETOOTH_CONNECT في وقت التشغيل قبل استدعاء هذه الدالة.
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceipt(device: BluetoothDevice, receipt: ReceiptData) {
        withContext(Dispatchers.IO) {
            sendWithPersistentConnection(device) { output -> writeReceipt(output, receipt) }
        }
    }

    /**
     * يتصل بالطابعة الحرارية ويطبع صفحة اختبار جداول الحروف (Code Pages):
     * نفس الجملة العربية تُطبع مكرّرة أسفل كل رقم جدول حروف من CP0 إلى CP47، بالإضافة إلى CP255،
     * لمساعدتك على تحديد رقم الجدول (codePage) الذي يعرض العربية بشكل صحيح على طابعتك المحددة.
     * استخدم الرقم الذي تظهر أسفله الجملة سليمة كقيمة codePage عند إنشاء EscPosCharsetEncoding.
     */
    @SuppressLint("MissingPermission")
    suspend fun printCharsetTestPage(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            sendWithPersistentConnection(device) { output -> writeCharsetTestPage(output) }
        }
    }

    /**
     * يرسل بيانات الطباعة عبر الاتصال الدائم المُدار بواسطة [BluetoothConnectionManager]
     * بدل فتح اتصال جديد مع كل طباعة. إذا فشل الإرسال على الاتصال الحالي (مثلاً لأن
     * الطابعة انقطعت فعليًا منذ آخر استخدام)، يُحاوَل إعادة الاتصال مرة واحدة تلقائيًا
     * قبل الاستسلام والإبلاغ عن الخطأ — هذا ما يجعل الطابعة تبدو "دائمًا متصلة" من
     * وجهة نظر المستخدم رغم أي انقطاع عرضي.
     */
    @SuppressLint("MissingPermission")
    private fun sendWithPersistentConnection(device: BluetoothDevice, block: (OutputStream) -> Unit) {
        try {
            block(BluetoothConnectionManager.connect(device))
        } catch (e: IOException) {
            try {
                block(BluetoothConnectionManager.reconnect(device))
            } catch (e2: IOException) {
                throw IOException("تعذّر الاتصال بالطابعة الحرارية: ${e2.message}")
            }
        }
    }

    private fun writeCharsetTestPage(output: OutputStream) {
        output.write(INIT)
        output.write(ALIGN_CENTER)
        output.write(BOLD_ON)
        output.write("اختبار جداول الحروف".toByteArray(charset("windows-1256")))
        output.write(BOLD_OFF)
        output.write(LINE_FEED)
        output.write("=".repeat(paperWidthChars).toByteArray(Charsets.US_ASCII))
        output.write(LINE_FEED)
        output.write(ALIGN_RIGHT)

        // نرمّز الجملة العربية مرة واحدة بترميز Windows-1256 (الأكثر شيوعًا في طابعات ESC/POS)؛
        // البايتات نفسها تُرسل تحت كل رقم جدول حروف، والطابعة هي من تفسّرها بشكل مختلف حسب
        // الجدول المفعّل، لذلك يظهر النص سليمًا فقط تحت الرقم المطابق فعليًا لتلك الطابعة.
        val arabicBytes = try {
            CHARSET_TEST_SENTENCE.toByteArray(charset("windows-1256"))
        } catch (e: Exception) {
            CHARSET_TEST_SENTENCE.toByteArray(Charsets.UTF_8)
        }

        val codePagesToTest = (0..47).toList() + listOf(255)
        for (cp in codePagesToTest) {
            // "CP<رقم>: " بأحرف/أرقام إنجليزية (ASCII) تبقى صحيحة بأي جدول حروف
            output.write("CP$cp: ".toByteArray(Charsets.US_ASCII))
            output.write(byteArrayOf(0x1B, 0x74, cp.toByte())) // ESC t n — اختيار جدول الحروف
            output.write(arabicBytes)
            output.write(LINE_FEED)
        }

        output.write(ALIGN_CENTER)
        output.write(LINE_FEED)
        output.write(LINE_FEED)
        output.write(CUT_PAPER)
        output.flush()
    }

    private fun writeReceipt(output: OutputStream, receipt: ReceiptData) {
        output.write(INIT)

        output.write(ALIGN_CENTER)
        receipt.logo?.let { logo ->
            printLogo(output, logo)
            output.write(LINE_FEED)
        }

        writeArabicLine(output, receipt.shopName, bold = true, alignment = Layout.Alignment.ALIGN_CENTER)
        writeArabicLine(output, "=".repeat(paperWidthChars), alignment = Layout.Alignment.ALIGN_CENTER)

        for (line in receipt.toLines().drop(1)) {
            writeArabicLine(output, line, alignment = Layout.Alignment.ALIGN_OPPOSITE)
        }

        output.write(ALIGN_CENTER)
        output.write(LINE_FEED)
        output.write(LINE_FEED)
        output.write(CUT_PAPER)
        output.flush()
    }

    /**
     * كثير من الطابعات الحرارية الرخيصة (خصوصًا ذات المنشأ الصيني) لا تدعم فعليًا أي جدول
     * حروف عربي رغم استجابتها الظاهرية لأمر "ESC t n" — فهي تتجاهله وتطبع أي بايت غير-ASCII
     * حسب جدولها الداخلي الثابت (عادة صيني GBK)، فيظهر العربي كرموز مشوّهة بغض النظر عن
     * الترميز المُرسَل (UTF-8 أو Windows-1256 أو غيره) وبغض النظر عن رقم CP المُختار.
     *
     * الحل الموثوق الوحيد مع هذه الطابعات هو تفادي جداول الحروف نهائيًا: نرسم السطر كصورة
     * (Bitmap) باستخدام محرك الرسم في أندرويد — الذي يدعم تشكيل واتجاه العربية (RTL) بشكل
     * صحيح تلقائيًا — ثم نطبعها كصورة نقطية عبر أمر GS v 0، بالضبط كما يحدث مع الشعار.
     * الأسطر التي تحتوي أحرف ASCII فقط (كخط الفواصل "====") تُرسل كنص عادي لأنها لا تتأثر
     * بجدول الحروف وتطبع أسرع.
     */
    private fun writeArabicLine(
        output: OutputStream,
        text: String,
        bold: Boolean = false,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ) {
        if (text.isEmpty()) {
            output.write(LINE_FEED)
            return
        }

        if (isAsciiOnly(text)) {
            when (alignment) {
                Layout.Alignment.ALIGN_CENTER -> output.write(ALIGN_CENTER)
                Layout.Alignment.ALIGN_OPPOSITE -> output.write(ALIGN_RIGHT)
                else -> output.write(byteArrayOf(0x1B, 0x61, 0x00))
            }
            if (bold) output.write(BOLD_ON)
            output.write(text.toByteArray(Charsets.US_ASCII))
            if (bold) output.write(BOLD_OFF)
            output.write(LINE_FEED)
            return
        }

        val printerWidthDots = if (paperWidthChars >= 48) 576 else 384
        val lineBitmap = renderTextLineBitmap(text, printerWidthDots, bold, alignment)
        output.write(ALIGN_CENTER) // البتمابات تُرسل بعرض الصفحة كاملًا؛ المحاذاة الفعلية مرسومة داخل الصورة نفسها
        printRasterBitmap(output, lineBitmap)
        output.write(LINE_FEED)
    }

    private fun isAsciiOnly(text: String): Boolean = text.all { it.code < 128 }

    /** يرسم سطر نص عربي واحد كصورة أحادية اللون بعرض الطابعة، مع محاذاة ودعم غامق اختياري */
    private fun renderTextLineBitmap(
        text: String,
        widthDots: Int,
        bold: Boolean,
        alignment: Layout.Alignment
    ): Bitmap {
        val textSizePx = if (widthDots >= 576) 30f else 26f
        val paint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = textSizePx
            isFakeBoldText = bold
        }

        @Suppress("DEPRECATION")
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthDots)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        val height = layout.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        layout.draw(canvas)
        return bitmap
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

        printRasterBitmap(output, scaled)
    }

    /**
     * يحوّل أي Bitmap (شعار أو سطر نص مرسوم) إلى صورة أحادية اللون ويطبعها عبر أمر
     * GS v 0 (Raster Bit Image) المدعوم في أغلب الطابعات الحرارية ESC/POS.
     * يُفترض أن عرض الـ bitmap مطابق بالفعل لعرض الطابعة بالنقاط (لا يتم تصغيره هنا).
     */
    private fun printRasterBitmap(output: OutputStream, bitmap: Bitmap) {
        val targetWidth = bitmap.width
        val targetHeight = bitmap.height
        val widthBytes = (targetWidth + 7) / 8
        val raster = ByteArray(widthBytes * targetHeight)

        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val pixel = bitmap.getPixel(x, y)
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
