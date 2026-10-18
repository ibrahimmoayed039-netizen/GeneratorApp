plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.generatorapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.generatorapp"
        minSdk = 24
        targetSdk = 34
        // رقم الإصدار يرتفع تلقائيًا مع كل بناء على GitHub Actions (GITHUB_RUN_NUMBER)،
        // لأن أندرويد يرفض التحديث إذا لم يكن versionCode أكبر من المثبّت.
        // يبدأ من 100 حتى يكون أكبر من الإصدارات السابقة (التي كانت 1) وعند البناء المحلي يبقى 100.
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + runNumber
        versionName = "1.0.$runNumber"
    }

    // مفتاح توقيع ثابت (app/debug.keystore): بدونه يولّد GitHub Actions مفتاحًا جديدًا في كل بناء،
    // فتتغير بصمة SHA-1 كل مرة ويفشل تسجيل الدخول بحساب Google (Drive).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")

    // مكتبة Material Components التقليدية (View-based) — مطلوبة فقط عشان توفّر
    // ثيم "Theme.Material3.DayNight.NoActionBar" المستخدم بـ AndroidManifest.xml
    // (لا علاقة له بـ Compose Material3 المستخدم داخل الشاشات نفسها)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel + Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Room (تخزين محلي دائم)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager (لتنبيهات المتأخرين اليومية التلقائية)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // تسجيل الدخول/التفويض بحساب Google (لرفع النسخ الاحتياطية إلى Google Drive)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
