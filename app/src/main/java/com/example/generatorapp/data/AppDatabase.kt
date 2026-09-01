package com.example.generatorapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.generatorapp.data.dao.AmpereChangeLogDao
import com.example.generatorapp.data.dao.ExpenseDao
import com.example.generatorapp.data.dao.GeneratorDao
import com.example.generatorapp.data.dao.InvoiceDao
import com.example.generatorapp.data.dao.SubscriberDao
import com.example.generatorapp.data.dao.SubscriptionDao
import com.example.generatorapp.data.entities.AmpereChangeLog
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.Subscription

/**
 * قاعدة البيانات المحلية (SQLite عبر Room)
 * البيانات تبقى محفوظة على الجهاز حتى بعد إغلاق التطبيق بالكامل.
 */
@Database(
    entities = [Subscriber::class, Generator::class, Subscription::class, Invoice::class, Expense::class, AmpereChangeLog::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subscriberDao(): SubscriberDao
    abstract fun generatorDao(): GeneratorDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun ampereChangeLogDao(): AmpereChangeLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "generator_app.db"
                )
                    // ملاحظة: أثناء التطوير فقط — أي تغيير مستقبلي بهيكل الجداول سيمسح البيانات القديمة.
                    // عند الإصدار النهائي يُفضّل استبدالها بخطوات Migration حقيقية للحفاظ على بيانات المستخدم.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * يغلق الاتصال الحالي بقاعدة البيانات ويصفّر الـ instance المخزّن.
         * ضروري قبل استبدال ملف قاعدة البيانات بنسخة احتياطية (استعادة)، وإلا Room
         * يبقى يكتب على نسخة قديمة من الملف مفتوحة بالذاكرة.
         */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
