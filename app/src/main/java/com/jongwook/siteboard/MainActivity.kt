package com.jongwook.siteboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
        // Edge-to-edge: 콘텐츠가 시스템바 뒤까지 확장, 각 화면이 직접 insets 처리
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 하단 네비게이션 바 패딩 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBar.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)

        // 💡 앱 시작 시 클리닝 작업 시작 (백그라운드 실행)
        lifecycleScope.launch(Dispatchers.IO) {
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

    // 🔥 [미션 1] 실제 앨범에 사진이 없는 DB 데이터만 안전하게 삭제
    private suspend fun syncDatabaseWithGallery() {
        try {
            val allPosts = db.postDao().getAllPosts().first()
            val ghostPosts = mutableListOf<PostEntity>()

            for (post in allPosts) {
                // 저장된 경로가 비어있지 않은데, 실제 파일이 없는 경우에만 유령 데이터로 취급!
                if (post.imageUri.isNotEmpty() && !isImageFileExists(post.imageUri)) {
                    ghostPosts.add(post)
                }
            }

            // 진짜 갤러리에서 지워진 '유령 데이터'만 삭제
            if (ghostPosts.isNotEmpty()) {
                db.postDao().deleteList(ghostPosts)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "갤러리에서 삭제된 기록 ${ghostPosts.size}개를 정리했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 동기화 중 에러가 나더라도 앱이 튕기지 않게 방어
        }
    }

    // 💡 실제 파일이 존재하는지 체크하는 함수 (절대경로 & 보안정책 완벽 대응 + 방어 로직)
    private fun isImageFileExists(uriString: String): Boolean {
        if (uriString.isBlank()) return false

        return try {
            if (uriString.startsWith("content://")) {
                // 1. 갤러리(MediaStore) 형태의 URI인 경우: Cursor로 안전하게 존재 여부만 확인
                val uri = Uri.parse(uriString)
                val cursor = contentResolver.query(uri, null, null, null, null)
                val exists = cursor != null && cursor.moveToFirst()
                cursor?.close()
                exists
            } else {
                // 2. 내부 저장소 절대 경로(/storage/...) 또는 file:// 인 경우: File 객체로 확인
                val path = if (uriString.startsWith("file://")) {
                    Uri.parse(uriString).path ?: uriString
                } else {
                    uriString
                }
                val file = java.io.File(path)
                file.exists()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 🚨 [가장 중요한 안전장치] 권한 문제 등으로 파일 확인에 실패(에러)했을 때,
            // false를 반환하면 억울하게 DB가 다 날아가므로 무조건 true(있음)로 반환해서 기록을 보호합니다!
            true
        }
    }

    override fun onStop() {
        super.onStop()
        // 앱이 백그라운드로 이동할 때마다 자동 백업
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.backup(this@MainActivity)
        }
    }

    // 프래그먼트를 교체하는 공통 함수
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, fragment)
            .commit()
    }
}