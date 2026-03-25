package com.jongwook.siteboard

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.first

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

@Dao
interface PostDao {
    @Query("SELECT * FROM site_posts ORDER BY id DESC")
    fun getAllPosts(): kotlinx.coroutines.flow.Flow<List<PostEntity>>

    @Query("SELECT * FROM site_posts WHERE id = :id LIMIT 1")
    fun getById(id: Int): PostEntity?

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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE site_posts ADD COLUMN detailLocation TEXT")
        database.execSQL("ALTER TABLE site_posts ADD COLUMN memo TEXT")
        database.execSQL("ALTER TABLE site_posts ADD COLUMN originalUri TEXT")
        database.execSQL("ALTER TABLE site_posts ADD COLUMN originalFileName TEXT")
    }
}

@Database(entities = [PostEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    companion object {
        private const val BACKUP_FILE_NAME = "siteboard_backup.db"
        private const val BACKUP_RELATIVE_PATH = "Download/SITEBOARD/"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        // Primary DB 경로: Android/data/.../files/ (앱 전용 외부 저장소)
        private fun primaryDbFile(context: android.content.Context): java.io.File {
            val dir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
            return java.io.File(dir, "siteboard.db")
        }

        // 레거시 백업 경로: Downloads/SITEBOARD/ (API 28 이하, requestLegacyExternalStorage)
        private fun legacyBackupFile(): java.io.File? {
            return try {
                val dir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "SITEBOARD"
                )
                if (!dir.exists()) dir.mkdirs()
                java.io.File(dir, BACKUP_FILE_NAME)
            } catch (e: Exception) { null }
        }

        // API 29+: MediaStore.Downloads로 백업 저장
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun backupViaMediaStore(context: android.content.Context, dbFile: java.io.File): Boolean {
            return try {
                val contentResolver = context.contentResolver
                val externalUri = MediaStore.Downloads.getContentUri("external")

                // 기존 백업 항목 삭제
                val existing = contentResolver.query(
                    externalUri,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                    arrayOf(BACKUP_FILE_NAME, "%SITEBOARD%"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    } else null
                }
                if (existing != null) {
                    contentResolver.delete(ContentUris.withAppendedId(externalUri, existing), null, null)
                }

                // 새 백업 생성
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, BACKUP_RELATIVE_PATH)
                }
                val newUri = contentResolver.insert(externalUri, values) ?: return false
                contentResolver.openOutputStream(newUri)?.use { out ->
                    dbFile.inputStream().use { input -> input.copyTo(out) }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        // API 29+: MediaStore.Downloads에서 백업 복원
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun restoreViaMediaStore(context: android.content.Context, destFile: java.io.File): Boolean {
            return try {
                val contentResolver = context.contentResolver
                val externalUri = MediaStore.Downloads.getContentUri("external")

                val id = contentResolver.query(
                    externalUri,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                    arrayOf(BACKUP_FILE_NAME, "%SITEBOARD%"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    else null
                } ?: return false

                val uri = ContentUris.withAppendedId(externalUri, id)
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { out -> input.copyTo(out) }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        private fun buildDatabase(context: android.content.Context): AppDatabase {
            val ctx = context.applicationContext
            val primary = primaryDbFile(ctx)

            if (!primary.exists()) {
                var restored = false

                // 1순위: API 29+ — MediaStore.Downloads 에서 복원
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    restored = restoreViaMediaStore(ctx, primary)
                }

                if (!restored) {
                    // 2순위: 레거시 파일 백업에서 복원 (API 28-, requestLegacyExternalStorage)
                    val legacy = legacyBackupFile()
                    if (legacy?.exists() == true) {
                        try { legacy.copyTo(primary, overwrite = true); restored = true }
                        catch (e: Exception) { e.printStackTrace() }
                    }
                }

                if (!restored) {
                    // 3순위: 구버전 앱의 내부 DB에서 이전
                    val legacyInternalDb = ctx.getDatabasePath("siteboard.db")
                    if (legacyInternalDb.exists()) {
                        try { legacyInternalDb.copyTo(primary, overwrite = false) }
                        catch (e: Exception) { e.printStackTrace() }
                    }
                }

                // 복원 직후임을 표시 → syncDatabaseWithGallery() 건너뜀
                if (restored) {
                    ctx.getSharedPreferences("SiteboardPrefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("db_just_restored", true).apply()
                }
            }

            return Room.databaseBuilder(ctx, AppDatabase::class.java, primary.absolutePath)
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        // WAL checkpoint: PRAGMA는 결과를 반환하므로 execSQL 대신 query 사용
        private fun walCheckpoint() {
            try {
                INSTANCE?.openHelper?.writableDatabase
                    ?.query("PRAGMA wal_checkpoint(FULL)", emptyArray<Any?>())?.close()
            } catch (e: Exception) { /* ignore */ }
        }

        private fun guessMimeType(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
        }

        private fun restorePhotoToMediaStore(
            context: android.content.Context,
            photoFile: java.io.File,
            relativePath: String
        ): Uri? {
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, guessMimeType(photoFile.name))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            }

            val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

            return try {
                val written = contentResolver.openOutputStream(newUri)?.use { out ->
                    photoFile.inputStream().use { input -> input.copyTo(out) }
                    true
                } ?: false

                if (!written) {
                    contentResolver.delete(newUri, null, null)
                    null
                } else {
                    newUri
                }
            } catch (e: Exception) {
                try { contentResolver.delete(newUri, null, null) } catch (_: Exception) { }
                throw e
            }
        }

        // MainActivity.onStop() 에서 호출 — 자동 백업 (API별 분기)
        fun backup(context: android.content.Context) {
            try {
                val primary = primaryDbFile(context.applicationContext)
                if (!primary.exists()) return
                walCheckpoint()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+: MediaStore 백업 (재설치 후 복구 가능)
                    backupViaMediaStore(context.applicationContext, primary)
                } else {
                    // API 28-: 파일 직접 복사 (requestLegacyExternalStorage 활용)
                    val legacy = legacyBackupFile() ?: return
                    primary.copyTo(legacy, overwrite = true)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // ─── ZIP 내보내기 (DB + 사진 전체) ────────────────────────────────
        suspend fun exportToZip(context: android.content.Context, targetUri: android.net.Uri): Int? {
            return try {
                val ctx = context.applicationContext
                val primary = primaryDbFile(ctx)
                if (!primary.exists()) return null

                val db = getDatabase(ctx)
                walCheckpoint()
                val posts = db.postDao().getAllPosts().first()

                val tmpZip = java.io.File(ctx.cacheDir, "siteboard_export_tmp.zip")
                java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(tmpZip.outputStream())).use { zip ->
                    // 1. DB 파일
                    zip.putNextEntry(java.util.zip.ZipEntry("siteboard.db"))
                    primary.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()

                    // 2. 사진 파일 (워터마크본 + 원본)
                    for (post in posts) {
                        try {
                            ctx.contentResolver.openInputStream(android.net.Uri.parse(post.imageUri))?.use { input ->
                                zip.putNextEntry(java.util.zip.ZipEntry("photos/${post.id}_w.jpg"))
                                input.copyTo(zip)
                                zip.closeEntry()
                            }
                        } catch (e: Exception) { e.printStackTrace() }

                        if (!post.originalUri.isNullOrEmpty()) {
                            try {
                                ctx.contentResolver.openInputStream(android.net.Uri.parse(post.originalUri))?.use { input ->
                                    zip.putNextEntry(java.util.zip.ZipEntry("photos/${post.id}_o.jpg"))
                                    input.copyTo(zip)
                                    zip.closeEntry()
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }

                ctx.contentResolver.openOutputStream(targetUri)?.use { out ->
                    tmpZip.inputStream().use { it.copyTo(out) }
                }
                tmpZip.delete()
                posts.size
            } catch (e: Exception) { e.printStackTrace(); null }
        }

        // ─── ZIP 불러오기 (DB + 사진 전체 복원) ──────────────────────────
        fun importFromZip(context: android.content.Context, sourceUri: android.net.Uri): Int? {
            return try {
                val ctx = context.applicationContext
                val tmpDir = java.io.File(ctx.cacheDir, "siteboard_import_tmp")
                if (tmpDir.exists()) tmpDir.deleteRecursively()
                tmpDir.mkdirs()

                // 1-a. ZIP을 먼저 로컬 캐시로 복사 (클라우드/스트림 URI 호환성 보장)
                val localZip = java.io.File(ctx.cacheDir, "siteboard_import.zip")
                localZip.delete()
                ctx.contentResolver.openInputStream(sourceUri)?.use { input ->
                    localZip.outputStream().buffered().use { out -> input.copyTo(out) }
                } ?: run { tmpDir.deleteRecursively(); return null }

                // 1-b. ZipFile로 압축 해제 (ZipInputStream보다 안정적, 엔트리별 오류 처리 가능)
                try {
                    java.util.zip.ZipFile(localZip).use { zipFile ->
                        zipFile.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory) {
                                val entryPath = entry.name.replace('\\', '/')
                                val outFile = java.io.File(tmpDir, entryPath)
                                outFile.parentFile?.mkdirs()
                                zipFile.getInputStream(entry).use { input ->
                                    outFile.outputStream().buffered().use { out -> input.copyTo(out) }
                                }
                            }
                        }
                    }
                } finally {
                    localZip.delete()
                }

                val extractedDb = java.io.File(tmpDir, "siteboard.db")
                if (!extractedDb.exists()) { tmpDir.deleteRecursively(); return null }

                // 2. 사진을 갤러리에 재저장하고 새 URI 수집
                val photosDir = java.io.File(tmpDir, "photos")
                val uriMap = mutableMapOf<String, String>() // "42_w" → new content:// URI
                val zipHasOriginalFor = mutableSetOf<Int>() // zip에 _o 파일이 있던 post ID
                var expectedDisplayPhotoCount = 0
                var restoredDisplayPhotoCount = 0
                if (photosDir.exists()) {
                    for (photoFile in (photosDir.listFiles() ?: emptyArray()).sortedBy { it.name }) {
                        try {
                            val baseName = photoFile.nameWithoutExtension
                            val lastUnder = baseName.lastIndexOf('_')
                            if (lastUnder < 0) continue
                            val idStr = baseName.substring(0, lastUnder)
                            val type = baseName.substring(lastUnder + 1)
                            val photoId = idStr.toIntOrNull() ?: continue

                            if (type == "w") expectedDisplayPhotoCount++
                            if (type == "o") zipHasOriginalFor.add(photoId)

                            val relativePath = if (type == "o") "Pictures/SITEBOARD_ORIGINALS/" else "Pictures/SITEBOARD/"
                            val newUri = restorePhotoToMediaStore(ctx, photoFile, relativePath)
                            if (newUri != null) {
                                uriMap[baseName] = newUri.toString()
                                if (type == "w") restoredDisplayPhotoCount++
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                android.util.Log.d("SiteboardImport",
                    "photos: expected=$expectedDisplayPhotoCount, restored=$restoredDisplayPhotoCount, uriMap=${uriMap.size}")

                // 사진이 있는 ZIP인데 하나도 복원 못 한 경우만 실패 처리
                if (expectedDisplayPhotoCount > 0 && restoredDisplayPhotoCount == 0) {
                    tmpDir.deleteRecursively()
                    return null
                }

                // 3. 추출된 DB의 URI를 새 URI로 교체 (직접 SQLite)
                //    PRAGMA journal_mode=DELETE → WAL 대신 동기 쓰기, 복사 전 누락 방지
                val sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                    extractedDb.absolutePath, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                )
                var importedPostCount = 0
                try {
                    sqliteDb.execSQL("PRAGMA journal_mode=DELETE")
                    sqliteDb.rawQuery("SELECT COUNT(*) FROM site_posts", null).use { c ->
                        if (c.moveToFirst()) importedPostCount = c.getInt(0)
                    }
                    if (uriMap.isNotEmpty()) {
                        sqliteDb.rawQuery("SELECT id FROM site_posts", null).use { c ->
                            while (c.moveToNext()) {
                                val id = c.getInt(0)
                                // 복원된 URI가 있을 때만 업데이트 (없으면 zip DB의 원본 URI 유지)
                                val newImage = uriMap["${id}_w"]
                                if (newImage != null) {
                                    sqliteDb.execSQL(
                                        "UPDATE site_posts SET imageUri = ? WHERE id = ?",
                                        arrayOf(newImage, id)
                                    )
                                }
                                val newOriginal = uriMap["${id}_o"]
                                if (newOriginal != null) {
                                    sqliteDb.execSQL(
                                        "UPDATE site_posts SET originalUri = ? WHERE id = ?",
                                        arrayOf(newOriginal, id)
                                    )
                                } else if (!zipHasOriginalFor.contains(id)) {
                                    // zip에 원본이 없었던 레코드만 null 처리
                                    sqliteDb.execSQL(
                                        "UPDATE site_posts SET originalUri = NULL WHERE id = ?",
                                        arrayOf(id)
                                    )
                                }
                            }
                        }
                    }
                } finally { sqliteDb.close() }

                // 4. Room DB 교체
                INSTANCE?.close()
                INSTANCE = null
                extractedDb.copyTo(primaryDbFile(ctx), overwrite = true)

                ctx.getSharedPreferences("SiteboardPrefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("db_just_restored", true).apply()

                tmpDir.deleteRecursively()
                importedPostCount
            } catch (e: Exception) { e.printStackTrace(); null }
        }
    }
}
