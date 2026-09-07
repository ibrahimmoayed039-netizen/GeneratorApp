package com.example.generatorapp.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 *
 * [lineSpacingDots] يتحكم بالمسافة العمودية بين كل سطرين متتاليين بالفاتورة (أمر
 * ESC/POS "ESC 3 n")، مما يسمح بتقريب الأسطر من بعضها (فاتورة مضغوطة توفّر بالورق)
 * أو تباعدها. راجع [ShopInfoManager.LINE_SPACING_COMPACT]/[ShopInfoManager.LINE_SPACING_NORMAL]/
 * [ShopInfoManager.LINE_SPACING_WIDE] للقيم الجاهزة المستخدمة بشاشة الإعدادات.
 */
class EscPosPrinter(
    private val paperWidthChars: Int = 32,
    private val lineSpacingDots: Int = ShopInfoManager.DEFAULT_LINE_SPACING
) {

    companion object {
        // أوامر ESC/POS الأساسية
        private val INIT = byteArrayOf(0x1B, 0x40)
        private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        private val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
        private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        private val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x00)
        private val LINE_FEED = byteArrayOf(0x0A)
        /** ESC 3 n — يضبط تباعد الأسطر لـ n نقطة؛ n=0x1B,0x33 متبوعة بقيمة النقاط */
        private const val SET_LINE_SPACING_CMD: Byte = 0x33

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
        // نضبط تباعد الأسطر مرة واحدة أول الوصل؛ يبقى ساريًا على كل أوامر LINE_FEED
        // (0x0A) اللاحقة بما فيها الأسطر المطبوعة كصور (bitmaps) — فيتقارب أو يتباعد
        // كل سطر بالفاتورة تلقائيًا حسب [lineSpacingDots] دون تعديل كل سطر يدويًا.
        output.write(byteArrayOf(0x1B, SET_LINE_SPACING_CMD, lineSpacingDots.coerceIn(0, 255).toByte()))

        output.write(ALIGN_CENTER)
        receipt.logo?.let { logo ->
            printLogo(output, logo)
            output.write(LINE_FEED)
        }

        val printerWidthDots = if (paperWidthChars >= 48) 576 else 384

        for (line in receipt.toReceiptLines()) {
            when (line) {
                is ReceiptLine.Header -> {
                    // اسم المحل داخل لوحة مؤطّرة (إطار مربّع) ليبرز كترويسة مميّزة أعلى الوصل
                    val headerBitmap = renderTextLineBitmap(
                        line.text, printerWidthDots, bold = true, alignment = Layout.Alignment.ALIGN_CENTER
                    )
                    val framed = frameBitmap(
                        headerBitmap,
                        verticalPadding = 14,
                        borderWidth = 3,
                        marginDots = (printerWidthDots * 0.05f).toInt()
                    )
                    output.write(ALIGN_CENTER)
                    printRasterBitmap(output, framed)
                    output.write(LINE_FEED)

                    // رقم الهاتف (إن وُجد) يُطبع مباشرة أسفل ترويسة اسم المحل، بخط عادي غير مؤطّر
                    if (line.phone.isNotBlank()) {
                        writeArabicLine(output, "هاتف: ${line.phone}", bold = false, alignment = Layout.Alignment.ALIGN_CENTER)
                    }
                }

                is ReceiptLine.Badge ->
                    writeArabicLine(output, line.text, bold = true, alignment = Layout.Alignment.ALIGN_CENTER)

                is ReceiptLine.Divider -> {
                    val char = if (line.double) "=" else "-"
                    output.write(ALIGN_CENTER)
                    output.write(char.repeat(paperWidthChars).toByteArray(Charsets.US_ASCII))
                    output.write(LINE_FEED)
                }

                is ReceiptLine.Field -> {
                    // عناوين أقسام فرعية تفصل بيانات المشترك عن تفاصيل الاشتراك/الدفع بصريًا
                    when (line.label) {
                        "التاريخ" -> writeSectionTitle(output, "بيانات المشترك")
                        "عدد الأمبيرات" -> {
                            writeLightDivider(output)
                            writeSectionTitle(output, "تفاصيل الاشتراك")
                        }
                    }
                    writeFieldLine(output, line.label, line.value, printerWidthDots)
                }

                is ReceiptLine.Total -> {
                    // المبلغ الإجمالي داخل صندوق مؤطّر بخط كبير غامق ليكون أبرز عنصر بالوصل
                    writeFieldLine(output, line.label, line.value, printerWidthDots, emphasize = true, framed = true)
                }

                is ReceiptLine.Note ->
                    writeArabicLine(output, line.text, alignment = Layout.Alignment.ALIGN_OPPOSITE)

                is ReceiptLine.Footer -> {
                    // خط متقطّع يحاكي خط قص الوصل الحقيقي قبل عبارة الشكر
                    writeLightDivider(output)
                    writeArabicLine(output, line.text, alignment = Layout.Alignment.ALIGN_CENTER)
                }
            }
        }

        output.write(ALIGN_CENTER)
        output.write(LINE_FEED)
        output.write(CUT_PAPER)
        output.flush()
    }

    /** يطبع عنوان قسم فرعي (مثل "بيانات المشترك") بخط غامق وسط الصفحة، محاط برمزين صغيرين للتمييز */
    private fun writeSectionTitle(output: OutputStream, title: String) {
        writeArabicLine(output, "• $title •", bold = true, alignment = Layout.Alignment.ALIGN_CENTER)
    }

    /** يطبع فاصلًا خفيفًا متقطّعًا (نقاط) يفصل بين أقسام فرعية دون ثقل بصري خط الفاصل الرئيسي */
    private fun writeLightDivider(output: OutputStream) {
        output.write(ALIGN_CENTER)
        val pattern = "- ".repeat(paperWidthChars / 2 + 1).take(paperWidthChars)
        output.write(pattern.toByteArray(Charsets.US_ASCII))
        output.write(LINE_FEED)
    }

    /**
     * يحيط بتمابًا (نص أو حقل مرسوم) بإطار مربّع أسود، لإبراز عناصر مهمة (اسم المحل،
     * المبلغ الإجمالي) كصندوق مميّز داخل الوصل بدل نص عادي متصل بباقي الأسطر.
     */
    private fun frameBitmap(content: Bitmap, verticalPadding: Int, borderWidth: Int, marginDots: Int): Bitmap {
        val width = content.width
        val height = content.height + verticalPadding * 2
        val framed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(framed)
        canvas.drawColor(Color.WHITE)
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = borderWidth.toFloat()
            isAntiAlias = true
        }
        canvas.drawRect(
            marginDots.toFloat(),
            borderWidth / 2f,
            (width - marginDots).toFloat(),
            (height - borderWidth / 2f),
            borderPaint
        )
        canvas.drawBitmap(content, 0f, verticalPadding.toFloat(), null)
        return framed
    }

    /**
     * يطبع حقل "تسمية: قيمة" في عمودين منفصلين على نفس السطر — التسمية عند الحافة اليمنى
     * والقيمة عند الحافة اليسرى (تمامًا كصف [com.example.generatorapp.ui.components.ReceiptPreview]
     * في معاينة الشاشة) — بدل سطر نصي واحد ملتصق، فتصطف كل القيم عموديًا في عمود واحد
     * ويبدو الوصل كفاتورة حقيقية بدل نص متتابع. إن كانت التسمية والقيمة طويلتين لدرجة
     * تمنع فصلهما بوضوح ضمن عرض الورق، يُرجع تلقائيًا لسطر واحد ملتصق (لتفادي التداخل).
     */
    private fun writeFieldLine(
        output: OutputStream,
        label: String,
        value: String,
        printerWidthDots: Int,
        emphasize: Boolean = false,
        framed: Boolean = false
    ) {
        val bitmap = renderFieldLineBitmap(label, value, printerWidthDots, emphasize)
        if (bitmap == null) {
            // لا مساحة كافية لعمودين منفصلين — سطر ملتصق واحد كحل احتياطي آمن
            writeArabicLine(output, "$label: $value", bold = emphasize, alignment = Layout.Alignment.ALIGN_OPPOSITE)
            return
        }
        val finalBitmap = if (framed) {
            frameBitmap(bitmap, verticalPadding = 10, borderWidth = 3, marginDots = (printerWidthDots * 0.05f).toInt())
        } else {
            bitmap
        }
        output.write(ALIGN_CENTER)
        printRasterBitmap(output, finalBitmap)
        output.write(LINE_FEED)
    }

    /** يرسم سطر "تسمية ... قيمة" بعمودين محاذاة يمين/يسار، أو null إن لم تتسع المساحة لفصلهما بوضوح */
    private fun renderFieldLineBitmap(
        label: String,
        value: String,
        widthDots: Int,
        emphasize: Boolean
    ): Bitmap? {
        val baseSizePx = if (widthDots >= 576) 30f else 26f
        val valueSizePx = if (emphasize) baseSizePx * 1.25f else baseSizePx
        val labelSizePx = baseSizePx * 0.92f

        val labelPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = labelSizePx
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val valuePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = valueSizePx
            isFakeBoldText = emphasize
            textAlign = android.graphics.Paint.Align.LEFT
        }

        val labelText = "$label:"
        val minGapDots = widthDots * 0.08f
        val labelWidth = labelPaint.measureText(labelText)
        val valueWidth = valuePaint.measureText(value)

        // إن كان مجموع عرض التسمية + القيمة + هامش فاصل يتجاوز عرض الورق، لا مجال لعمودين
        // واضحين بدون تداخل النصين — نرجع null ليستخدم المستدعي سطرًا ملتصقًا احتياطيًا
        if (labelWidth + valueWidth + minGapDots > widthDots) return null

        val labelHeight = labelPaint.descent() - labelPaint.ascent()
        val valueHeight = valuePaint.descent() - valuePaint.ascent()
        val height = maxOf(labelHeight, valueHeight).let { (it * 1.2f).toInt() }.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val labelBaseline = height / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f
        val valueBaseline = height / 2f - (valuePaint.ascent() + valuePaint.descent()) / 2f

        canvas.drawText(labelText, widthDots.toFloat(), labelBaseline, labelPaint)
        canvas.drawText(value, 0f, valueBaseline, valuePaint)

        return bitmap
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
