package com.example.generatorapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.generatorapp.data.AppDatabase
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.data.entities.FaultLog
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.GeneratorHourLog
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.MaintenanceItem
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.SubscriberType
import com.example.generatorapp.data.entities.Subscription
import com.example.generatorapp.repository.AppRepository
import com.example.generatorapp.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** حالة استحقاق بند صيانة معيّن، محسوبة من ساعات التشغيل الحالية للمولد ومن عدد الأيام منذ آخر خدمة */
data class MaintenanceStatus(
    val item: MaintenanceItem,
    val generatorName: String,
    val currentHours: Double,
    private val nowMillis: Long = System.currentTimeMillis()
) {
    val hoursSinceService: Double get() = (currentHours - item.lastServiceHours).coerceAtLeast(0.0)
    val hoursRemaining: Double get() = item.intervalHours - hoursSinceService

    /** هل البند مفعّل عليه تنبيه بعدد الأيام أيضًا (0 = تنبيه بالساعات فقط) */
    val hasDaySchedule: Boolean get() = item.intervalDays > 0

    val daysSinceService: Long get() =
        TimeUnit.MILLISECONDS.toDays((nowMillis - item.lastServiceDate).coerceAtLeast(0))

    val daysRemaining: Long get() =
        if (hasDaySchedule) item.intervalDays - daysSinceService else Long.MAX_VALUE

    /** مستحقة إذا انتهت مدة الساعات، أو انتهت مدة الأيام (أيهما أسبق) */
    val isDue: Boolean get() = hoursRemaining <= 0.0 || (hasDaySchedule && daysRemaining <= 0)

    /** يعتبر "قريب" إذا تبقّى له 10% أو أقل من مدة الساعات، أو 10% أو أقل (وبحد أقصى 7 أيام) من مدة الأيام */
    val isDueSoon: Boolean get() = !isDue && (
        hoursRemaining <= (item.intervalHours * 0.1).coerceAtMost(25.0) ||
            (hasDaySchedule && daysRemaining <= (item.intervalDays * 0.1).coerceAtMost(7.0))
        )

    /** أقرب نسبة متبقية بين جدول الساعات وجدول الأيام (الأصغر هو الأكثر إلحاحًا)، تُستخدم للترتيب */
    val urgencyFraction: Double get() {
        val hoursFraction = if (item.intervalHours > 0) hoursRemaining / item.intervalHours else Double.MAX_VALUE
        val daysFraction = if (hasDaySchedule) daysRemaining.toDouble() / item.intervalDays else Double.MAX_VALUE
        return minOf(hoursFraction, daysFraction)
    }
}

/** يمثل مشترك متأخر بالدفع مع تاريخ آخر دفعة له (إن وُجدت) */
data class LateSubscriberInfo(
    val subscriber: Subscriber,
    val lastPaymentMillis: Long?
)

/** يمثل مشترك مع مولدته الحالية وسعر الأمبير المطبَّق عليه (لرسالة السعر الشهري) */
data class SubscriberPriceInfo(
    val subscriber: Subscriber,
    val generator: Generator,
    val pricePerAmpere: Double
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
    val netProfit: Double,
    /**
     * الربح الناتج فقط عن الفرق بين سعر تكلفة الأمبير وسعر بيعه (مجموع Invoice.profit)،
     * أي: مجموع (سعر البيع - سعر التكلفة) × عدد الأمبيرات لكل فاتورة.
     * هذا مختلف عن "صافي الربح" الذي يطرح كل المصروفات المسجَّلة (ديزل/صيانة/أخرى) من الإيرادات.
     */
    val ampereMarginProfit: Double = 0.0
)

