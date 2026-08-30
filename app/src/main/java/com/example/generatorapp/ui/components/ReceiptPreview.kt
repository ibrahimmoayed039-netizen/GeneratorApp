package com.example.generatorapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.generatorapp.printing.ReceiptData

/**
 * يعرض الوصل بنفس الشكل الذي سيُطبع فعليًا (نص أحادي المسافة يحاكي مخرجات الطابعة الحرارية)
 * ليتحقق منه المستخدم قبل الضغط على "طباعة".
 */
@Composable
fun ReceiptPreview(receipt: ReceiptData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        receipt.logo?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "شعار المحل",
                modifier = Modifier
                    .height(70.dp)
                    .padding(bottom = 6.dp)
            )
        }

        Text(
            text = receipt.shopName,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = Color.Black, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            receipt.toLines().drop(1).forEach { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
            }
        }
    }
}
