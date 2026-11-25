package com.example.generatorapp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.generatorapp.R
import com.example.generatorapp.activation.ActivationManager

/**
 * شاشة تفعيل التطبيق — تظهر بدل الشاشة الرئيسية طالما التطبيق غير مفعّل (أو انتهت مدة
 * تفعيله المؤقت). تعرض "رقم الجهاز" الذي يرسله صاحب المحل للمطوّر، وحقل إدخال كود
 * التفعيل الذي يستلمه بالمقابل من أداة توليد الأكواد.
 */
@Composable
fun ActivationScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val deviceId = remember { ActivationManager.getDeviceId(context) }
    var shopName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "شعار التطبيق",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Text(
                "تفعيل التطبيق",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "هذا التطبيق يحتاج كود تفعيل لمرة واحدة لكل جهاز. أدخل اسم محلك، وأرسل رقم " +
                    "الجهاز أدناه للمطوّر عبر الهاتف أو واتساب، وسيرسل لك كود التفعيل الخاص بجهازك.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text("اسم المحل") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("رقم الجهاز", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        deviceId,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("رقم الجهاز", deviceId))
                        }) { Text("نسخ") }

                        OutlinedButton(onClick = {
                            val shopLine = if (shopName.isNotBlank()) "اسم المحل: $shopName\n" else ""
                            val message = Uri.encode(
                                "مرحبًا، أريد كود تفعيل لتطبيق مدير المولدات.\n$shopLine" +
                                    "رقم الجهاز: $deviceId"
                            )
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://wa.me/9647736970504?text=$message")
                            )
                            context.startActivity(intent)
                        }) { Text("إرسال عبر واتساب") }
                    }
                }
            }

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    errorMessage = null
                    successMessage = null
                },
                label = { Text("كود التفعيل") },
                placeholder = { Text("مثال: 000-12345") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    when (val result = ActivationManager.validateAndActivate(context, code)) {
                        is ActivationManager.ActivationResult.Success -> {
                            successMessage = result.message
                            errorMessage = null
                            onActivated()
                        }
                        is ActivationManager.ActivationResult.Error -> {
                            errorMessage = result.message
                            successMessage = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تفعيل") }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            successMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }

            Text(
                "هاتف المطوّر: +9647736970504",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
