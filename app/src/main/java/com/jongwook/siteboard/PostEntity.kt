package com.jongwook.siteboard

import androidx.room.*
import androidx.room.migration.Migration
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "site_posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val location: String?,          // GPS 자동 위치
    val imageUri: String,
    val date: String,
    val detailLocation: String? = null,   // 상세 위치 (수기 입력)
    val memo: String? = null,             // 메모 (비고)
    val originalUri: String? = null,      // 원본 사진 URI
    val originalFileName: String? = null  // 원본 파일명
)

@Entity(tableName = "preference_snapshots")
data class PreferenceSnapshotEntity(
    @PrimaryKey val prefName: String,
    val jsonPayload: String
)

@Dao
interface PostDao {
    @Query("SELECT * FROM site_posts ORDER BY id DESC")
    fun getAllPosts(): kotlinx.coroutines.flow.Flow<List<PostEntity>>

    @Query("SELECT * FROM site_posts ORDER BY id DESC")
    fun getAllPostsOnce(): List<PostEntity>

    @Query("SELECT * FROM site_posts WHERE id = :id LIMIT 1")
    fun getById(id: Int): PostEntity?

    @Query("SELECT COUNT(*) FROM site_posts")
    fun countPosts(): Int

    @Insert
    fun insert(post: PostEntity)

    @Update
    fun update(post: PostEntity)

    @Delete
    fun delete(post: PostEntity)

    @Query("DELETE FROM site_posts")
    fun deleteAll()

    @Delete
    fun deleteList(posts: List<PostEntity>)
}

@Dao
interface PreferenceSnapshotDao {
    @Query("SELECT * FROM preference_snapshots")
    fun getAll(): List<PreferenceSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(snapshot: PreferenceSnapshotEntity)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE site_posts ADD COLUMN detailLocation TEXT")
        database.execSQL("ALTER TABLE site_posts ADD COLUMN memo TEXT")
        database.execSQL("ALTER TABLE site_posts ADD COLUMN originalUri TEXT")
        database.execSQL("ALTER TABLE site_posts ADD COLUMN originalFileName TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS preference_snapshots (" +
                "prefName TEXT NOT NULL PRIMARY KEY, " +
                "jsonPayload TEXT NOT NULL)"
        )
    }
}

