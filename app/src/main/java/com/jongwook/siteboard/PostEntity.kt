package com.jongwook.siteboard

import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.room.*
import androidx.room.migration.Migration

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
            }

            return Room.databaseBuilder(ctx, AppDatabase::class.java, primary.absolutePath)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }

        // WAL checkpoint: PRAGMA는 결과를 반환하므로 execSQL 대신 query 사용
        private fun walCheckpoint() {
            try {
                INSTANCE?.openHelper?.writableDatabase
                    ?.query("PRAGMA wal_checkpoint(FULL)", emptyArray<Any?>())?.close()
            } catch (e: Exception) { /* ignore */ }
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

        // DB를 사용자 지정 URI로 내보내기 (SAF — Google Drive·OneDrive 지원)
        fun exportToUri(context: android.content.Context, targetUri: android.net.Uri): Boolean {
            return try {
                val primary = primaryDbFile(context.applicationContext)
                if (!primary.exists()) return false
                walCheckpoint()
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    primary.inputStream().use { input -> input.copyTo(out) }
                }
                true
            } catch (e: Exception) { e.printStackTrace(); false }
        }

        // 사용자 지정 DB 파일을 불러와 현재 DB 교체 (SAF)
        fun importFromUri(context: android.content.Context, sourceUri: android.net.Uri): Boolean {
            return try {
                val primary = primaryDbFile(context.applicationContext)
                INSTANCE?.close()
                INSTANCE = null
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    primary.outputStream().use { out -> input.copyTo(out) }
                }
                true
            } catch (e: Exception) { e.printStackTrace(); false }
        }
    }
}
