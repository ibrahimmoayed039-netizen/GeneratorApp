package com.example.generatorapp.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.generatorapp.R
import com.example.generatorapp.security.PinSession
import com.example.generatorapp.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_SECONDS = 30

/**
 * شاشة الدخول: تظهر عند فتح التطبيق وتطلب رمز المدير أو رمز أي موظف مفعّل.
 * عند النجاح تُفتح الجلسة باسم صاحب الرمز (فيُسجَّل اسمه على الدفعات) ثم يظهر التطبيق.
 * بعد 5 محاولات خاطئة متتالية يُقفل الإدخال 30 ثانية.
 */
@Composable
fun LoginScreen(viewModel: MainViewModel = viewModel()) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableStateOf(0L) }
    var secondsLeft by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }

    // عدّاد تنازلي أثناء فترة القفل
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > SystemClock.elapsedRealtime()) {
            secondsLeft = ((lockedUntil - SystemClock.elapsedRealtime() + 999) / 1000).toInt()
            delay(250)
        }
        secondsLeft = 0
    }

    val locked = secondsLeft > 0

    fun submit() {
        if (locked || checking || pin.isBlank()) return
        checking = true
        viewModel.authenticate(pin) { ok ->
            checking = false
            if (ok) {
                error = null
                PinSession.markLoggedIn()
            } else {
                failedAttempts++
                pin = ""
                if (failedAttempts >= MAX_ATTEMPTS) {
                    failedAttempts = 0
                    lockedUntil = SystemClock.elapsedRealtime() + LOCKOUT_SECONDS * 1000L
                    error = "محاولات كثيرة خاطئة"
                } else {
                    error = "الرمز غير صحيح"
                }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "شعار التطبيق",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text("تسجيل الدخول", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "أدخل رمزك الشخصي (PIN) للدخول إلى البرنامج",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) pin = it },
                label = { Text("رمز PIN") },
                singleLine = true,
                enabled = !locked,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (locked) {
                Text(
                    "أعد المحاولة بعد $secondsLeft ثانية",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { submit() },
                enabled = !locked && !checking && pin.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("دخول") }
        }
    }
}
