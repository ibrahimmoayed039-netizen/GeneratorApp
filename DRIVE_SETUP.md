# إعداد رفع النسخ الاحتياطية إلى Google Drive (مرة واحدة)

الحزمة (applicationId): com.example.generatorapp
بصمة SHA-1 للمفتاح الثابت المرفق (app/debug.keystore):
FA:87:B6:0F:48:FC:DF:6F:2D:70:3A:89:16:B4:17:52:D5:45:F0:0D

1. https://console.cloud.google.com ← أنشئ مشروعًا جديدًا.
2. APIs & Services ← Library ← فعّل "Google Drive API".
3. OAuth consent screen: النوع External، أدخل اسم التطبيق وبريدك. أضف النطاق (Scope):
   .../auth/drive.file  ثم أضف بريد حسابك في Test users. (اتركه بحالة Testing.)
4. Credentials ← Create credentials ← OAuth client ID:
   - Application type: Android
   - Package name: com.example.generatorapp
   - SHA-1: القيمة أعلاه
5. ابنِ التطبيق وثبّته، ثم الإعدادات ← "ربط حساب Google Drive".
