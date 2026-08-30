package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل مولد كهرباء واحد وسعره لكل أمبير
 */
@Entity(tableName = "generators")
data class Generator(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val capacityKva: Double,
    val pricePerAmpere: Double
)
