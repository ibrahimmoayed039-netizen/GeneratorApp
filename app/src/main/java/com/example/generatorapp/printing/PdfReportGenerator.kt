package com.example.generatorapp.printing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.util.DateUtils
import com.example.generatorapp.viewmodel.ProfitSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يبني ملفات PDF احترافية جاهزة للمشاركة أو الطباعة لثلاثة تقارير:
 * كشف حساب شهري لمشترك، تقرير الأرباح والمصروفات، وقائمة كل المشتركين.
 */
object PdfReportGenerator {

    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private fun money(v: Double) = "%.2f".format(v)

    /** كشف حساب شهري لمشترك معيّن */
    fun generateStatementPdf(
        context: Context,
        subscriberName: String,
        periodLabel: String,
        invoices: List<Invoice>,
        total: Double
    ): File {
        val builder = PdfReportBuilder(context, "كشف حساب شهري", LogoManager.loadLogo(context))
        builder.keyValue("المشترك", subscriberName)
        builder.keyValue("الفترة", periodLabel)
        builder.keyValue("عدد الفواتير", invoices.size.toString())
        builder.spacer(6f)
        builder.highlightBox("الإجمالي المستحق للفترة", money(total))
        builder.spacer(10f)

        builder.sectionTitle("تفاصيل الفواتير")
        builder.table(
            headers = listOf("التاريخ", "المولد", "الأمبيرات", "سعر الأمبير", "المبلغ"),
            rows = invoices.sortedByDescending { it.date }.map {
                listOf(
                    DateUtils.formatDate(it.date),
                    it.generatorName,
                    "%.0f".format(it.amperes),
                    money(it.pricePerAmpere),
                    money(it.amount)
                )
            },
            weights = listOf(0.22f, 0.28f, 0.16f, 0.16f, 0.18f)
        )

        return builder.save("statement_${fileStamp.format(Date())}.pdf")
    }

    /** تقرير الأرباح والمصروفات (شهري أو سنوي) مع تفصيل الفواتير والمصروفات */
    fun generateProfitReportPdf(
        context: Context,
        periodLabel: String,
        summary: ProfitSummary,
        invoices: List<Invoice>,
        expenses: List<Expense>
    ): File {
        val builder = PdfReportBuilder(context, "تقرير الأرباح والمصروفات", LogoManager.loadLogo(context))
        builder.keyValue("الفترة", periodLabel)
        builder.spacer(6f)
        builder.highlightBox("إجمالي الإيرادات", money(summary.totalRevenue), color = android.graphics.Color.parseColor("#2F6B4F"))
        builder.highlightBox("إجمالي المصروفات", money(summary.totalExpenses), color = android.graphics.Color.parseColor("#B3401F"))
        builder.highlightBox(
            "صافي الربح",
            money(summary.netProfit),
            color = if (summary.netProfit >= 0) android.graphics.Color.parseColor("#1F5FB3") else android.graphics.Color.parseColor("#B3401F")
        )
        builder.spacer(10f)

        builder.sectionTitle("تفاصيل الإيرادات (${invoices.size} فاتورة)")
        builder.table(
            headers = listOf("التاريخ", "المشترك", "المولد", "المبلغ"),
            rows = invoices.sortedByDescending { it.date }.map {
                listOf(DateUtils.formatDate(it.date), it.subscriberName, it.generatorName, money(it.amount))
            },
            weights = listOf(0.2f, 0.3f, 0.3f, 0.2f)
        )

        builder.spacer(16f)
        builder.sectionTitle("تفاصيل المصروفات (${expenses.size} مصروف)")
        builder.table(
            headers = listOf("التاريخ", "الفئة", "ملاحظة", "المبلغ"),
            rows = expenses.sortedByDescending { it.date }.map {
                listOf(DateUtils.formatDate(it.date), it.category, it.note.ifBlank { "-" }, money(it.amount))
            },
            weights = listOf(0.2f, 0.25f, 0.35f, 0.2f)
        )

        return builder.save("profit_report_${fileStamp.format(Date())}.pdf")
    }

    /** قائمة كل المشتركين (تحترم أي فلترة/بحث مطبّق مسبقًا في الشاشة قبل تمرير القائمة) */
    fun generateSubscribersListPdf(context: Context, subscribers: List<Subscriber>): File {
        val builder = PdfReportBuilder(context, "قائمة المشتركين", LogoManager.loadLogo(context))
        builder.keyValue("عدد المشتركين", subscribers.size.toString())
        builder.spacer(10f)

        builder.table(
            headers = listOf("الاسم", "الهاتف", "العنوان", "رقم العداد", "المنطقة"),
            rows = subscribers.sortedBy { it.name }.map {
                listOf(it.name, it.phone, it.address.ifBlank { "-" }, it.meterNumber, it.area.ifBlank { "-" })
            },
            weights = listOf(0.24f, 0.18f, 0.26f, 0.16f, 0.16f)
        )

        return builder.save("subscribers_${fileStamp.format(Date())}.pdf")
    }

    /** يفتح ملف PDF مباشرة بأي تطبيق عارض متوفر على الجهاز، وإن لم يوجد يعرض قائمة مشاركة كبديل */
    fun openOrShare(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "فتح/مشاركة التقرير").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
