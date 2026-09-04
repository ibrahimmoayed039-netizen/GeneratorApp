package com.example.generatorapp.printing

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream
import java.io.IOException

/**
 * محوّل طباعة يرسم نفس محتوى الوصل (ReceiptData) على صفحة PDF
 * ليُطبع عبر أي طابعة عادية مسجّلة في نظام أندرويد (خدمة الطباعة الافتراضية).
 */
class SystemPrintAdapter(
    private val receipt: ReceiptData
) : PrintDocumentAdapter() {

    private var pdfDocument: PdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        pdfDocument = PdfDocument()

        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder("وصل_دفع.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        val document = pdfDocument ?: PdfDocument().also { pdfDocument = it }

        // حجم صفحة مناسب لوصل قصير بتصميم احترافي (شريط علوي + شارة + بطاقة حقول)
        val pageInfo = PdfDocument.PageInfo.Builder(420, 660, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // ألوان الهوية: أخضر داكن (الشريط العلوي)، ذهبي (الشارة والإبراز)، بيج فاتح (تظليل الصفوف)
        val headerColor = android.graphics.Color.parseColor("#0B4F44")
        val goldColor = android.graphics.Color.parseColor("#C9922B")
        val rowAltColor = android.graphics.Color.parseColor("#F5F1EA")
        val totalBgColor = android.graphics.Color.parseColor("#EAF3F0")
        val darkTextColor = android.graphics.Color.parseColor("#1F2E2B")
        val borderColor = android.graphics.Color.parseColor("#D9D2C4")

        val leftX = 34f
        val rightX = pageInfo.pageWidth - 34f
        val cardLeft = 20f
        val cardRight = pageInfo.pageWidth - 20f

        val titlePaint = Paint().apply {
            textSize = 20f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
        val badgePaint = Paint().apply {
            textSize = 13f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            textSize = 13f
            textAlign = Paint.Align.RIGHT
            color = android.graphics.Color.DKGRAY
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
            color = darkTextColor
            isAntiAlias = true
        }
        val totalLabelPaint = Paint(labelPaint).apply {
            textSize = 15f
            color = darkTextColor
            isFakeBoldText = true
        }
        val totalValuePaint = Paint(valuePaint).apply {
            textSize = 22f
            color = headerColor
        }
        val dividerPaint = Paint().apply { strokeWidth = 1f; color = android.graphics.Color.LTGRAY }
        val dashedDividerPaint = Paint(dividerPaint).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }
        val centerPaint = Paint().apply {
            textSize = 12.5f
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.GRAY
            isAntiAlias = true
        }
        val rowBgPaint = Paint().apply { color = rowAltColor }
        val totalBgPaint = Paint().apply { color = totalBgColor }
        val totalBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = headerColor
        }
        val cardBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = borderColor
        }

        // 1) الشريط العلوي الملوّن باسم المحل
        val headerHeight = 92f
        canvas.drawRect(0f, 0f, pageInfo.pageWidth.toFloat(), headerHeight, Paint().apply { color = headerColor })

        var logoBottom = 0f
        receipt.logo?.let { logoBitmap ->
            val maxLogoWidth = 60
            val ratio = maxLogoWidth.toFloat() / logoBitmap.width
            val drawWidth = maxLogoWidth
            val drawHeight = (logoBitmap.height * ratio).toInt()
            val left = (pageInfo.pageWidth - drawWidth) / 2f
            val scaledLogo = android.graphics.Bitmap.createScaledBitmap(logoBitmap, drawWidth, drawHeight, true)
            canvas.drawBitmap(scaledLogo, left, 14f, null)
            logoBottom = 14f + drawHeight
        }

        canvas.drawText(
            receipt.shopName,
            pageInfo.pageWidth / 2f,
            if (logoBottom > 0f) logoBottom + 22f else headerHeight / 2f + 7f,
            titlePaint
        )

        // 2) شارة "وصل دفع" ذهبية تتوسّط حافة الشريط السفلية (نصفها بالشريط ونصفها تحته)
        val badgeWidth = 110f
        val badgeHeight = 30f
        val badgeLeft = (pageInfo.pageWidth - badgeWidth) / 2f
        val badgeTop = headerHeight - badgeHeight / 2f
        val badgeRect = android.graphics.RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)
        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, Paint().apply {
            color = goldColor
            isAntiAlias = true
        })
        canvas.drawText("وصل دفع", pageInfo.pageWidth / 2f, badgeTop + badgeHeight / 2f + 5f, badgePaint)

        var y = headerHeight + badgeHeight / 2f + 30f

        // 3) بطاقة الحقول: خلفية بيضاء بحدود رفيعة، مع تظليل متبادل للصفوف
        val fieldLines = receipt.toReceiptLines().filterIsInstance<com.example.generatorapp.printing.ReceiptLine.Field>()
        val cardTop = y - 8f
        val rowHeight = 27f
        val cardHeight = fieldLines.size * rowHeight + 16f
        canvas.drawRoundRect(
            android.graphics.RectF(cardLeft, cardTop, cardRight, cardTop + cardHeight),
            8f, 8f, cardBorderPaint
        )
        y = cardTop + 26f

        var fieldIndex = 0
        for (line in receipt.toReceiptLines()) {
            when (line) {
                is com.example.generatorapp.printing.ReceiptLine.Field -> {
                    if (fieldIndex % 2 == 0) {
                        canvas.drawRect(cardLeft + 1f, y - 18f, cardRight - 1f, y - 18f + rowHeight, rowBgPaint)
                    }
                    canvas.drawText("${line.label}:", rightX, y, labelPaint)
                    canvas.drawText(line.value, leftX, y, valuePaint)
                    y += rowHeight
                    fieldIndex++
                }
                is com.example.generatorapp.printing.ReceiptLine.Divider -> {
                    if (line.double) {
                        // فاصل أول الوصل (بعد الشارة) — لا يُرسم لأن الشريط والشارة يكفيان كفاصل بصري
                        continue
                    }
                    y += 14f
                    canvas.drawLine(leftX, y, rightX, y, dividerPaint)
                    y += 18f
                }
                is com.example.generatorapp.printing.ReceiptLine.Total -> {
                    val totalBoxTop = y - 20f
                    val totalBoxHeight = 40f
                    val totalRect = android.graphics.RectF(cardLeft, totalBoxTop, cardRight, totalBoxTop + totalBoxHeight)
                    canvas.drawRoundRect(totalRect, 8f, 8f, totalBgPaint)
                    canvas.drawRoundRect(totalRect, 8f, 8f, totalBorderPaint)
                    canvas.drawText("${line.label}:", rightX - 8f, y + 5f, totalLabelPaint)
                    canvas.drawText(line.value, leftX + 8f, y + 5f, totalValuePaint)
                    y += totalBoxHeight + 12f
                }
                is com.example.generatorapp.printing.ReceiptLine.Note -> {
                    canvas.drawText(line.text, rightX, y, labelPaint)
                    y += 22f
                }
                is com.example.generatorapp.printing.ReceiptLine.Footer -> {
                    y += 10f
                    canvas.drawLine(leftX, y, rightX, y, dashedDividerPaint)
                    y += 22f
                    canvas.drawText(line.text, pageInfo.pageWidth / 2f, y, centerPaint)
                    y += 22f
                }
                else -> Unit // Header/Badge سبق رسمهما بالشريط العلوي والشارة
            }
        }

        document.finishPage(page)

        try {
            FileOutputStream(destination.fileDescriptor).use { out ->
                document.writeTo(out)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        } finally {
            document.close()
            pdfDocument = null
        }
    }

    override fun onFinish() {
        pdfDocument?.close()
        pdfDocument = null
    }
}
