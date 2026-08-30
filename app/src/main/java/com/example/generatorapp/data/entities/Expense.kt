package com.example.generatorapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * يمثل مصروف تشغيلي (ديزل، صيانة، أو أخرى) يُستخدم لحساب صافي الربح
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // "ديزل" | "صيانة" | "أخرى"
    val amount: Double,
    val date: Long,
    val note: String = ""
)
