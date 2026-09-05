package com.example.generatorapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.generatorapp.printing.ReceiptData
import com.example.generatorapp.util.Formatters

/**
 * يعرض الوصل بتصميم بطاقة احترافي (شعار، عنوان، بيانات مرتّبة، مبلغ إجمالي بارز) داخل التطبيق،
 * ليتحقق منه المستخدم قبل الطباعة. هذا التصميم للعرض داخل التطبيق فقط؛ الطباعة الفعلية
 * (حرارية أو عادية) تبقى نصًا بسيطًا مطابقًا للمحتوى لأن الطابعات الحرارية لا تدعم الألوان
 * أو التنسيقات المتقدمة — التطابق هنا في البيانات، لا في الشكل الحرفي.
 */
@Composable
fun ReceiptPreview(receipt: ReceiptData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        receipt.logo?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "شعار المحل",
                modifier = Modifier
                    .height(64.dp)
                    .padding(bottom = 8.dp)
            )
        }

        Text(
            text = receipt.shopName,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        if (receipt.shopPhone.isNotBlank()) {
            Text(
                text = "هاتف: ${receipt.shopPhone}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "وصل دفع",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        DashedDivider()
        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReceiptInfoRow("التاريخ", receipt.formattedDate())
            ReceiptInfoRow("المشترك", receipt.subscriberName)
            if (receipt.meterNumber.isNotBlank()) {
                ReceiptInfoRow("رقم العداد", receipt.meterNumber)
            }
            ReceiptInfoRow("المولد", receipt.generatorName)
            ReceiptInfoRow("عدد الأمبيرات", "${Formatters.formatMoney(receipt.amperes)} أمبير")
            ReceiptInfoRow("سعر الأمبير", Formatters.formatMoney(receipt.pricePerAmpere))
        }

        Spacer(modifier = Modifier.height(14.dp))
        DashedDivider()
        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "المبلغ الإجمالي",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = Formatters.formatMoney(receipt.amount),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (receipt.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ملاحظة: ${receipt.note}",
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        DashedDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "شكراً لتعاملكم معنا",
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** صف معلومة داخل الوصل: تسمية باهتة على جهة، وقيمة بارزة على الجهة الأخرى */
@Composable
private fun ReceiptInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** خط فاصل متقطّع (يشبه خط تمزيق الوصل الحراري) بدل شرطات نصية "-----" */
@Composable
private fun DashedDivider() {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    }
}
