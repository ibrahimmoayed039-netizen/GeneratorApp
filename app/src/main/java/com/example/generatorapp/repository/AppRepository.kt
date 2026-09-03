package com.example.generatorapp.repository

import com.example.generatorapp.data.AppDatabase
import com.example.generatorapp.data.dao.SubscriberLastPayment
import com.example.generatorapp.data.entities.AmpereChangeLog
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.data.entities.FaultLog
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.GeneratorHourLog
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.MaintenanceItem
import com.example.generatorapp.data.entities.PriceChangeLog
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.Subscription
import kotlinx.coroutines.flow.first

/**
 * طبقة وسيطة بين قاعدة البيانات وباقي التطبيق (ViewModel)
 */
class AppRepository(private val db: AppDatabase) {

    // المشتركون
    val subscribers = db.subscriberDao().getAll()
    suspend fun addSubscriber(subscriber: Subscriber) = db.subscriberDao().insert(subscriber)
    suspend fun updateSubscriber(subscriber: Subscriber) = db.subscriberDao().update(subscriber)
    suspend fun deleteSubscriber(subscriber: Subscriber) = db.subscriberDao().delete(subscriber)

    // المولدات
    val generators = db.generatorDao().getAll()
    suspend fun addGenerator(generator: Generator) = db.generatorDao().insert(generator)
    suspend fun getGenerator(id: Long): Generator? = db.generatorDao().getById(id)
    suspend fun updateGenerator(generator: Generator) = db.generatorDao().update(generator)
    suspend fun deleteGenerator(generator: Generator) = db.generatorDao().delete(generator)

    /**
     * يعدّل أسعار مولد (تكلفة الأمبير، بيع منزلي، بيع تجاري) ويسجّل التغيير بسجل الأسعار
     * (فقط إن تغيّر أي منها فعلًا). بما أن كل الشاشات تقرأ من نفس تدفّق `generators`، فإن
     * السعر الجديد ينعكس فورًا على كل المشتركين المرتبطين بهذا المولد عند إصدار أي فاتورة جديدة.
     */
    suspend fun updateGeneratorPrices(
        generator: Generator,
        newCostPrice: Double,
        newResidentialPrice: Double,
        newCommercialPrice: Double,
        note: String = ""
    ) {
        val changed = newCostPrice != generator.costPricePerAmpere ||
            newResidentialPrice != generator.residentialPricePerAmpere ||
            newCommercialPrice != generator.commercialPricePerAmpere
        if (changed) {
            db.priceChangeLogDao().insert(
                PriceChangeLog(
                    generatorId = generator.id,
                    generatorName = generator.name,
                    oldCostPrice = generator.costPricePerAmpere,
                    newCostPrice = newCostPrice,
                    oldResidentialPrice = generator.residentialPricePerAmpere,
                    newResidentialPrice = newResidentialPrice,
                    oldCommercialPrice = generator.commercialPricePerAmpere,
                    newCommercialPrice = newCommercialPrice,
                    changeDate = System.currentTimeMillis(),
                    note = note
                )
            )
            db.generatorDao().update(
                generator.copy(
                    costPricePerAmpere = newCostPrice,
                    residentialPricePerAmpere = newResidentialPrice,
                    commercialPricePerAmpere = newCommercialPrice
                )
            )
        }
    }

    /** سجل تغييرات أسعار مولد معيّن، من الأحدث للأقدم */
    fun priceLogsForGenerator(generatorId: Long) = db.priceChangeLogDao().getForGenerator(generatorId)

    // الاشتراكات
    val activeSubscriptions = db.subscriptionDao().getActive()
    suspend fun addSubscription(subscription: Subscription) = db.subscriptionDao().insert(subscription)

    /** كل اشتراكات مشترك معيّن (المفعّلة والمعلّقة) */
    fun subscriptionsForSubscriber(subscriberId: Long) = db.subscriptionDao().getAllBySubscriber(subscriberId)

    /** تفعيل أو تعليق اشتراك دون حذفه نهائيًا */
    suspend fun setSubscriptionActive(subscription: Subscription, active: Boolean) =
        db.subscriptionDao().update(subscription.copy(active = active))

    /** تعديل عدد أمبيرات اشتراك مع تسجيل التغيير بسجل الأمبيرات (فقط إن تغيّرت القيمة فعلًا) */
    suspend fun changeSubscriptionAmperes(subscription: Subscription, newAmperes: Double, note: String = "") {
        if (newAmperes != subscription.amperes) {
            db.ampereChangeLogDao().insert(
                AmpereChangeLog(
                    subscriptionId = subscription.id,
                    subscriberId = subscription.subscriberId,
                    oldAmperes = subscription.amperes,
                    newAmperes = newAmperes,
                    changeDate = System.currentTimeMillis(),
                    note = note
                )
            )
            db.subscriptionDao().update(subscription.copy(amperes = newAmperes))
        }
    }

    /** سجل تغييرات الأمبيرات الخاص بمشترك معيّن، مرتّب من الأحدث للأقدم */
    fun ampereLogsForSubscriber(subscriberId: Long) = db.ampereChangeLogDao().getForSubscriber(subscriberId)

