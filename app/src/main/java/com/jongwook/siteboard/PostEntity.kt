package com.jongwook.siteboard

import androidx.room.*

@Entity(tableName = "site_posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val location: String?,
    val imageUri: String,
    val date: String
)

@Dao
interface PostDao {
    @Query("SELECT * FROM site_posts ORDER BY id DESC")
    fun getAllPosts(): kotlinx.coroutines.flow.Flow<List<PostEntity>>

    @Insert
    fun insert(post: PostEntity) // suspend 제거, 반환 타입 제거

    @Update
    fun update(post: PostEntity) // suspend 제거, 반환 타입 제거

    @Delete
    fun delete(post: PostEntity) // suspend 제거, 반환 타입 제거

    @Query("DELETE FROM site_posts")
    fun deleteAll() // 💡 모든 데이터를 한 방에 날리는 쿼리

    @Delete
    fun deleteList(posts: List<PostEntity>) // 💡 일괄 삭제용 함수 추가!
}

@Database(entities = [PostEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        // Primary DB 경로: Android/data/.../files/ (파일 관리자에서 접근 어려움)
        private fun primaryDbFile(context: android.content.Context): java.io.File {
            val dir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
            return java.io.File(dir, "siteboard.db")
        }

        // Backup 경로: Downloads/SITEBOARD/.siteboard_db (숨김파일, 재설치 후에도 유지)
        private fun backupDbFile(): java.io.File? {
            return try {
                val dir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "SITEBOARD"
                )
                if (!dir.exists()) dir.mkdirs()
                java.io.File(dir, ".siteboard_db")
            } catch (e: Exception) { null }
        }

        private fun buildDatabase(context: android.content.Context): AppDatabase {
            val ctx = context.applicationContext
            val primary = primaryDbFile(ctx)
            val backup = backupDbFile()

            if (!primary.exists()) {
                // 1순위: 재설치 후 복구 — backup에서 복원
                if (backup?.exists() == true) {
                    try { backup.copyTo(primary, overwrite = true) } catch (e: Exception) { e.printStackTrace() }
                } else {
                    // 2순위: 구버전 앱 업데이트 — 기존 내부 DB(databases/siteboard.db)에서 이전
                    val legacyDb = ctx.getDatabasePath("siteboard.db")
                    if (legacyDb.exists()) {
                        try { legacyDb.copyTo(primary, overwrite = false) } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }

            return Room.databaseBuilder(ctx, AppDatabase::class.java, primary.absolutePath)
                .fallbackToDestructiveMigration()
                .build()
        }

        // MainActivity.onStop() 에서 호출 — primary → backup 백업
        fun backup(context: android.content.Context) {
            try {
                val primary = primaryDbFile(context.applicationContext)
                val backup = backupDbFile() ?: return
                if (!primary.exists()) return
                // WAL 체크포인트: 미반영 트랜잭션을 main DB 파일에 병합 후 복사
                INSTANCE?.openHelper?.writableDatabase?.execSQL("PRAGMA wal_checkpoint(FULL)")
                primary.copyTo(backup, overwrite = true)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}