@Database(entities = [PostEntity::class, PreferenceSnapshotEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun preferenceSnapshotDao(): PreferenceSnapshotDao
    companion object {
        private val PREF_NAMES = listOf(
            "SiteboardPrefs",
            "WatermarkPrefs",
            "SiteboardInspectionSchedules",
            "SiteboardProjectMeta",
            "SiteboardTemplates",
            "SiteboardWidgetPrefs"
        )
        private val floatPrefKeys = setOf("wm_font_size")
        private const val BACKUP_FILE_NAME = "siteboard_backup.sbbak"
        private const val BACKUP_FILE_GLOB = "siteboard_backup%"
        private val LEGACY_BACKUP_FILE_NAMES = listOf("siteboard_backup.sbbak", "siteboard_backup.db")
        private const val BACKUP_RELATIVE_PATH = "Download/SITEBOARD/"
        private const val INTERNAL_PREF_NAME = "SiteboardInternalPrefs"
        private const val KEY_BACKUP_URI = "backup_uri"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    migrateFromExternalIfNeeded(context)
                    buildDatabase(context).also {
                        INSTANCE = it
                        repairPreferences(context.applicationContext)
                    }
                }
            }
        }

        // Float로 저장해야 할 키가 Int로 잘못 저장된 경우 자동 교정
        private fun repairPreferences(context: android.content.Context) {
            for (prefName in PREF_NAMES) {
                val prefs = context.getSharedPreferences(prefName, android.content.Context.MODE_PRIVATE)
                val editor = prefs.edit()
                var changed = false
                for (key in floatPrefKeys) {
                    if (prefs.all[key] is Int) { editor.putFloat(key, (prefs.all[key] as Int).toFloat()); changed = true }
                }
                if (prefName == "WatermarkPrefs") {
                    if (!prefs.contains("wm_is_top")) { editor.putBoolean("wm_is_top", false); changed = true }
                    if (!prefs.contains("wm_is_left")) { editor.putBoolean("wm_is_left", true); changed = true }
                    if (!prefs.contains("wm_margin_x")) { editor.putInt("wm_margin_x", 10); changed = true }
                    if (!prefs.contains("wm_margin_y")) { editor.putInt("wm_margin_y", 50); changed = true }
                    if (!prefs.contains("wm_font_size")) { editor.putFloat("wm_font_size", 30f); changed = true }
                    if (!prefs.contains("wm_font")) { editor.putString("wm_font", "DEFAULT"); changed = true }
                    if (!prefs.contains("wm_color")) { editor.putInt("wm_color", android.graphics.Color.WHITE); changed = true }
                    if (!prefs.contains("wm_use_bg")) { editor.putBoolean("wm_use_bg", true); changed = true }
                }
                if (changed) editor.apply()
            }
        }

        private fun storedBackupUri(context: android.content.Context): android.net.Uri? {
            val raw = context.getSharedPreferences(INTERNAL_PREF_NAME, android.content.Context.MODE_PRIVATE)
                .getString(KEY_BACKUP_URI, null)
            return raw?.let { android.net.Uri.parse(it) }
        }

        fun rememberBackupUri(context: android.content.Context, uri: android.net.Uri) {
            context.getSharedPreferences(INTERNAL_PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKUP_URI, uri.toString())
                .apply()
        }

        private fun clearStoredBackupUri(context: android.content.Context) {
            context.getSharedPreferences(INTERNAL_PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_BACKUP_URI)
                .apply()
        }

        private fun canReadUri(context: android.content.Context, uri: android.net.Uri): Boolean {
            return try {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }

        // ── DB 경로: 내부 저장소 (Android Auto Backup 포함, 앱 삭제 시 자동 Google Drive 복원)
        private fun primaryDbFile(context: android.content.Context): java.io.File =
            context.applicationContext.getDatabasePath("siteboard.db")

        private fun buildDatabase(context: android.content.Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "siteboard.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // WAL 비활성화 → .db 파일이 항상 최신 상태
            .build()
        }

        private fun snapshotPreferencesToDb(context: android.content.Context) {
            val db = INSTANCE ?: return
            for (prefName in PREF_NAMES) {
                val prefs = context.getSharedPreferences(prefName, android.content.Context.MODE_PRIVATE)
                val root = JSONObject()
                for ((key, value) in prefs.all) {
                    val item = JSONObject()
                    when (value) {
                        is String -> { item.put("type", "string"); item.put("value", value) }
                        is Int -> { item.put("type", "int"); item.put("value", value) }
                        is Long -> { item.put("type", "long"); item.put("value", value) }
                        is Float -> { item.put("type", "float"); item.put("value", value.toDouble()) }
                        is Boolean -> { item.put("type", "boolean"); item.put("value", value) }
                        is Set<*> -> {
                            item.put("type", "string_set")
                            item.put("value", JSONArray(value.filterIsInstance<String>()))
                        }
                        else -> continue
                    }
                    root.put(key, item)
                }
                db.preferenceSnapshotDao().upsert(
                    PreferenceSnapshotEntity(prefName = prefName, jsonPayload = root.toString())
                )
            }
        }

        private fun restorePreferencesFromDb(context: android.content.Context) {
            val db = INSTANCE ?: return
            for (snapshot in db.preferenceSnapshotDao().getAll()) {
                val prefs = context.getSharedPreferences(snapshot.prefName, android.content.Context.MODE_PRIVATE)
                val editor = prefs.edit().clear()
                val root = JSONObject(snapshot.jsonPayload)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = root.optJSONObject(key) ?: continue
                    when (item.optString("type")) {
                        "string" -> editor.putString(key, item.optString("value"))
                        "int" -> editor.putInt(key, item.optInt("value"))
                        "long" -> editor.putLong(key, item.optLong("value"))
                        "float" -> editor.putFloat(key, item.optDouble("value").toFloat())
                        "boolean" -> editor.putBoolean(key, item.optBoolean("value"))
                        "string_set" -> {
                            val arr = item.optJSONArray("value") ?: JSONArray()
                            val values = mutableSetOf<String>()
                            for (i in 0 until arr.length()) values.add(arr.optString(i))
                            editor.putStringSet(key, values)
                        }
                    }
                }
                editor.apply()
            }
        }

        // ── 구 외부 저장소 경로(Android/data/.../files/)에서 내부 저장소로 1회 마이그레이션
        private fun migrateFromExternalIfNeeded(context: android.content.Context) {
            val ctx = context.applicationContext
            val newDb = primaryDbFile(ctx)
            if (newDb.exists()) return
            val oldDb = java.io.File(ctx.getExternalFilesDir(null) ?: return, "siteboard.db")
            if (!oldDb.exists()) return
            try {
                newDb.parentFile?.mkdirs()
                oldDb.copyTo(newDb, overwrite = true)
                listOf("siteboard.db-wal", "siteboard.db-shm").forEach { name ->
                    val old = java.io.File(oldDb.parent!!, name)
                    if (old.exists()) old.copyTo(java.io.File(newDb.parent!!, name), overwrite = true)
                }
                android.util.Log.d("SiteboardDB", "DB migrated to internal storage")
            } catch (e: Exception) {
                android.util.Log.e("SiteboardDB", "Migration failed: ${e.message}")
            }
        }

        // ── 앱 종료 시 Downloads/SITEBOARD/에 로컬 백업 저장
        fun backupToDownloads(context: android.content.Context) {
            val ctx = context.applicationContext
            val dbFile = primaryDbFile(ctx)
            if (!dbFile.exists()) return
            try {
                snapshotPreferencesToDb(ctx)
                try {
                    INSTANCE?.openHelper?.writableDatabase?.query("PRAGMA wal_checkpoint(FULL)")?.close()
                } catch (_: Exception) {
                    // TRUNCATE 모드에서는 checkpoint 실패가 치명적이지 않으므로 백업을 계속 진행한다.
                }
                val tmp = java.io.File(ctx.cacheDir, "sb_bak_tmp.db")
                dbFile.copyTo(tmp, overwrite = true)
                saveDbToDownloads(ctx, tmp)
                tmp.delete()
            } catch (e: Exception) {
                android.util.Log.e("SiteboardDB", "Backup failed: ${e.message}")
            }
        }

        // DB 변경 직후 즉시 로컬 백업을 남겨 재설치 시 복원 가능성을 높인다.
        fun backupNow(context: android.content.Context) {
            try {
                backupToDownloads(context.applicationContext)
            } catch (e: Exception) {
                android.util.Log.e("SiteboardDB", "Immediate backup failed: ${e.message}")
            }
        }

        private fun saveDbToDownloads(ctx: android.content.Context, file: java.io.File) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val collection = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val storedUri = storedBackupUri(ctx)?.takeIf { canReadUri(ctx, it) } ?: run {
                    clearStoredBackupUri(ctx)
                    null
                }
                val existingBefore = queryBackupEntries(resolver, collection)
                android.util.Log.d("SiteboardDB", "Backup candidates before save: ${existingBefore.joinToString { "${it.displayName}@${it.uri}" }}")
                val reusableStoredUri = when {
                    storedUri == null -> null
                    !isMediaStoreUri(storedUri) -> storedUri
                    existingBefore.any { it.uri == storedUri } -> storedUri
                    else -> {
                        android.util.Log.w("SiteboardDB", "Stored MediaStore backup URI is not query-visible anymore, recreating backup file: $storedUri")
                        clearStoredBackupUri(ctx)
                        null
                    }
                }

                var createdNewMediaStoreItem = false
                val targetUri = when {
                    reusableStoredUri != null -> reusableStoredUri
                    existingBefore.isNotEmpty() -> existingBefore.first().uri
                    else -> run {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME)
                            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/x-siteboard-backup")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, BACKUP_RELATIVE_PATH)
                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        createdNewMediaStoreItem = true
                        resolver.insert(collection, values)
                            ?: throw java.io.IOException("MediaStore insert returned null")
                    }
                }

                val targetIsMediaStore = isMediaStoreUri(targetUri)

                val writeMode = if (targetIsMediaStore) "w" else "wt"
                val copied = resolver.openOutputStream(targetUri, writeMode)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: throw java.io.IOException("MediaStore output stream is null")
                if (copied <= 0L) {
                    resolver.delete(targetUri, null, null)
                    throw java.io.IOException("No bytes were written to backup file")
                }
                if (targetIsMediaStore && createdNewMediaStoreItem) {
                    val publishValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(targetUri, publishValues, null, null)
                }

                rememberBackupUri(ctx, targetUri)

                if (targetIsMediaStore) {
                    queryBackupEntries(resolver, collection)
                        .filter { it.uri != targetUri }
                        .forEach { duplicate ->
                            try {
                                resolver.delete(duplicate.uri, null, null)
                                android.util.Log.d("SiteboardDB", "Deleted duplicate backup: ${duplicate.displayName}@${duplicate.uri}")
                            } catch (e: Exception) {
                                android.util.Log.e("SiteboardDB", "Failed deleting duplicate backup ${duplicate.displayName}: ${e.message}")
                            }
                        }

                    val existingAfter = queryBackupEntries(resolver, collection)
                    android.util.Log.d("SiteboardDB", "Backup candidates after save: ${existingAfter.joinToString { "${it.displayName}@${it.uri}" }}")
                    if (existingAfter.isEmpty()) {
                        android.util.Log.w("SiteboardDB", "Backup file was written but MediaStore did not return it on immediate re-query")
                    }
                } else {
                    android.util.Log.d("SiteboardDB", "Backup candidates after save: stored-document-uri@$targetUri")
                }
                android.util.Log.d("SiteboardDB", "Backup saved: $targetUri ($copied bytes)")
            } else {
                val dir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "SITEBOARD"
                )
                dir.mkdirs()
                dir.listFiles { candidate ->
                    candidate.isFile &&
                        LEGACY_BACKUP_FILE_NAMES.any { candidate.name.startsWith(it.substringBeforeLast('.')) }
                }?.forEach { candidate ->
                    if (candidate.name != BACKUP_FILE_NAME) {
                        candidate.delete()
                    }
                }
                    val target = java.io.File(dir, BACKUP_FILE_NAME)
                file.copyTo(target, overwrite = true)
                android.util.Log.d("SiteboardDB", "Backup saved: ${target.absolutePath} (${target.length()} bytes)")
            }
        }

        private fun isMediaStoreUri(uri: android.net.Uri): Boolean =
            uri.authority?.startsWith("media") == true

        private data class BackupEntry(
            val uri: android.net.Uri,
            val displayName: String
        )

        private fun queryBackupEntries(
            resolver: android.content.ContentResolver,
            collection: android.net.Uri
        ): List<BackupEntry> {
            val items = mutableListOf<BackupEntry>()
            resolver.query(
                collection,
                arrayOf(
                    android.provider.MediaStore.Downloads._ID,
                    android.provider.MediaStore.Downloads.DISPLAY_NAME
                ),
                "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${android.provider.MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf(BACKUP_RELATIVE_PATH, BACKUP_FILE_GLOB),
                "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                val idIndex = c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID)
                val nameIndex = c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.DISPLAY_NAME)
                while (c.moveToNext()) {
                    items += BackupEntry(
                        uri = android.content.ContentUris.withAppendedId(collection, c.getLong(idIndex)),
                        displayName = c.getString(nameIndex) ?: ""
                    )
                }
            }
            return items
        }

        // ── Downloads/SITEBOARD/siteboard_backup.db URI 찾기
        fun findDownloadsBackup(context: android.content.Context): android.net.Uri? {
            return try {
                val ctx = context.applicationContext
                val remembered = storedBackupUri(ctx)
                if (remembered != null && canReadUri(ctx, remembered)) {
                    return remembered
                }
                if (remembered != null) {
                    clearStoredBackupUri(ctx)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = ctx.contentResolver
                    val found = resolver.query(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        arrayOf(
                            android.provider.MediaStore.Downloads._ID,
                            android.provider.MediaStore.Downloads.DISPLAY_NAME
                        ),
                        "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ? AND (" +
                            LEGACY_BACKUP_FILE_NAMES.joinToString(" OR ") { "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?" } +
                            ")",
                        arrayOf(BACKUP_RELATIVE_PATH, *LEGACY_BACKUP_FILE_NAMES.toTypedArray()),
                        "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { c ->
                        if (c.moveToFirst()) android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)
                        ) else null
                    } ?: resolver.query(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        arrayOf(android.provider.MediaStore.Files.FileColumns._ID),
                        "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ? AND (" +
                            LEGACY_BACKUP_FILE_NAMES.joinToString(" OR ") { "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?" } +
                            ")",
                        arrayOf(BACKUP_RELATIVE_PATH, *LEGACY_BACKUP_FILE_NAMES.toTypedArray()),
                        "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { c ->
                        if (c.moveToFirst()) android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Files.getContentUri("external"), c.getLong(0)
                        ) else null
                    }
                    found
                } else {
                    val f = java.io.File(
                        java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "SITEBOARD"),
                        BACKUP_FILE_NAME
                    )
                    if (f.exists()) android.net.Uri.fromFile(f) else {
                        LEGACY_BACKUP_FILE_NAMES
                            .asSequence()
                            .map { name ->
                                java.io.File(
                                    java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "SITEBOARD"),
                                    name
                                )
                            }
                            .firstOrNull { it.exists() }
                            ?.let { android.net.Uri.fromFile(it) }
                    }
                }
            } catch (e: Exception) { null }
        }

        // ── Downloads 백업에서 DB 복원
        fun restoreFromBackup(context: android.content.Context, backupUri: android.net.Uri): Boolean {
            val ctx = context.applicationContext
            return try {
                INSTANCE?.close(); INSTANCE = null
                val dbFile = primaryDbFile(ctx)
                dbFile.parentFile?.mkdirs()
                val tmpFile = java.io.File(ctx.cacheDir, "siteboard_restore_tmp.db")
                val copiedBytes = ctx.contentResolver.openInputStream(backupUri)?.use { input ->
                    tmpFile.outputStream().use { out -> input.copyTo(out) }
                } ?: -1L
                if (copiedBytes <= 0L || !tmpFile.exists() || tmpFile.length() <= 0L) {
                    tmpFile.delete()
                    return false
                }
                rememberBackupUri(ctx, backupUri)
                if (dbFile.exists()) dbFile.delete()
                tmpFile.copyTo(dbFile, overwrite = true)
                tmpFile.delete()
                listOf("siteboard.db-wal", "siteboard.db-shm", "siteboard.db-journal").forEach {
                    java.io.File(dbFile.parent!!, it).delete()
                }
                INSTANCE = buildDatabase(ctx)
                restorePreferencesFromDb(ctx)
                repairPreferences(ctx)
                true
            } catch (e: Exception) {
                android.util.Log.e("SiteboardDB", "Restore failed: ${e.message}"); false
            }
        }

        fun ensureBackupExists(context: android.content.Context) {
            val ctx = context.applicationContext
            try {
                if (!primaryDbFile(ctx).exists()) return
                if (findDownloadsBackup(ctx) != null) return
                val db = getDatabase(ctx)
                if (db.postDao().countPosts() > 0) {
                    backupToDownloads(ctx)
                }
            } catch (e: Exception) {
                android.util.Log.e("SiteboardDB", "Ensure backup failed: ${e.message}")
            }
        }

        // ── 이번이 첫 설치(또는 재설치)인지 확인 — DB 파일 자체가 없으면 true
        fun isFirstInstall(context: android.content.Context): Boolean =
            !primaryDbFile(context.applicationContext).exists()
    }
}
