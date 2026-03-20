package com.jongwook.siteboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.jongwook.siteboard.databinding.ActivityMainBinding
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        // 💡 앱 시작 시 클리닝 작업 시작 (백그라운드 실행)
        lifecycleScope.launch(Dispatchers.IO) {
            handleInitialCleanUp()
            syncDatabaseWithGallery()
        }

        // 앱이 켜지면 기본으로 홈 프래그먼트를 보여줌
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        // 하단 탭 바 터치 이벤트 처리
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> replaceFragment(HomeFragment())
                 R.id.nav_archive -> replaceFragment(ArchiveFragment()) // 나중에 추가할 보관함
                 R.id.nav_settings -> replaceFragment(SettingsFragment()) // 나중에 추가할 설정
            }
            true
        }
    }

    // 🔥 [미션 1] 최초 설치 시 DB 전체 삭제
    private suspend fun handleInitialCleanUp() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        if (isFirstRun) {
            db.postDao().deleteAll() // DB 싹 비우기
            prefs.edit().putBoolean("is_first_run", false).apply() // 다시는 실행 안 되게 저장
        }
    }

    // 🔥 [미션 2] 실제 앨범에 사진이 없는 DB 데이터 삭제
    private suspend fun syncDatabaseWithGallery() {
        // 💡 .first()를 붙이면 Flow 파이프에서 '현재 리스트'만 딱 한 번 꺼내옵니다.
        // 이제 allPosts는 Flow가 아니라 진짜 List<PostEntity>가 됩니다!
        val allPosts = db.postDao().getAllPosts().first()

        val ghostPosts = mutableListOf<PostEntity>()

        for (post in allPosts) { // 🚀 이제 여기서 에러가 안 납니다!
            if (!isImageFileExists(post.imageUri)) {
                ghostPosts.add(post)
            }
        }

        // 유령 데이터 삭제 로직 (이하 동일)
        if (ghostPosts.isNotEmpty()) {
            db.postDao().deleteList(ghostPosts)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "앨범에 없는 유령 데이터 ${ghostPosts.size}개를 정리했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 💡 실제 파일이 존재하는지 체크하는 함수
    private fun isImageFileExists(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            // contentResolver를 통해 파일에 접근 가능한지 확인
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.close()
            true // 접근 성공 = 파일 있음
        } catch (e: Exception) {
            false // 접근 실패 = 파일 없음 (삭제됨)
        }
    }

    // 프래그먼트를 교체하는 공통 함수
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, fragment)
            .commit()
    }
}