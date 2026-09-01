package com.example.generatorapp.printing

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager as AndroidPrintManager
import com.example.generatorapp.data.entities.Invoice

/**
 * نقطة الدخول الموحدة للطباعة — يختار المسار المناسب:
 *  - printViaSystem: يفتح مربع حوار الطباعة القياسي في أندرويد (أي طابعة مسجّلة بالنظام)
 *  - printViaThermal: يرسل الوصل مباشرة إلى طابعة حرارية عبر البلوتوث (ESC/POS)
 */
object ReceiptPrintManager {

    fun printViaSystem(context: Context, receipt: ReceiptData) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as AndroidPrintManager
        val adapter = SystemPrintAdapter(receipt)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()

        printManager.print("وصل دفع - ${receipt.subscriberName}", adapter, attributes)
    }

    suspend fun printViaThermal(
        device: BluetoothDevice,
        receipt: ReceiptData,
        paperWidthChars: Int = 32 // استخدم 48 لعرض 80مم
    ) {
        EscPosPrinter(paperWidthChars).printReceipt(device, receipt)
    }

    /**
     * يطبع صفحة اختبار جداول الحروف (CP0 - CP47 و CP255) على الطابعة الحرارية
     * لمساعدتك على تحديد رقم جدول الحروف الصحيح لدعم اللغة العربية على طابعتك.
     */
    suspend fun printCharsetTestPage(
        device: BluetoothDevice,
        paperWidthChars: Int = 32 // استخدم 48 لعرض 80مم
    ) {
        EscPosPrinter(paperWidthChars).printCharsetTestPage(device)
    }

    fun printStatement(
        context: Context,
        subscriberName: String,
        periodLabel: String,
        invoicesList: List<Invoice>,
        total: Double
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as AndroidPrintManager
        val adapter = StatementPrintAdapter(subscriberName, periodLabel, invoicesList, total, LogoManager.loadLogo(context))
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        printManager.print("كشف حساب - $subscriberName", adapter, attributes)
    }
}
