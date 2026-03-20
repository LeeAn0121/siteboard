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
}

@Database(entities = [PostEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "siteboard.db").fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}