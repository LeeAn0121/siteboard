package com.jongwook.siteboard

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CloudBackupManager {

    private const val PREFS_NAME = "SiteboardPrefs"
    private const val KEY_AUTO_BACKUP = "cloud_auto_backup_enabled"
    private const val KEY_LAST_AUTO_BACKUP_TS = "cloud_last_auto_backup_ts"
    private const val AUTO_BACKUP_MIN_INTERVAL_MS = 5 * 60 * 1000L // 5분 이상 간격
    val DRIVE_SCOPE = Scope("https://www.googleapis.com/auth/drive.file")

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class DriveFile(
        val id: String,
        val name: String,
        val createdTime: String,
        val size: Long
    )

    // ── 자동 백업 설정 ─────────────────────────────────────────────────────────
    fun isAutoBackupEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP, false)

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    /** DB 변경 후 호출 — 자동 백업이 켜져 있고 최근 5분 내 업로드가 없으면 Drive에 업로드 */
    fun triggerAutoBackup(context: Context) {
        if (!isAutoBackupEnabled(context)) return
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return
        if (!GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_AUTO_BACKUP_TS, 0L)
        if (System.currentTimeMillis() - last < AUTO_BACKUP_MIN_INTERVAL_MS) return

        bgScope.launch {
            try {
                val token = getToken(context, account) ?: return@launch
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                upload(token, "siteboard_sites_$ts.json", buildSiteListJson(context))
                upload(token, "siteboard_settings_$ts.json",
                    SettingsBackupManager.buildSettingsBackupJson(context))
                prefs.edit().putLong(KEY_LAST_AUTO_BACKUP_TS, System.currentTimeMillis()).apply()
                Log.d("CloudBackup", "Auto-backup completed: $ts")
            } catch (e: Exception) {
                Log.e("CloudBackup", "Auto-backup failed: ${e.message}")
            }
        }
    }

    // ── Drive 파일 목록 조회 ──────────────────────────────────────────────────
    suspend fun listSiteBackups(token: String): List<DriveFile> {
        val url = java.net.URL(
            "https://www.googleapis.com/drive/v3/files" +
            "?q=name+contains+%27siteboard_sites%27" +
            "&orderBy=createdTime+desc" +
            "&fields=files(id,name,createdTime,size)" +
            "&pageSize=20"
        )
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode !in 200..299) {
                throw Exception("HTTP ${conn.responseCode}")
            }
            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            val files = root.optJSONArray("files") ?: return emptyList()
            return (0 until files.length()).map { i ->
                val f = files.getJSONObject(i)
                DriveFile(
                    id = f.getString("id"),
                    name = f.getString("name"),
                    createdTime = f.optString("createdTime", ""),
                    size = f.optLong("size", 0L)
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    // ── Drive 파일 다운로드 ───────────────────────────────────────────────────
    suspend fun downloadFile(token: String, fileId: String): String {
        val url = java.net.URL(
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        )
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode !in 200..299) {
                throw Exception("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // ── DB 복원 ───────────────────────────────────────────────────────────────
    suspend fun restoreFromJson(context: Context, json: String): Int {
        val arr = JSONArray(json)
        val db = AppDatabase.getDatabase(context)
        db.postDao().deleteAll()
        var count = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.postDao().insert(
                PostEntity(
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    location = obj.optString("location").ifEmpty { null },
                    imageUri = obj.optString("imageUri"),
                    date = obj.getString("date"),
                    detailLocation = obj.optString("detailLocation").ifEmpty { null },
                    memo = obj.optString("memo").ifEmpty { null },
                    originalUri = obj.optString("originalUri").ifEmpty { null },
                    originalFileName = obj.optString("originalFileName").ifEmpty { null },
                    extraFields = obj.optString("extraFields").ifEmpty { null }
                )
            )
            count++
        }
        AppDatabase.backupNow(context)
        SiteboardWidgetManager.refreshAll(context)
        return count
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────
    fun getToken(context: Context, account: GoogleSignInAccount): String? = try {
        GoogleAuthUtil.getToken(
            context, account.account!!,
            "oauth2:https://www.googleapis.com/auth/drive.file"
        )
    } catch (e: Exception) {
        Log.e("CloudBackup", "Token error: ${e.message}")
        null
    }

    fun upload(token: String, fileName: String, content: String) {
        val boundary = "sb_mp_bdry"
        val url = java.net.URL(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        )
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            val meta = """{"name":"$fileName","mimeType":"application/json"}"""
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$meta\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n" +
                "$content\r\n--$boundary--"
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception(conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun buildSiteListJson(context: Context): String {
        val posts = AppDatabase.getDatabase(context).postDao().getAllPostsOnce()
        val arr = JSONArray()
        posts.forEach { post ->
            arr.put(JSONObject().apply {
                put("title", post.title)
                put("description", post.description)
                put("location", post.location ?: "")
                put("date", post.date)
                put("detailLocation", post.detailLocation ?: "")
                put("memo", post.memo ?: "")
                put("imageUri", post.imageUri)
                put("originalUri", post.originalUri ?: "")
                put("originalFileName", post.originalFileName ?: "")
                put("extraFields", post.extraFields ?: "")
            })
        }
        return arr.toString(2)
    }
}
