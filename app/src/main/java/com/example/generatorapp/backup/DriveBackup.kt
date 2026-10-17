package com.example.generatorapp.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * رفع النسخة الاحتياطية إلى حساب Google Drive الخاص بالمستخدم عبر Drive REST API.
 *
 * - الصلاحية المطلوبة drive.file فقط: التطبيق يرى ويعدّل الملف الذي أنشأه هو، ولا يرى بقية ملفات Drive.
 * - الملف يظهر في Drive باسم [FILE_NAME] ويُستبدل محتواه في كل مرة (Drive يحتفظ بنسخ قديمة تلقائيًا).
 * - كل الدوال هنا "حاجبة" (blocking) ويجب استدعاؤها من خيط خلفي (IO) فقط.
 */
object DriveBackup {

    private const val PREFS = "drive_backup_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EMAIL = "email"
    private const val KEY_FILE_ID = "file_id"
    private const val KEY_STATUS = "status"
    const val FILE_NAME = "GeneratorApp_backup.db"
    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

    private val statusFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, false)
    fun email(c: Context): String? = prefs(c).getString(KEY_EMAIL, null)
    fun status(c: Context): String? = prefs(c).getString(KEY_STATUS, null)
    private fun setStatus(c: Context, text: String) = prefs(c).edit().putString(KEY_STATUS, text).apply()

    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE))).build()

    /** يُستدعى بعد موافقة المستخدم على الربط */
    fun onAuthorized(c: Context, result: AuthorizationResult) {
        val mail = try { result.toGoogleSignInAccount()?.email } catch (_: Exception) { null }
        prefs(c).edit().putBoolean(KEY_ENABLED, true).apply {
            if (mail != null) putString(KEY_EMAIL, mail)
        }.apply()
    }

    fun disable(c: Context) {
        prefs(c).edit().clear().apply()
    }

    /** توكن وصول بدون أي نافذة (يعمل بالخلفية). null لو لزم أن يوافق المستخدم من جديد. */
    private fun silentToken(c: Context): String? {
        val res = Tasks.await(
            Identity.getAuthorizationClient(c).authorize(authorizationRequest()),
            30, TimeUnit.SECONDS
        )
        return if (res.hasResolution()) null else res.accessToken
    }

    private class Resp(val code: Int, val body: String)

    private fun call(
        method: String, url: String, token: String,
        contentType: String? = null, body: ByteArray? = null
    ): Resp {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (method == "PATCH") {
                // HttpURLConnection لا يدعم PATCH مباشرة، فنستخدم POST مع ترويسة تجاوز الطريقة
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Resp(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun createFile(token: String, bytes: ByteArray): String {
        val boundary = "gen" + System.currentTimeMillis()
        val meta = JSONObject().put("name", FILE_NAME).put("mimeType", "application/octet-stream").toString()
        val out = ByteArrayOutputStream()
        out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
        out.write("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
        out.write(bytes)
        out.write("\r\n--$boundary--".toByteArray())
        val r = call(
            "POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            token, "multipart/related; boundary=$boundary", out.toByteArray()
        )
        if (r.code !in 200..299) throw IllegalStateException(describeError(r))
        return JSONObject(r.body).getString("id")
    }

    private fun describeError(r: Resp): String = when (r.code) {
        401, 403 -> "رفض Google الطلب (${r.code}): تأكد من تفعيل Drive API وإضافة حسابك كمستخدم اختبار"
        else -> "خطأ Drive ${r.code}"
    }

    /** يرفع [bytes] (محتوى ملف النسخة) إلى Drive، وينشئ الملف أول مرة ثم يستبدل محتواه بعدها. */
    fun upload(c: Context, bytes: ByteArray): Boolean {
        if (!isEnabled(c)) return false
        return try {
            val token = silentToken(c) ?: throw IllegalStateException("يلزم إعادة ربط حساب Google من الإعدادات")
            var id = prefs(c).getString(KEY_FILE_ID, null)
            if (id != null) {
                val r = call(
                    "PATCH", "https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media",
                    token, "application/octet-stream", bytes
                )
                if (r.code == 404) id = null
                else if (r.code !in 200..299) throw IllegalStateException(describeError(r))
            }
            if (id == null) {
                id = createFile(token, bytes)
                prefs(c).edit().putString(KEY_FILE_ID, id).apply()
            }
            setStatus(c, "آخر رفع ناجح إلى Drive: " + statusFormat.format(Date()))
            true
        } catch (e: Exception) {
            setStatus(c, "فشل الرفع إلى Drive: " + (e.message ?: e.javaClass.simpleName))
            false
        }
    }

    /** ينزّل آخر نسخة من Drive إلى [dest]. يرجع نص الخطأ أو null عند النجاح. */
    fun downloadLatest(c: Context, dest: File): String? {
        return try {
            val token = silentToken(c) ?: return "يلزم إعادة ربط حساب Google من الإعدادات"
            var id = prefs(c).getString(KEY_FILE_ID, null)
            if (id == null) id = findLatestId(token)
            if (id == null) return "لم أجد نسخة باسم $FILE_NAME في Drive"

            var r = download(token, id, dest)
            if (r == 404) {
                // المعرّف المحفوظ قديم/محذوف، نبحث بالاسم
                id = findLatestId(token) ?: return "لم أجد نسخة باسم $FILE_NAME في Drive"
                r = download(token, id, dest)
            }
            if (r !in 200..299) return "تعذّر التنزيل من Drive (خطأ $r)"
            prefs(c).edit().putString(KEY_FILE_ID, id).apply()
            null
        } catch (e: Exception) {
            "تعذّر التنزيل من Drive: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun findLatestId(token: String): String? {
        val q = URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$q&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id)"
        val r = call("GET", url, token)
        if (r.code !in 200..299) throw IllegalStateException(describeError(r))
        val files = JSONObject(r.body).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun download(token: String, id: String, dest: File): Int {
        val conn = URL("https://www.googleapis.com/drive/v3/files/$id?alt=media").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
            return code
        } finally {
            conn.disconnect()
        }
    }
}
