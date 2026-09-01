package com.example.generatorapp.messaging

import com.example.generatorapp.data.entities.Generator
import com.example.generatorapp.data.entities.Subscriber
import com.example.generatorapp.data.entities.SubscriberType
import com.example.generatorapp.util.DateUtils

/**
 * يبني نص الرسالة النهائي من قالب نصي فيه متغيرات {name} {month} {price} {generator} {type}
 * ليُستخدم بكل من رسائل تذكير المتأخرين ورسائل سعر الأمبير الشهري.
 */
object MessageComposer {

    private fun typeLabel(subscriberType: String): String =
        if (subscriberType == SubscriberType.COMMERCIAL) "تجاري" else "منزلي"

    private fun fill(template: String, values: Map<String, String>): String {
        var result = template
        values.forEach { (key, value) -> result = result.replace("{$key}", value) }
        return result
    }

    /** رسالة تذكير لمشترك متأخر بالدفع */
    fun lateReminder(subscriber: Subscriber, template: String): String {
        return fill(
            template,
            mapOf(
                "name" to subscriber.name,
                "month" to DateUtils.monthName(DateUtils.currentMonth())
            )
        )
    }

    /** رسالة سعر الأمبير الشهري لمشترك معيّن حسب مولدته ونوع اشتراكه */
    fun priceMessage(subscriber: Subscriber, generator: Generator, template: String): String {
        val price = generator.sellPriceFor(subscriber.subscriberType)
        return fill(
            template,
            mapOf(
                "name" to subscriber.name,
                "month" to DateUtils.monthName(DateUtils.currentMonth()),
                "price" to formatPrice(price),
                "generator" to generator.name,
                "type" to typeLabel(subscriber.subscriberType)
            )
        )
    }

    private fun formatPrice(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
