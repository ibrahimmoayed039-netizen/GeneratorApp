package com.example.generatorapp.repository

import com.example.generatorapp.data.AppDatabase
import com.example.generatorapp.data.dao.SubscriberLastPayment
import com.example.generatorapp.data.entities.AmpereChangeLog
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.Subscription

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
    suspend fun updateGenerator(generator: Generator) = db.generatorDao().update(generator)
    suspend fun deleteGenerator(generator: Generator) = db.generatorDao().delete(generator)

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
}
