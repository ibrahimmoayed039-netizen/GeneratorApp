package com.example.generatorapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.generatorapp.data.entities.MONEY_EPSILON
import com.example.generatorapp.util.Formatters

/** المبلغ المستحق الآن = إجمالي الفاتورة - ما دُفع سابقًا لنفس الشهر (وإن كان الشهر مسدَّدًا فهي فاتورة جديدة كاملة) */
fun amountOwedNow(totalDue: Double, paidBefore: Double): Double =
    (totalDue - paidBefore).let { if (it <= MONEY_EPSILON) totalDue else it }

/**
 * يحوّل اختيار المستخدم (دفع كامل / جزء) إلى المبلغ المدفوع.
 * يرجع Pair(المبلغ، رسالة الخطأ): المبلغ null = دفع كامل المستحق، ورسالة الخطأ غير null = إدخال غير صالح.
 */
fun resolvePaidAmount(
    totalDue: Double,
    paidBefore: Double,
    partial: Boolean,
    partialText: String
): Pair<Double?, String?> {
    if (!partial) return null to null
    val owed = amountOwedNow(totalDue, paidBefore)
    val entered = partialText.trim().toDoubleOrNull()
    return when {
        entered == null || entered <= 0.0 -> null to "أدخل المبلغ المدفوع"
        entered > owed + MONEY_EPSILON ->
            null to "المبلغ أكبر من المتبقي (${Formatters.formatMoney(owed)})"
        else -> entered to null
    }
}

/**
 * قسم "طريقة التسديد": تسديد كامل المبلغ المستحق أو دفع جزء منه فقط (والباقي يبقى دينًا على المشترك).
 * مشترك بين شاشة الفواتير ونافذة الدفع السريع في حالة الدفع الشهرية.
 */
@Composable
fun PaymentAmountSection(
    totalDue: Double,
    paidBefore: Double,
    partial: Boolean,
    onPartialChange: (Boolean) -> Unit,
    partialText: String,
    onPartialTextChange: (String) -> Unit
) {
    if (totalDue <= 0.0) return
    val owed = amountOwedNow(totalDue, paidBefore)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (owed < totalDue - MONEY_EPSILON) {
            Text(
                "مدفوع سابقًا هذا الشهر: ${Formatters.formatMoney(paidBefore)} — المتبقي: ${Formatters.formatMoney(owed)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Text("طريقة التسديد", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !partial,
                onClick = { onPartialChange(false) },
                label = { Text("تسديد كامل (${Formatters.formatMoney(owed)})") }
            )
            FilterChip(
                selected = partial,
                onClick = { onPartialChange(true) },
                label = { Text("دفع جزء") }
            )
        }
        if (partial) {
            OutlinedTextField(
                value = partialText,
                onValueChange = onPartialTextChange,
                label = { Text("المبلغ المدفوع الآن") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            val entered = partialText.trim().toDoubleOrNull()
            if (entered != null && entered > 0.0) {
                if (entered > owed + MONEY_EPSILON) {
                    Text(
                        "المبلغ أكبر من المتبقي (${Formatters.formatMoney(owed)})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val left = (owed - entered).let { if (it <= MONEY_EPSILON) 0.0 else it }
                    Text(
                        if (left > 0.0) "المتبقي بعد هذه الدفعة: ${Formatters.formatMoney(left)}"
                        else "هذه الدفعة تسدّد المبلغ بالكامل",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
