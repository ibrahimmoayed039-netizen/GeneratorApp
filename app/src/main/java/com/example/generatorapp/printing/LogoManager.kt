package com.example.generatorapp.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * يدير حفظ شعار المحل بشكل دائم داخل تخزين التطبيق الخاص (Internal Storage)
 * بحيث يبقى محفوظًا حتى بعد إغلاق التطبيق أو إعادة تشغيل الجهاز.
 */
object LogoManager {

    private const val LOGO_FILE_NAME = "shop_logo.png"
    private const val MAX_DIMENSION = 600 // بكسل، كافٍ لجودة طباعة جيدة بدون تضخيم الحجم

    private fun logoFile(context: Context): File =
        File(context.filesDir, LOGO_FILE_NAME)

    fun hasLogo(context: Context): Boolean = logoFile(context).exists()

    /** يحفظ الصورة المختارة من المعرض بعد تصغيرها، ويحذف أي شعار سابق */
    fun saveLogo(context: Context, uri: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            val original = BitmapFactory.decodeStream(input)
            input.close()

            val scaled = scaleDown(original, MAX_DIMENSION)
            FileOutputStream(logoFile(context)).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadLogo(context: Context): Bitmap? {
        val file = logoFile(context)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun deleteLogo(context: Context) {
        logoFile(context).delete()
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val ratio = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1f // لا نكبّر الصور الصغيرة
        )
        if (ratio >= 1f) return bitmap
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
