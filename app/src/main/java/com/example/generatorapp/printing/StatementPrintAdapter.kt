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
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.util.DateUtils
import java.io.FileOutputStream
import java.io.IOException

/**
 * يطبع كشف حساب شهري كامل لمشترك معيّن (قائمة كل فواتيره خلال الفترة + الإجمالي)
 * بنفس أسلوب SystemPrintAdapter (رسم PDF مباشر) لكن بتنسيق جدول بدل الوصل المختصر.
 */
class StatementPrintAdapter(
    private val subscriberName: String,
    private val periodLabel: String,
    private val invoicesList: List<Invoice>,
    private val total: Double,
    private val logo: android.graphics.Bitmap? = null
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
            callback.onLayoutCancelled(); return
        }
        val info = PrintDocumentInfo.Builder("كشف_حساب.pdf")
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
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val subPaint = Paint().apply { textSize = 13f; textAlign = Paint.Align.CENTER }
        val headerPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        val rowPaint = Paint().apply { textSize = 12f; textAlign = Paint.Align.RIGHT }
        val totalPaint = Paint().apply { textSize = 15f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }

        var y = 40f
        logo?.let {
            val maxW = 120
            val ratio = maxW.toFloat() / it.width
            val w = maxW
            val h = (it.height * ratio).toInt()
            val left = (pageInfo.pageWidth - w) / 2f
            val scaled = android.graphics.Bitmap.createScaledBitmap(it, w, h, true)
            canvas.drawBitmap(scaled, left, y, null)
            y += h + 14f
        }

        canvas.drawText("كشف حساب مشترك", pageInfo.pageWidth / 2f, y, titlePaint)
        y += 24f
        canvas.drawText("$subscriberName - $periodLabel", pageInfo.pageWidth / 2f, y, subPaint)
        y += 30f

        val rightX = pageInfo.pageWidth - 40f
        val midX = pageInfo.pageWidth - 220f
        val leftX = pageInfo.pageWidth - 400f

        canvas.drawText("التاريخ", rightX, y, headerPaint)
        canvas.drawText("المولد", midX, y, headerPaint)
        canvas.drawText("المبلغ", leftX, y, headerPaint)
        y += 10f
        canvas.drawLine(40f, y, pageInfo.pageWidth - 40f, y, rowPaint)
        y += 20f

        for (inv in invoicesList) {
            canvas.drawText(DateUtils.formatDate(inv.date), rightX, y, rowPaint)
            canvas.drawText(inv.generatorName, midX, y, rowPaint)
            canvas.drawText("%.2f".format(inv.amount), leftX, y, rowPaint)
            y += 22f
            if (y > pageInfo.pageHeight - 80f) break // حماية بسيطة من تجاوز الصفحة
        }

        y += 10f
        canvas.drawLine(40f, y, pageInfo.pageWidth - 40f, y, rowPaint)
        y += 26f
        canvas.drawText("الإجمالي: %.2f".format(total), rightX, y, totalPaint)

        document.finishPage(page)

        try {
            FileOutputStream(destination.fileDescriptor).use { out -> document.writeTo(out) }
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
