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
import com.example.generatorapp.data.dao.SubscriberDao
import com.example.generatorapp.data.dao.SubscriptionDao
import com.example.generatorapp.data.entities.AmpereChangeLog
import com.example.generatorapp.data.entities.Expense
import com.example.generatorapp.data.entities.FaultLog
import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.GeneratorHourLog
import com.example.generatorapp.data.entities.Invoice
import com.example.generatorapp.data.entities.MaintenanceItem
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
        MaintenanceItem::class, FaultLog::class
    ],
    version = 5,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "generator_app.db"
                )
                    .addMigrations(MIGRATION_4_5)
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
