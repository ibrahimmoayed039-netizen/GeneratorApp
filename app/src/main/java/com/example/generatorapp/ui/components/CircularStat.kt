package com.example.generatorapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * مؤشر دائري (Gauge) يعرض قيمة داخل حلقة تقدُّم:
 * - [value]: النص المعروض في المنتصف (مثلاً "1240 A" أو "%35").
 * - [label]: وصف قصير أسفل الدائرة (مثلاً "الأمبيرات المستخدمة").
 * - [progress]: نسبة تعبئة الحلقة من 0f إلى 1f (تُقصّ تلقائيًا ضمن هذا المدى).
 * - [color]: لون الحلقة والنص، يُستخدم غالبًا للتمييز بين موجب/سالب.
 */
@Composable
fun CircularStat(
    value: String,
    label: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.11f
                val arcDiameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - arcDiameter) / 2f,
                    (size.height - arcDiameter) / 2f
                )
                val arcSize = Size(arcDiameter, arcDiameter)

                // الحلقة الخلفية (الخلفية الباهتة الكاملة)
                drawArc(
                    color = color.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // حلقة التقدّم الفعلية
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
