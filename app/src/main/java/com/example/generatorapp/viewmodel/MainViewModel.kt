package com.example.generatorapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.generatorapp.data.AppDatabase
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.Subscription
import com.example.generatorapp.repository.AppRepository
import com.example.generatorapp.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** يمثل مشترك متأخر بالدفع مع تاريخ آخر دفعة له (إن وُجدت) */
data class LateSubscriberInfo(
    val subscriber: Subscriber,
    val lastPaymentMillis: Long?
)

/** يمثل حالة دفع مشترك لهذا الشهر: مدفوع (له فاتورة هذا الشهر) أو غير مدفوع */
data class PaymentStatusInfo(
    val subscriber: Subscriber,
    val paid: Boolean,
    val lastInvoiceThisMonthMillis: Long?
)

/** ملخص تقرير الأرباح لفترة معيّنة */
data class ProfitSummary(
    val totalRevenue: Double,
    val totalExpenses: Double,
    val netProfit: Double
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(AppDatabase.getInstance(application))

    val subscribers = repository.subscribers
    val generators = repository.generators
    val invoices = repository.invoices
    val expenses = repository.expenses

    fun addSubscriber(name: String, phone: String, address: String, meterNumber: String) {
        viewModelScope.launch {
            repository.addSubscriber(
                Subscriber(name = name, phone = phone, address = address, meterNumber = meterNumber)
            )
        }
    }

    fun addGenerator(name: String, capacityKva: Double, pricePerAmpere: Double) {
        viewModelScope.launch {
            repository.addGenerator(
                Generator(name = name, capacityKva = capacityKva, pricePerAmpere = pricePerAmpere)
            )
        }
    }

    /** ينشئ فاتورة ويحفظها فعليًا بقاعدة البيانات (مرتبطة بمعرّف المشترك الحقيقي) */
    fun createInvoice(
        subscriberId: Long,
        subscriberName: String,
        generatorName: String,
        amperes: Double,
        pricePerAmpere: Double,
        note: String,
        onCreated: (Invoice) -> Unit
    ) {
        viewModelScope.launch {
            val amount = amperes * pricePerAmpere
            val invoice = Invoice(
                subscriberId = subscriberId,
                subscriberName = subscriberName,
                generatorName = generatorName,
                amperes = amperes,
                pricePerAmpere = pricePerAmpere,
                amount = amount,
                date = System.currentTimeMillis(),
                note = note
            )
            repository.addInvoice(invoice)
            onCreated(invoice)
        }
    }

    // ---------- إدارة اشتراكات المشترك (تفعيل/تعليق + سجل الأمبيرات) ----------

    /** اشتراكات مشترك معيّن (مفعّلة ومعلّقة) — كل اشتراك يمثّل ربط مشترك بمولد */
    fun subscriptionsForSubscriber(subscriberId: Long) = repository.subscriptionsForSubscriber(subscriberId)

    /** سجل تغييرات الأمبيرات لمشترك معيّن */
    fun ampereLogsForSubscriber(subscriberId: Long) = repository.ampereLogsForSubscriber(subscriberId)

    fun addSubscription(subscriberId: Long, generatorId: Long, amperes: Double) {
        viewModelScope.launch {
            repository.addSubscription(
                Subscription(
                    subscriberId = subscriberId,
                    generatorId = generatorId,
                    amperes = amperes,
                    startDate = System.currentTimeMillis(),
                    active = true
                )
            )
        }
    }

    /** تفعيل أو تعليق اشتراك بدل حذفه نهائيًا */
    fun toggleSubscriptionActive(subscription: Subscription) {
        viewModelScope.launch {
            repository.setSubscriptionActive(subscription, !subscription.active)
        }
    }

    /** تعديل عدد أمبيرات اشتراك (يُسجَّل تلقائيًا بسجل تغييرات الأمبيرات) */
    fun changeSubscriptionAmperes(subscription: Subscription, newAmperes: Double, note: String = "") {
        viewModelScope.launch {
            repository.changeSubscriptionAmperes(subscription, newAmperes, note)
        }
    }

    // ---------- المصروفات ----------

    fun addExpense(category: String, amount: Double, note: String) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(category = category, amount = amount, date = System.currentTimeMillis(), note = note)
            )
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    // ---------- كشف حساب شهري لمشترك معيّن ----------

    /** يرجع فواتير مشترك معيّن ضمن شهر/سنة محددين + الإجمالي، عبر callback (لأنه استعلام لمرة واحدة) */
    fun loadSubscriberStatement(
        subscriberId: Long,
        year: Int,
        month: Int,
        onResult: (List<Invoice>, Double) -> Unit
    ) {
        viewModelScope.launch {
            val (start, end) = DateUtils.monthRange(year, month)
            val list = repository.invoicesForSubscriberBetween(subscriberId, start, end).first()
            onResult(list, list.sumOf { it.amount })
        }
    }

    // ---------- المتأخرون بالدفع ----------

    /** يحدد المشتركين اللي ما لهم دفعة مسجّلة خلال الشهر الحالي */
    fun loadLateSubscribers(onResult: (List<LateSubscriberInfo>) -> Unit) {
        viewModelScope.launch {
            val allSubscribers = repository.subscribers.first()
            val lastPayments = repository.lastPaymentPerSubscriber().associateBy { it.subscriberId }
            val (monthStart, _) = DateUtils.monthRange(DateUtils.currentYear(), DateUtils.currentMonth())

            val late = allSubscribers.filter { sub ->
                val last = lastPayments[sub.id]?.lastDate
                last == null || last < monthStart
            }.map { sub ->
                LateSubscriberInfo(sub, lastPayments[sub.id]?.lastDate)
            }
            onResult(late)
        }
    }

    // ---------- حالة الدفع الشهرية لكل العملاء ----------

    /**
     * يرجع كل المشتركين مع حالة دفعهم لهذا الشهر: مدفوع (له فاتورة مسجّلة خلال الشهر الحالي)
     * أو غير مدفوع. تُستخدم لتلوين العميل باللون الأحمر بعد الدفع في شاشة حالة الدفع الشهرية.
     */
    fun loadMonthlyPaymentStatus(onResult: (List<PaymentStatusInfo>) -> Unit) {
        viewModelScope.launch {
            val allSubscribers = repository.subscribers.first()
            val (monthStart, monthEnd) = DateUtils.monthRange(DateUtils.currentYear(), DateUtils.currentMonth())

            val statusList = allSubscribers.map { sub ->
                val invoicesThisMonth = repository
                    .invoicesForSubscriberBetween(sub.id, monthStart, monthEnd)
                    .first()
                val lastInvoice = invoicesThisMonth.maxByOrNull { it.date }
                PaymentStatusInfo(
                    subscriber = sub,
                    paid = invoicesThisMonth.isNotEmpty(),
                    lastInvoiceThisMonthMillis = lastInvoice?.date
                )
            }
            onResult(statusList)
        }
    }

    // ---------- تقرير الأرباح (شهري/سنوي) ----------

    fun loadProfitSummary(year: Int, month: Int?, onResult: (ProfitSummary) -> Unit) {
        viewModelScope.launch {
            val (start, end) = if (month != null) {
                DateUtils.monthRange(year, month)
            } else {
                DateUtils.yearRange(year)
            }
            val revenue = repository.invoicesBetween(start, end).first().sumOf { it.amount }
            val cost = repository.expensesBetween(start, end).first().sumOf { it.amount }
            onResult(ProfitSummary(revenue, cost, revenue - cost))
        }
    }
}
