package com.example.generatorapp.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل مولد كهرباء واحد وأسعاره:
 * - سعر تكلفة الأمبير (ما يدفعه صاحب المولد لكل أمبير: وقود، صيانة...)
 * - سعر بيع الأمبير للمشترك المنزلي
 * - سعر بيع الأمبير للمشترك التجاري
 * أي تعديل على هذه الأسعار من شاشة المولدات ينعكس فورًا على كل المشتركين المرتبطين
 * بهذا المولد، لأن الفاتورة تُحسب دائمًا بالسعر الحالي المخزّن هنا وليس بسعر مجمّد.
 */
@Entity(tableName = "generators")
data class Generator(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val capacityKva: Double,
    /** تكلفة الأمبير الواحد على صاحب المولد */
    val costPricePerAmpere: Double = 0.0,
    /** سعر بيع الأمبير للمشترك المنزلي (اسم العمود بقي pricePerAmpere حفاظًا على البيانات القديمة) */
    @ColumnInfo(name = "pricePerAmpere")
    val residentialPricePerAmpere: Double,
    /** سعر بيع الأمبير للمشترك التجاري */
    val commercialPricePerAmpere: Double = 0.0,
    /** آخر قراءة معروفة لعداد ساعات التشغيل (تُحدَّث تلقائيًا عند إضافة قراءة جديدة بسجل الساعات) */
    val currentHours: Double = 0.0
) {
    /** سعر البيع المناسب حسب نوع المشترك (منزلي/تجاري) */
    fun sellPriceFor(subscriberType: String): Double =
        if (subscriberType == SubscriberType.COMMERCIAL) commercialPricePerAmpere else residentialPricePerAmpere

    /** هامش الربح للأمبير الواحد حسب نوع المشترك (سعر البيع - سعر التكلفة) */
    fun profitPerAmpereFor(subscriberType: String): Double = sellPriceFor(subscriberType) - costPricePerAmpere
}
