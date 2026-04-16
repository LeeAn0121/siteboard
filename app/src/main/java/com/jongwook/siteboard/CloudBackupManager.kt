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
    private const val KEY_BACKUP_FOLDER_NAME = "cloud_backup_folder_name"
    private const val KEY_BACKUP_FOLDER_ID = "cloud_backup_folder_id"
    private const val DEFAULT_FOLDER_NAME = "SITEBOARD"
    private const val AUTO_BACKUP_MIN_INTERVAL_MS = 5 * 60 * 1000L
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
        prefs(context).getBoolean(KEY_AUTO_BACKUP, false)

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    // ── 백업 폴더 설정 ─────────────────────────────────────────────────────────
    fun getBackupFolderName(context: Context): String =
        prefs(context).getString(KEY_BACKUP_FOLDER_NAME, DEFAULT_FOLDER_NAME) ?: DEFAULT_FOLDER_NAME

    fun setBackupFolderName(context: Context, name: String) {
        prefs(context).edit()
            .putString(KEY_BACKUP_FOLDER_NAME, name.trim().ifEmpty { DEFAULT_FOLDER_NAME })
            .remove(KEY_BACKUP_FOLDER_ID) // 폴더명 변경 시 캐시 초기화
            .apply()
    }

    private fun getCachedFolderId(context: Context): String? =
        prefs(context).getString(KEY_BACKUP_FOLDER_ID, null)

    private fun setCachedFolderId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_BACKUP_FOLDER_ID, id).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 자동 백업 트리거 (JSON + 사진 모두 업로드) ──────────────────────────────
    fun triggerAutoBackup(context: Context) {
        if (!isAutoBackupEnabled(context)) return
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return
        if (!GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)) return

        val p = prefs(context)
        val last = p.getLong(KEY_LAST_AUTO_BACKUP_TS, 0L)
        // 자동 백업은 하루에 한 번 또는 최소 1시간 간격 등으로 조정 가능 (현재는 5분)
        if (System.currentTimeMillis() - last < AUTO_BACKUP_MIN_INTERVAL_MS) return

        bgScope.launch {
            try {
                val token = getToken(context, account) ?: return@launch
                
                // 용량 체크
                if (isStorageFull(token)) {
                    SiteboardNotificationManager.showStorageFullNotification(context)
                    return@launch
                }

                val parentId = findOrCreateFolder(context, token)
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                
                // 백업용 서브 폴더 생성 (선택 사항, 여기서는 기존처럼 파일명에 날짜 포함)
                // 1. 이미지 업로드
                val imageIdMap = uploadImages(context, token, parentId)
                
                // 2. JSON 업로드 (이미지 ID 포함)
                upload(token, "siteboard_sites_$ts.json", buildSiteListJson(context, imageIdMap), parentId)
                upload(token, "siteboard_settings_$ts.json",
                    SettingsBackupManager.buildSettingsBackupJson(context), parentId)

                p.edit().putLong(KEY_LAST_AUTO_BACKUP_TS, System.currentTimeMillis()).apply()
                Log.d("CloudBackup", "Auto-backup completed: $ts")
            } catch (e: Exception) {
                Log.e("CloudBackup", "Auto-backup failed: ${e.message}")
                if (e.message?.contains("quota", ignoreCase = true) == true || 
                    e.message?.contains("full", ignoreCase = true) == true) {
                    SiteboardNotificationManager.showStorageFullNotification(context)
                }
            }
        }
    }

    private fun isStorageFull(token: String): Boolean {
        val url = java.net.URL("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
        val conn = url.openConnection() as java.net.HttpURLConnection
        return try {
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode in 200..299) {
                val quota = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONObject("storageQuota")
                if (quota != null) {
                    val limit = quota.optLong("limit", -1L)
                    val usage = quota.optLong("usage", 0L)
                    if (limit > 0 && usage >= limit) return true
                }
            }
            false
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    // ── 폴더 찾기 / 생성 ──────────────────────────────────────────────────────
    suspend fun findOrCreateFolder(context: Context, token: String): String {
        val folderName = getBackupFolderName(context)
        val cached = getCachedFolderId(context)
        if (cached != null) return cached
        val existing = findFolder(token, folderName)
        if (existing != null) {
            setCachedFolderId(context, existing)
            return existing
        }
        val created = createFolderOnDrive(token, folderName)
        setCachedFolderId(context, created)
        return created
    }

    private fun findFolder(token: String, name: String): String? {
        val q = java.net.URLEncoder.encode(
            "name='$name' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            "UTF-8"
        )
        val url = java.net.URL(
            "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)&pageSize=1"
        )
        val conn = url.openConnection() as java.net.HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode !in 200..299) return null
            val files = JSONObject(conn.inputStream.bufferedReader().readText())
                .optJSONArray("files")
            if (files != null && files.length() > 0) files.getJSONObject(0).getString("id")
            else null
        } catch (e: Exception) {
            Log.e("CloudBackup", "findFolder failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    fun findFileByName(token: String, name: String): String? {
        val q = java.net.URLEncoder.encode(
            "name='$name' and trashed=false",
            "UTF-8"
        )
        val url = java.net.URL(
            "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)&pageSize=1"
        )
        val conn = url.openConnection() as java.net.HttpURLConnection
        return try {
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode !in 200..299) return null
            val files = JSONObject(conn.inputStream.bufferedReader().readText())
                .optJSONArray("files")
            if (files != null && files.length() > 0) files.getJSONObject(0).getString("id")
            else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun createFolderOnDrive(token: String, name: String): String {
        val url = java.net.URL("https://www.googleapis.com/drive/v3/files?fields=id")
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            val body = """{"name":"$name","mimeType":"application/vnd.google-apps.folder"}"""
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299)
                throw Exception("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().readText()).getString("id")
        } finally {
            conn.disconnect()
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
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
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

    // ── Drive 파일 다운로드 (텍스트) ──────────────────────────────────────────
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
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // ── Drive 파일 다운로드 (바이너리) ────────────────────────────────────────
    private fun downloadFileBytes(token: String, fileId: String): ByteArray {
        val url = java.net.URL(
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        )
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    // ── 이미지 업로드 (postId → driveFileId 맵 반환) ─────────────────────────
    fun uploadImages(
        context: Context,
        token: String,
        parentFolderId: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Map<Int, String> {
        val posts = AppDatabase.getDatabase(context).postDao().getAllPostsOnce()
        val result = mutableMapOf<Int, String>()
        val total = posts.count { it.imageUri.isNotEmpty() }
        var current = 0
        posts.forEach { post ->
            if (post.imageUri.isEmpty()) return@forEach
            try {
                val uri = android.net.Uri.parse(post.imageUri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val driveId = uploadBinaryFile(
                        token, "img_${post.id}.jpg", "image/jpeg", bytes, parentFolderId
                    )
                    result[post.id] = driveId
                }
            } catch (e: Exception) {
                Log.w("CloudBackup", "Image upload failed for post ${post.id}: ${e.message}")
                if (e.message?.contains("quota", ignoreCase = true) == true || 
                    e.message?.contains("full", ignoreCase = true) == true) {
                    throw e // 중단하고 상위에서 알림 처리
                }
            }
            current++
            onProgress?.invoke(current, total)
        }
        return result
    }

    // ── 바이너리 파일 Drive 업로드 (이미지용) ────────────────────────────────
    private fun uploadBinaryFile(
        token: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        parentFolderId: String
    ): String {
        val boundary = "sb_mp_bdry"
        val url = java.net.URL(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
        )
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            val meta = """{"name":"$fileName","mimeType":"$mimeType","parents":["$parentFolderId"]}"""
            val header = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$meta\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n").toByteArray(Charsets.UTF_8)
            val footer = "\r\n--$boundary--".toByteArray(Charsets.UTF_8)
            conn.outputStream.use { out ->
                out.write(header)
                out.write(data)
                out.write(footer)
            }
            if (conn.responseCode !in 200..299)
                throw Exception("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().readText()).getString("id")
        } finally {
            conn.disconnect()
        }
    }

    // ── DB 복원 ───────────────────────────────────────────────────────────────
    suspend fun restoreFromJson(
        context: Context,
        json: String,
        token: String? = null
    ): Int {
        val arr = JSONArray(json)
        val db = AppDatabase.getDatabase(context)
        db.postDao().deleteAll()
        var count = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            var imageUri = obj.optString("imageUri", "")

            // 로컬 URI가 유효하지 않고 Drive 이미지 ID가 있으면 다운로드
            if (imageUri.isNotEmpty() && token != null) {
                val canRead = try {
                    context.contentResolver
                        .openInputStream(android.net.Uri.parse(imageUri))?.use { true } ?: false
                } catch (_: Exception) { false }

                if (!canRead) {
                    val driveId = obj.optString("driveImageFileId", "")
                    if (driveId.isNotEmpty()) {
                        imageUri = restoreImageFromDrive(context, token, driveId, "restore_${count}.jpg")
                            ?: imageUri
                    }
                }
            }

            db.postDao().insert(
                PostEntity(
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    location = obj.optString("location").ifEmpty { null },
                    imageUri = imageUri,
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

    private fun restoreImageFromDrive(
        context: Context,
        token: String,
        driveFileId: String,
        fileName: String
    ): String? {
        return try {
            val bytes = downloadFileBytes(token, driveFileId)
            saveImageToMediaStore(context, fileName, bytes)
        } catch (e: Exception) {
            Log.e("CloudBackup", "Image restore failed: ${e.message}")
            null
        }
    }

    private fun saveImageToMediaStore(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): String? {
        val resolver = context.contentResolver
        val cv = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv
        ) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cv.clear()
            cv.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, cv, null, null)
        }
        return uri.toString()
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

    fun upload(
        token: String,
        fileName: String,
        content: String,
        parentFolderId: String? = null
    ) {
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
            val meta = if (parentFolderId != null)
                """{"name":"$fileName","mimeType":"application/json","parents":["$parentFolderId"]}"""
            else
                """{"name":"$fileName","mimeType":"application/json"}"""
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$meta\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n" +
                "$content\r\n--$boundary--"
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299)
                throw Exception(conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    fun buildSiteListJson(
        context: Context,
        imageIdMap: Map<Int, String> = emptyMap()
    ): String {
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
                put("driveImageFileId", imageIdMap[post.id] ?: "")
            })
        }
        return arr.toString(2)
    }
}
