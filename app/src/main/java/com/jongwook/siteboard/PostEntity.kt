package com.jongwook.siteboard

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
        // SharedPreferences에서 Float로 저장되는 키 목록
        private val PREF_NAMES = listOf("SiteboardPrefs", "WatermarkPrefs")
        private val floatPrefKeys = setOf("wm_font_size")

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also {
                    INSTANCE = it
                    repairPreferences(context.applicationContext)
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
                    val value = prefs.all[key]
                    if (value is Int) {
                        editor.putFloat(key, value.toFloat())
                        changed = true
                    }
                }
                if (changed) editor.apply()
            }
        }

        // Primary DB 경로: Android/data/.../files/ (앱 전용 외부 저장소)
        private fun primaryDbFile(context: android.content.Context): java.io.File {
            val dir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
            return java.io.File(dir, "siteboard.db")
        }

        private fun buildDatabase(context: android.content.Context): AppDatabase {
            val ctx = context.applicationContext
            val primary = primaryDbFile(ctx)
            return Room.databaseBuilder(ctx, AppDatabase::class.java, primary.absolutePath)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
