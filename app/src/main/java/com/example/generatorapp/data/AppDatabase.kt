package com.example.generatorapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.generatorapp.data.dao.AmpereChangeLogDao
import com.example.generatorapp.data.dao.ExpenseDao
import com.example.generatorapp.data.dao.FaultLogDao
import com.example.generatorapp.data.dao.GeneratorDao
import com.example.generatorapp.data.dao.GeneratorHourLogDao
import com.example.generatorapp.data.dao.InvoiceDao
import com.example.generatorapp.data.dao.MaintenanceItemDao
import com.example.generatorapp.data.dao.PriceChangeLogDao
import com.example.generatorapp.data.dao.SubscriberDao
import com.example.generatorapp.data.dao.SubscriptionDao
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

/**
 * قاعدة البيانات المحلية (SQLite عبر Room)
 * البيانات تبقى محفوظة على الجهاز حتى بعد إغلاق التطبيق بالكامل.
 */
@Database(
    entities = [
        Subscriber::class, Generator::class, Subscription::class, Invoice::class,
        Expense::class, AmpereChangeLog::class, GeneratorHourLog::class,
        MaintenanceItem::class, FaultLog::class, PriceChangeLog::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subscriberDao(): SubscriberDao
    abstract fun generatorDao(): GeneratorDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun ampereChangeLogDao(): AmpereChangeLogDao
    abstract fun generatorHourLogDao(): GeneratorHourLogDao
    abstract fun maintenanceItemDao(): MaintenanceItemDao
    abstract fun faultLogDao(): FaultLogDao
    abstract fun priceChangeLogDao(): PriceChangeLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * يضيف: عمود ساعات التشغيل الحالية للمولد + جداول سجل الساعات وبنود الصيانة والأعطال.
         * خطوة حقيقية (وليست destructive) حتى لا تُفقد بيانات المستخدم الحالية (مشتركون، فواتير...).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE generators ADD COLUMN currentHours REAL NOT NULL DEFAULT 0.0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS generator_hour_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        generatorId INTEGER NOT NULL,
                        hours REAL NOT NULL,
                        date INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS maintenance_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        generatorId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        intervalHours REAL NOT NULL,
                        lastServiceHours REAL NOT NULL DEFAULT 0.0,
                        lastServiceDate INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS fault_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        generatorId INTEGER NOT NULL,
                        date INTEGER NOT NULL,
                        faultDescription TEXT NOT NULL,
                        repairDescription TEXT NOT NULL DEFAULT '',
                        cost REAL NOT NULL DEFAULT 0.0,
                        resolved INTEGER NOT NULL DEFAULT 0,
                        resolvedDate INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * يضيف: سعر تكلفة الأمبير وسعر بيع تجاري منفصل للمولد (سعر البيع المنزلي هو نفس
         * عمود pricePerAmpere القديم)، ونوع المشترك (منزلي/تجاري) للمشترك، وحقول نوع
         * المشترك/تكلفة الأمبير/الربح بالفاتورة، وجدول سجل تغييرات الأسعار.
         * القيمة الافتراضية لسعر البيع التجاري تُنسخ من السعر القديم حتى لا تنقلب فواتير
         * المشتركين الحاليين إلى صفر بعد الترقية مباشرة.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE generators ADD COLUMN costPricePerAmpere REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE generators ADD COLUMN commercialPricePerAmpere REAL NOT NULL DEFAULT 0.0")
                db.execSQL("UPDATE generators SET commercialPricePerAmpere = pricePerAmpere")

                db.execSQL("ALTER TABLE subscribers ADD COLUMN subscriberType TEXT NOT NULL DEFAULT 'منزلي'")

                db.execSQL("ALTER TABLE invoices ADD COLUMN subscriberType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN costPricePerAmpere REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN profit REAL NOT NULL DEFAULT 0.0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_change_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        generatorId INTEGER NOT NULL,
                        generatorName TEXT NOT NULL,
                        oldCostPrice REAL NOT NULL,
                        newCostPrice REAL NOT NULL,
                        oldResidentialPrice REAL NOT NULL,
                        newResidentialPrice REAL NOT NULL,
                        oldCommercialPrice REAL NOT NULL,
                        newCommercialPrice REAL NOT NULL,
                        changeDate INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * يضيف: عدد أيام الاستحقاق الاختياري لكل بند صيانة (بجانب ساعات التشغيل)،
         * حتى يمكن ضبط بند مثل "تغيير الزيت كل 250 ساعة أو كل 90 يوم أيهما أقرب".
         * القيمة الافتراضية 0 تعني: تنبيه بالساعات فقط كما كان سابقًا، بدون أي تغيير
         * على سلوك البنود الحالية بعد الترقية.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE maintenance_items ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * يضيف: عمود الخصم لكل فاتورة (اختياري، القيمة الافتراضية صفر = بدون خصم كما كان
         * سلوك التطبيق سابقًا). "amount" يبقى كما هو المبلغ الصافي بعد الخصم.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN discount REAL NOT NULL DEFAULT 0.0")
            }
        }

        /** يضيف عمود "الشهر المدفوع عنه" لكل فاتورة، يظهر بالوصل كـ "تم الدفع لشهر: ..." */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN paidForMonth TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "generator_app.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    // احتياطًا فقط لأي قفزة إصدار غير متوقعة لا تغطيها خطوات Migration أعلاه.
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
