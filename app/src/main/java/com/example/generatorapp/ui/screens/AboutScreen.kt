package com.example.generatorapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.generatorapp.BuildConfig
import com.example.generatorapp.R

/**
 * شاشة "حول البرنامج" المستقلة — تعرض شعار التطبيق واسمه وإصداره، ومعلومات المصمم/المطوّر
 * (الاسم، رقم الهاتف القابل للاتصال المباشر، والموقع)، بدل أن تكون قسمًا مدمجًا داخل
 * شاشة الإعدادات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حول البرنامج") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "شعار التطبيق",
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Text(
                stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "الإصدار ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "حالة التفعيل: ${com.example.generatorapp.activation.ActivationManager.statusText(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    AboutInfoRow(
                        icon = Icons.Default.Person,
                        label = "تصميم وتطوير",
                        value = "المهندس إبراهيم مؤيد عطارباشي"
                    )
                    AboutInfoRow(
                        icon = Icons.Default.Phone,
                        label = "رقم الهاتف",
                        value = "+9647736970504",
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+9647736970504"))
                            context.startActivity(intent)
                        }
                    )
                    AboutInfoRow(
                        icon = Icons.Default.LocationOn,
                        label = "الموقع",
                        value = "الموصل - حي الثقافة - شركة المسار الذهبي"
                    )
                }
            }

            Text(
                "جميع الحقوق محفوظة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** صف معلومة داخل شاشة "حول البرنامج" (أيقونة + تسمية + قيمة)، قابل للنقر اختياريًا (مثل الاتصال بالهاتف) */
@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
