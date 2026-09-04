package com.example.generatorapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.generatorapp.printing.BluetoothConnectionManager
import com.example.generatorapp.printing.findPreferredThermalPrinter
import com.example.generatorapp.ui.navigation.AppNavigation
import com.example.generatorapp.ui.theme.GeneratorAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* لا حاجة لفعل شيء هنا؛
            سواء وافق المستخدم أو لا، فحص الصيانة اليومي مجدول أصلاً من GeneratorApp،
            وسيبدأ بعرض الإشعارات فور منح الصلاحية لاحقًا لو تم رفضها الآن */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // نطلب صلاحية الإشعارات مرة واحدة عند أول فتح للتطبيق (أندرويد 13 فأعلى)
        // حتى تصل تنبيهات الصيانة المجدولة تلقائيًا بدون الحاجة لدخول المستخدم لشاشة الإعدادات
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        reconnectSavedPrinterQuietly()

        setContent {
            GeneratorAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    /**
     * يعيد الاتصال تلقائيًا بالطابعة الحرارية المحفوظة (إن وُجدت) فور فتح التطبيق، بدل
     * انتظار أول عملية طباعة — فتبدو الطابعة "متصلة دائمًا" من لحظة فتح التطبيق. لا يفعل
     * شيئًا إن لم تكن هناك طابعة محفوظة بعد، أو إن لم تُمنح صلاحية BLUETOOTH_CONNECT بعد
     * (ستُطلب لاحقًا من شاشة الإعدادات عند اختيار الطابعة لأول مرة).
     */
    private fun reconnectSavedPrinterQuietly() {
        val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasBluetoothPermission) return

        lifecycleScope.launch(Dispatchers.IO) {
            val device = findPreferredThermalPrinter(applicationContext) ?: return@launch
            BluetoothConnectionManager.connectQuietly(device)
        }
    }
}
