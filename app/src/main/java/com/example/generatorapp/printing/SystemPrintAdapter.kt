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

        // حجم صفحة A5 تقريبًا مناسب لوصل قصير (يمكن تغييره إلى A4 عند الحاجة لتقرير)
        val pageInfo = PdfDocument.PageInfo.Builder(420, 595, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 20f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            textSize = 13f
            textAlign = Paint.Align.RIGHT
            color = android.graphics.Color.DKGRAY
        }
        val valuePaint = Paint().apply {
            textSize = 14f
            textAlign = Paint.Align.LEFT
        }
        val totalLabelPaint = Paint(labelPaint).apply { textSize = 15f }
        val totalValuePaint = Paint(valuePaint).apply { textSize = 20f; isFakeBoldText = true }
        val dividerPaint = Paint().apply { strokeWidth = 1f; color = android.graphics.Color.LTGRAY }
        val centerPaint = Paint().apply { textSize = 13f; textAlign = Paint.Align.CENTER }

        val leftX = 30f
        val rightX = pageInfo.pageWidth - 30f
        var y = 40f

        receipt.logo?.let { logoBitmap ->
            val maxLogoWidth = 150
            val ratio = maxLogoWidth.toFloat() / logoBitmap.width
            val drawWidth = maxLogoWidth
            val drawHeight = (logoBitmap.height * ratio).toInt()
            val left = (pageInfo.pageWidth - drawWidth) / 2f
            val scaledLogo = android.graphics.Bitmap.createScaledBitmap(logoBitmap, drawWidth, drawHeight, true)
            canvas.drawBitmap(scaledLogo, left, y, null)
            y += drawHeight + 16f
        }

        canvas.drawText(receipt.shopName, pageInfo.pageWidth / 2f, y, titlePaint)
        y += 26f
        canvas.drawText("وصل دفع", pageInfo.pageWidth / 2f, y, centerPaint)
        y += 16f
        canvas.drawLine(leftX, y, rightX, y, dividerPaint)
        y += 24f

        // كل حقل يُرسم في عمودين: التسمية عند الحافة اليمنى، والقيمة عند الحافة اليسرى —
        // فتصطف كل القيم عموديًا في عمود واحد كفاتورة حقيقية بدل سطر نصي ملتصق
        for (line in receipt.toReceiptLines()) {
            when (line) {
                is com.example.generatorapp.printing.ReceiptLine.Field -> {
                    canvas.drawText("${line.label}:", rightX, y, labelPaint)
                    canvas.drawText(line.value, leftX, y, valuePaint)
                    y += 26f
                }
                is com.example.generatorapp.printing.ReceiptLine.Divider -> {
                    canvas.drawLine(leftX, y, rightX, y, dividerPaint)
                    y += 20f
                }
                is com.example.generatorapp.printing.ReceiptLine.Total -> {
                    y += 6f
                    canvas.drawText("${line.label}:", rightX, y, totalLabelPaint)
                    canvas.drawText(line.value, leftX, y, totalValuePaint)
                    y += 32f
                }
                is com.example.generatorapp.printing.ReceiptLine.Note -> {
                    canvas.drawText(line.text, rightX, y, labelPaint)
                    y += 22f
                }
                is com.example.generatorapp.printing.ReceiptLine.Footer -> {
                    canvas.drawText(line.text, pageInfo.pageWidth / 2f, y, centerPaint)
                    y += 22f
                }
                else -> Unit // Header/Badge سبق رسمهما أعلى الصفحة
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
