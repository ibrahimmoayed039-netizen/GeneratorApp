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
        val textPaint = Paint().apply {
            textSize = 14f
            textAlign = Paint.Align.RIGHT
        }

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
        y += 30f

        val rightX = pageInfo.pageWidth - 30f
        for (line in receipt.toLines().drop(1)) {
            canvas.drawText(line, rightX, y, textPaint)
            y += 24f
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