/** تفاصيل كاملة لتقرير الأرباح (تُستخدم لتصدير PDF مفصّل بالفواتير والمصروفات) */
data class ProfitReportDetails(
    val summary: ProfitSummary,
    val invoices: List<Invoice>,
    val expenses: List<Expense>
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(AppDatabase.getInstance(application))

    val subscribers = repository.subscribers
    val generators = repository.generators
    val invoices = repository.invoices
    val expenses = repository.expenses
    /** كل الاشتراكات الفعّالة حاليًا (بكل المولدات) — تُستخدم لحساب الأمبيرات الموزّعة لكل مولد */
    val activeSubscriptions = repository.activeSubscriptions

    fun addSubscriber(
        name: String,
        phone: String,
        address: String,
        meterNumber: String,
        area: String = "",
        subscriberType: String = SubscriberType.RESIDENTIAL
    ) {
        viewModelScope.launch {
            repository.addSubscriber(
                Subscriber(
                    name = name,
                    phone = phone,
                    address = address,
                    meterNumber = meterNumber,
                    area = area,
                    subscriberType = subscriberType
                )
            )
        }
    }

    /** يحدّث بيانات مشترك موجود (الاسم أو الهاتف أو العنوان أو رقم العداد) */
    fun updateSubscriber(subscriber: Subscriber) {
        viewModelScope.launch {
            repository.updateSubscriber(subscriber)
        }
    }

    fun addGenerator(
        name: String,
        capacityKva: Double,
        costPricePerAmpere: Double,
        residentialPricePerAmpere: Double,
        commercialPricePerAmpere: Double
    ) {
        viewModelScope.launch {
            repository.addGenerator(
                Generator(
                    name = name,
                    capacityKva = capacityKva,
                    costPricePerAmpere = costPricePerAmpere,
                    residentialPricePerAmpere = residentialPricePerAmpere,
                    commercialPricePerAmpere = commercialPricePerAmpere
                )
            )
        }
    }

    /**
     * يعدّل أسعار مولد موجود (تكلفة/بيع منزلي/بيع تجاري) — يُسجَّل التغيير بسجل الأسعار
     * وينعكس فورًا على كل المشتركين المرتبطين بهذا المولد بأي فاتورة جديدة تُصدر لهم.
     */
    fun updateGeneratorPrices(
        generator: Generator,
        newCostPrice: Double,
        newResidentialPrice: Double,
        newCommercialPrice: Double,
        note: String = ""
    ) {
        viewModelScope.launch {
            repository.updateGeneratorPrices(generator, newCostPrice, newResidentialPrice, newCommercialPrice, note)
        }
    }

    /** سجل تغييرات أسعار مولد معيّن */
    fun priceLogsForGenerator(generatorId: Long) = repository.priceLogsForGenerator(generatorId)

    /** ينشئ فاتورة ويحفظها فعليًا بقاعدة البيانات (مرتبطة بمعرّف المشترك الحقيقي) */
    fun createInvoice(
        subscriberId: Long,
        subscriberName: String,
        generatorName: String,
        amperes: Double,
        pricePerAmpere: Double,
        note: String,
        subscriberType: String = "",
        costPricePerAmpere: Double = 0.0,
        onCreated: (Invoice) -> Unit
    ) {
        viewModelScope.launch {
            val amount = amperes * pricePerAmpere
            val profit = amount - (amperes * costPricePerAmpere)
            val invoice = Invoice(
                subscriberId = subscriberId,
                subscriberName = subscriberName,
                generatorName = generatorName,
                amperes = amperes,
                pricePerAmpere = pricePerAmpere,
                amount = amount,
                date = System.currentTimeMillis(),
                note = note,
                subscriberType = subscriberType,
                costPricePerAmpere = costPricePerAmpere,
                profit = profit
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

    // ---------- رسالة سعر الأمبير الشهري ----------

    /**
     * يرجع كل مشترك له اشتراك فعّال مع مولدته وسعر الأمبير المطبَّق عليه حسب نوعه
     * (منزلي/تجاري)، تُستخدم لبناء رسالة السعر الشهري وإرسالها دفعة واحدة.
     */
    fun loadSubscriberPricingInfo(onResult: (List<SubscriberPriceInfo>) -> Unit) {
        viewModelScope.launch {
            val allSubscribers = repository.subscribers.first()
            val result = mutableListOf<SubscriberPriceInfo>()
            allSubscribers.forEach { sub ->
                val activeSubscription = repository.subscriptionsForSubscriber(sub.id).first()
                    .firstOrNull { it.active }
                val generator = activeSubscription?.let { repository.getGenerator(it.generatorId) }
                if (generator != null) {
                    result.add(
                        SubscriberPriceInfo(
                            subscriber = sub,
                            generator = generator,
                            pricePerAmpere = generator.sellPriceFor(sub.subscriberType)
                        )
                    )
                }
            }
            onResult(result)
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
            val invoicesInRange = repository.invoicesBetween(start, end).first()
            val revenue = invoicesInRange.sumOf { it.amount }
            val cost = repository.expensesBetween(start, end).first().sumOf { it.amount }
            val ampereMargin = invoicesInRange.sumOf { it.profit }
            onResult(ProfitSummary(revenue, cost, revenue - cost, ampereMargin))
        }
    }

    /** نفس تقرير الأرباح لكن مع قوائم الفواتير والمصروفات كاملة (لتصدير PDF مفصّل) */
    fun loadProfitReportDetails(year: Int, month: Int?, onResult: (ProfitReportDetails) -> Unit) {
        viewModelScope.launch {
            val (start, end) = if (month != null) {
                DateUtils.monthRange(year, month)
            } else {
                DateUtils.yearRange(year)
            }
            val invoicesList = repository.invoicesBetween(start, end).first()
            val expensesList = repository.expensesBetween(start, end).first()
            val revenue = invoicesList.sumOf { it.amount }
            val cost = expensesList.sumOf { it.amount }
            val ampereMargin = invoicesList.sumOf { it.profit }
            onResult(
                ProfitReportDetails(
                    ProfitSummary(revenue, cost, revenue - cost, ampereMargin),
                    invoicesList,
                    expensesList
                )
            )
        }
    }

    // ---------- ساعات تشغيل المولد ----------

    /** سجل قراءات ساعات التشغيل لمولد معيّن */
    fun hourLogsForGenerator(generatorId: Long) = repository.hourLogsForGenerator(generatorId)

    /** يضيف قراءة جديدة لعداد الساعات (تحدّث تلقائيًا القراءة الحالية المخزّنة على المولد) */
    fun addHourLog(generatorId: Long, hours: Double, note: String = "") {
        viewModelScope.launch { repository.addHourLog(generatorId, hours, note) }
    }

    fun deleteHourLog(log: GeneratorHourLog) {
        viewModelScope.launch { repository.deleteHourLog(log) }
    }

    // ---------- بنود الصيانة الدورية ----------

    fun maintenanceItemsForGenerator(generatorId: Long) = repository.maintenanceItemsForGenerator(generatorId)

    fun addMaintenanceItem(generatorId: Long, type: String, intervalHours: Double, intervalDays: Int = 0, note: String = "") {
        viewModelScope.launch {
            val generator = repository.generators.first().find { it.id == generatorId }
            repository.addMaintenanceItem(
                MaintenanceItem(
                    generatorId = generatorId,
                    type = type,
                    intervalHours = intervalHours,
                    lastServiceHours = generator?.currentHours ?: 0.0,
                    lastServiceDate = System.currentTimeMillis(),
                    intervalDays = intervalDays,
                    note = note
                )
            )
        }
    }

    /** يسجّل تنفيذ الصيانة الآن (يصفّر العدّاد عند ساعات التشغيل الحالية) */
    fun markMaintenanceServiced(item: MaintenanceItem) {
        viewModelScope.launch { repository.markMaintenanceServiced(item) }
    }

    fun deleteMaintenanceItem(item: MaintenanceItem) {
        viewModelScope.launch { repository.deleteMaintenanceItem(item) }
    }

    /** كل بنود الصيانة بكل المولدات مع حالة استحقاقها، للمولدات المستحقة أو القريبة من الاستحقاق فقط */
    fun loadMaintenanceAlerts(onResult: (List<MaintenanceStatus>) -> Unit) {
        viewModelScope.launch {
            val pairs = repository.generatorsWithMaintenance()
            val statuses = pairs.flatMap { (generator, items) ->
                items.map { MaintenanceStatus(it, generator.name, generator.currentHours) }
            }.filter { it.isDue || it.isDueSoon }
                .sortedBy { it.urgencyFraction }
            onResult(statuses)
        }
    }

    // ---------- سجل الأعطال والتصليحات ----------

    fun faultLogsForGenerator(generatorId: Long) = repository.faultLogsForGenerator(generatorId)

    fun addFaultLog(generatorId: Long, description: String, repairDescription: String = "", cost: Double = 0.0) {
        viewModelScope.launch {
            repository.addFaultLog(
                FaultLog(
                    generatorId = generatorId,
                    date = System.currentTimeMillis(),
                    faultDescription = description,
                    repairDescription = repairDescription,
                    cost = cost,
                    resolved = repairDescription.isNotBlank()
                )
            )
        }
    }

    /** يحدّث بند عطل موجود (مثلًا: إضافة وصف التصليح وتكلفته وتعليمه كمُصلَّح) */
    fun updateFaultLog(log: FaultLog, repairDescription: String, cost: Double, resolved: Boolean) {
        viewModelScope.launch {
            repository.updateFaultLog(
                log.copy(
                    repairDescription = repairDescription,
                    cost = cost,
                    resolved = resolved,
                    resolvedDate = if (resolved) System.currentTimeMillis() else null
                )
            )
        }
    }

    fun deleteFaultLog(log: FaultLog) {
        viewModelScope.launch { repository.deleteFaultLog(log) }
    }
}
