package com.example.generatorapp.data.entities

/**
 * نوع المشترك: منزلي أو تجاري — لكل نوع سعر بيع أمبير مختلف عند كل مولد.
 */
object SubscriberType {
    const val RESIDENTIAL = "منزلي"
    const val COMMERCIAL = "تجاري"

    val ALL = listOf(RESIDENTIAL, COMMERCIAL)
}