    // الفواتير / الوصولات
    val invoices = db.invoiceDao().getAll()
    suspend fun addInvoice(invoice: Invoice) = db.invoiceDao().insert(invoice)
    fun invoicesForSubscriberBetween(subscriberId: Long, start: Long, end: Long) =
        db.invoiceDao().getForSubscriberBetween(subscriberId, start, end)
    fun invoicesBetween(start: Long, end: Long) = db.invoiceDao().getBetween(start, end)
    suspend fun lastPaymentPerSubscriber(): List<SubscriberLastPayment> =
        db.invoiceDao().getLastPaymentPerSubscriber()

    // المصروفات
    val expenses = db.expenseDao().getAll()
    suspend fun addExpense(expense: Expense) = db.expenseDao().insert(expense)
    suspend fun deleteExpense(expense: Expense) = db.expenseDao().delete(expense)
    fun expensesBetween(start: Long, end: Long) = db.expenseDao().getBetween(start, end)

    // ---------- ساعات تشغيل المولد ----------

    /** سجل قراءات ساعات التشغيل لمولد معيّن، من الأحدث للأقدم */
    fun hourLogsForGenerator(generatorId: Long) = db.generatorHourLogDao().getForGenerator(generatorId)

    /**
     * يضيف قراءة جديدة لعداد ساعات التشغيل، ويحدّث القراءة الحالية المخزّنة على المولد نفسه
     * (تُستخدم بشاشة قائمة المولدات وبفحص تنبيهات الصيانة دون الحاجة لجلب آخر قراءة كل مرة).
     */
    suspend fun addHourLog(generatorId: Long, hours: Double, note: String = "") {
        db.generatorHourLogDao().insert(
            GeneratorHourLog(generatorId = generatorId, hours = hours, date = System.currentTimeMillis(), note = note)
        )
        val generator = db.generatorDao().getById(generatorId)
        if (generator != null && hours > generator.currentHours) {
            db.generatorDao().update(generator.copy(currentHours = hours))
        }
    }

    /**
     * يحذف قراءة ساعات تشغيل، ثم يعيد حساب "الساعات الحالية" المخزّنة على المولد
     * من أعلى قراءة متبقية بعد الحذف (أو صفر إن لم يتبقَّ أي سجل)، حتى لا يبقى
     * الرقم المعروض بالشاشة عالقًا على قيمة السجل المحذوف.
     */
    suspend fun deleteHourLog(log: GeneratorHourLog) {
        db.generatorHourLogDao().delete(log)
        val remaining = db.generatorHourLogDao().getForGenerator(log.generatorId).first()
        val newCurrentHours = remaining.maxOfOrNull { it.hours } ?: 0.0
        val generator = db.generatorDao().getById(log.generatorId)
        if (generator != null && generator.currentHours != newCurrentHours) {
            db.generatorDao().update(generator.copy(currentHours = newCurrentHours))
        }
    }

    // ---------- بنود الصيانة الدورية (تغيير زيت / صيانة دورية) ----------

    /** بنود الصيانة الخاصة بمولد معيّن */
    fun maintenanceItemsForGenerator(generatorId: Long) = db.maintenanceItemDao().getForGenerator(generatorId)

    /** كل بنود الصيانة بكل المولدات (تُستخدم لفحص التنبيهات اليومي وشاشة تنبيهات الصيانة) */
    val allMaintenanceItems = db.maintenanceItemDao().getAll()

    suspend fun addMaintenanceItem(item: MaintenanceItem) = db.maintenanceItemDao().insert(item)
    suspend fun updateMaintenanceItem(item: MaintenanceItem) = db.maintenanceItemDao().update(item)
    suspend fun deleteMaintenanceItem(item: MaintenanceItem) = db.maintenanceItemDao().delete(item)

    /** يسجّل تنفيذ الصيانة الآن: يحدّث آخر ساعة/تاريخ خدمة عند ساعات التشغيل الحالية للمولد */
    suspend fun markMaintenanceServiced(item: MaintenanceItem) {
        val generator = db.generatorDao().getById(item.generatorId)
        val hoursNow = generator?.currentHours ?: item.lastServiceHours
        db.maintenanceItemDao().update(
            item.copy(lastServiceHours = hoursNow, lastServiceDate = System.currentTimeMillis())
        )
    }

    /** يجلب كل المولدات وبنود صيانتها دفعة واحدة (لفحص التنبيهات) */
    suspend fun generatorsWithMaintenance(): List<Pair<Generator, List<MaintenanceItem>>> {
        val allGenerators = generators.first()
        val allItems = allMaintenanceItems.first().groupBy { it.generatorId }
        return allGenerators.map { gen -> gen to (allItems[gen.id] ?: emptyList()) }
    }

    // ---------- سجل الأعطال والتصليحات ----------

    /** سجل الأعطال الخاص بمولد معيّن، من الأحدث للأقدم */
    fun faultLogsForGenerator(generatorId: Long) = db.faultLogDao().getForGenerator(generatorId)

    suspend fun addFaultLog(log: FaultLog) = db.faultLogDao().insert(log)
    suspend fun updateFaultLog(log: FaultLog) = db.faultLogDao().update(log)
    suspend fun deleteFaultLog(log: FaultLog) = db.faultLogDao().delete(log)
}
