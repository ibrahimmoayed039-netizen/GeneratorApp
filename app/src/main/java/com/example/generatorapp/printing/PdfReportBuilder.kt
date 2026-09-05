package com.example.generatorapp.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.example.generatorapp.util.DateUtils
import java.io.File
import java.io.FileOutputStream

/**
 * أداة عامة لبناء تقارير PDF احترافية متعددة الصفحات: ترويسة ملوّنة بشعار المحل،
 * أقسام وعناوين، جداول بيانات منسّقة (صفوف متناوبة الألوان)، صناديق تمييز للإجماليات،
 * وتذييل ثابت مع ترقيم صفحات تلقائي عند الحاجة.
 *
 * تُستخدم من [PdfReportGenerator] لتصدير: كشف الحساب الشهري، تقرير الأرباح، وقائمة المشتركين.
 */
class PdfReportBuilder(
    private val context: Context,
    private val reportTitle: String,
    private val logo: Bitmap?
) {
    companion object {
        private const val PAGE_WIDTH = 595   // A4 عند 72dpi
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 36f
        private const val HEADER_BAND_HEIGHT = 90f
        private const val CONTENT_BOTTOM_LIMIT = PAGE_HEIGHT - 56f

        private val PRIMARY = Color.parseColor("#2F6B4F")
        private val LIGHT_GRAY = Color.parseColor("#F2F2F2")
        private val BORDER = Color.parseColor("#DDDDDD")
        private val TEXT_DARK = Color.parseColor("#222222")
        private val TEXT_MUTED = Color.parseColor("#808080")
    }

    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNumber = 0
    private val contentWidth = PAGE_WIDTH - 2 * MARGIN

    private val titlePaint = TextPaint().apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
    private val subtitlePaint = TextPaint().apply { color = Color.WHITE; textSize = 10.5f; isAntiAlias = true }
    private val headerCellPaint = TextPaint().apply { color = Color.WHITE; textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }
    private val bodyCellPaint = TextPaint().apply { color = TEXT_DARK; textSize = 10.5f; isAntiAlias = true }
    private val labelPaint = TextPaint().apply { color = TEXT_DARK; textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }
    private val sectionPaint = TextPaint().apply { color = TEXT_DARK; textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
    private val mutedPaint = TextPaint().apply { color = TEXT_MUTED; textSize = 9f; isAntiAlias = true }
    private val shopNamePaint = TextPaint().apply { color = Color.WHITE; textSize = 11.5f; isFakeBoldText = true; isAntiAlias = true }
    private val shopPhonePaint = TextPaint().apply { color = Color.WHITE; textSize = 9.5f; alpha = 210; isAntiAlias = true }

    init {
        startNewPage()
    }

    private fun startNewPage() {
        finishCurrentPage()
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val newPage = document.startPage(info)
        page = newPage
        canvas = newPage.canvas
        drawHeaderBand()
        y = HEADER_BAND_HEIGHT + 26f
    }

    private fun drawHeaderBand() {
        val c = canvas ?: return
        val bandPaint = Paint().apply { color = PRIMARY }
        c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), HEADER_BAND_HEIGHT, bandPaint)

        var logoTextX = MARGIN
        logo?.let { bmp ->
            val size = 56
            val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
            c.drawBitmap(scaled, MARGIN, (HEADER_BAND_HEIGHT - size) / 2, null)
            logoTextX = MARGIN + size + 10f
        }

        // اسم المحل/المولدة ورقم الهاتف بجانب الشعار — نفس هوية المحل الظاهرة أعلى وصولات الدفع
        val shopName = ShopInfoManager.getShopName(context)
        val shopPhone = ShopInfoManager.getShopPhone(context)
        text(c, shopName, logoTextX, (HEADER_BAND_HEIGHT / 2) - (if (shopPhone.isNotBlank()) 4f else -4f), shopNamePaint, Paint.Align.LEFT)
        if (shopPhone.isNotBlank()) {
            text(c, "هاتف: $shopPhone", logoTextX, (HEADER_BAND_HEIGHT / 2) + 12f, shopPhonePaint, Paint.Align.LEFT)
        }

        text(c, reportTitle, PAGE_WIDTH - MARGIN, 42f, titlePaint, Paint.Align.RIGHT)
        text(
            c,
            "تاريخ الإصدار: ${DateUtils.formatDate(System.currentTimeMillis())}",
            PAGE_WIDTH - MARGIN, 66f, subtitlePaint, Paint.Align.RIGHT
        )
    }

    private fun drawFooter() {
        val c = canvas ?: return
        val linePaint = Paint().apply { color = BORDER; strokeWidth = 1f }
        c.drawLine(MARGIN, PAGE_HEIGHT - 40f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 40f, linePaint)
        text(c, "صفحة $pageNumber", PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 24f, mutedPaint, Paint.Align.RIGHT)
        text(c, "تم إنشاؤه بواسطة تطبيق مدير المولدات", MARGIN, PAGE_HEIGHT - 24f, mutedPaint, Paint.Align.LEFT)
    }

    private fun finishCurrentPage() {
        val p = page ?: return
        drawFooter()
        document.finishPage(p)
    }

    private fun ensureSpace(needed: Float) {
        if (y + needed > CONTENT_BOTTOM_LIMIT) startNewPage()
    }

    private fun text(c: Canvas, str: String, x: Float, yPos: Float, paint: TextPaint, align: Paint.Align) {
        paint.textAlign = align
        c.drawText(str, x, yPos, paint)
    }

    /** عنوان قسم فرعي داخل التقرير (مثال: "تفاصيل الفواتير") مع خط فاصل تحته */
    fun sectionTitle(title: String) {
        ensureSpace(34f)
        val c = canvas ?: return
        text(c, title, PAGE_WIDTH - MARGIN, y, sectionPaint, Paint.Align.RIGHT)
        y += 8f
        val linePaint = Paint().apply { color = PRIMARY; strokeWidth = 2f }
        c.drawLine(PAGE_WIDTH - MARGIN, y, MARGIN, y, linePaint)
        y += 20f
    }

    /** سطر "تسمية: قيمة" بسيط، مفيد لبيانات الترويسة (اسم المشترك، الفترة...) */
    fun keyValue(label: String, value: String) {
        ensureSpace(22f)
        val c = canvas ?: return
        text(c, "$label:", PAGE_WIDTH - MARGIN, y, labelPaint, Paint.Align.RIGHT)
        text(c, value, PAGE_WIDTH - MARGIN - 130f, y, bodyCellPaint, Paint.Align.RIGHT)
        y += 20f
    }

    /** صندوق ملوّن بارز لعرض إجمالي مهم (الإجمالي، صافي الربح...) */
    fun highlightBox(label: String, value: String, color: Int = PRIMARY) {
        ensureSpace(52f)
        val c = canvas ?: return
        val boxPaint = Paint().apply { this.color = color }
        c.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 42f), 8f, 8f, boxPaint)
        val lPaint = TextPaint().apply { this.color = Color.WHITE; textSize = 11f; isAntiAlias = true }
        val vPaint = TextPaint().apply { this.color = Color.WHITE; textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        text(c, label, PAGE_WIDTH - MARGIN - 14f, y + 17f, lPaint, Paint.Align.RIGHT)
        text(c, value, PAGE_WIDTH - MARGIN - 14f, y + 35f, vPaint, Paint.Align.RIGHT)
        y += 52f
    }

    /**
     * جدول احترافي: صف عناوين بخلفية داكنة + صفوف بيانات متناوبة الألوان.
     * الجدول RTL: أول عمود في [headers]/[rows] يُرسم أقصى اليمين.
     * [weights] توزيع نسبي لعرض كل عمود (المجموع تقريبًا 1.0).
     */
    fun table(headers: List<String>, rows: List<List<String>>, weights: List<Float>) {
        val rowHeight = 24f
        val colWidths = weights.map { it * contentWidth }

        ensureSpace(rowHeight)
        drawRow(headers, colWidths, rowHeight, headerCellPaint, PRIMARY, Color.WHITE)

        if (rows.isEmpty()) {
            ensureSpace(rowHeight)
            drawRow(listOf("لا توجد بيانات لهذه الفترة"), listOf(contentWidth), rowHeight, bodyCellPaint, Color.WHITE, TEXT_MUTED)
            return
        }

        rows.forEachIndexed { index, row ->
            ensureSpace(rowHeight)
            val bg = if (index % 2 == 0) Color.WHITE else LIGHT_GRAY
            drawRow(row, colWidths, rowHeight, bodyCellPaint, bg, TEXT_DARK)
        }
    }

    private fun drawRow(cells: List<String>, colWidths: List<Float>, rowHeight: Float, paint: TextPaint, bgColor: Int, textColor: Int) {
        val c = canvas ?: return
        val bgPaint = Paint().apply { color = bgColor }
        val borderPaint = Paint().apply { color = BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }

        c.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, bgPaint)
        c.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, borderPaint)

        paint.color = textColor
        var xRight = PAGE_WIDTH - MARGIN
        cells.forEachIndexed { i, cellText ->
            val w = colWidths.getOrElse(i) { contentWidth }
            text(c, truncate(cellText, paint, w - 12f), xRight - 6f, y + rowHeight / 2 + 4f, paint, Paint.Align.RIGHT)
            xRight -= w
        }
        y += rowHeight
    }

    private fun truncate(str: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(str) <= maxWidth) return str
        var t = str
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    fun spacer(height: Float = 12f) { y += height }

    /** ينهي التقرير ويحفظه كملف PDF داخل تخزين التطبيق، ويرجع الملف الناتج */
    fun save(fileName: String): File {
        finishCurrentPage()
        page = null

        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "reports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }
}
