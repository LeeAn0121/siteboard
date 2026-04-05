package com.jongwook.siteboard

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: 콘텐츠가 시스템바 뒤까지 확장, 각 화면이 직접 insets 처리
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SiteboardWidgetManager.refreshAll(applicationContext)

        // 하단 네비게이션 바 패딩 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBar.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.ensureBackupExists(applicationContext)
        }

        // 앱이 켜지면 기본으로 홈 프래그먼트를 보여줌
        if (savedInstanceState == null) {
            openTabFromIntent(intent)
        }
        binding.bottomNavigationView.selectedItemId = menuIdForTab(intent.getStringExtra(EXTRA_OPEN_TAB))

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

    override fun onStop() {
        super.onStop()
        // 앱이 백그라운드로 전환될 때마다 Downloads에 로컬 백업 저장
        Thread { AppDatabase.backupToDownloads(applicationContext) }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTabFromIntent(intent)
    }

    // 프래그먼트를 교체하는 공통 함수
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, fragment)
            .commit()
    }

    private fun openTabFromIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_OPEN_TAB)
        replaceFragment(fragmentForTab(tab))
        binding.bottomNavigationView.selectedItemId = menuIdForTab(tab)
    }

    private fun fragmentForTab(tab: String?): Fragment {
        return when (tab) {
            TAB_ARCHIVE -> ArchiveFragment.newInstance(intent?.getStringExtra(EXTRA_ARCHIVE_FILTER))
            TAB_SETTINGS -> SettingsFragment()
            else -> HomeFragment()
        }
    }

    private fun menuIdForTab(tab: String?): Int {
        return when (tab) {
            TAB_ARCHIVE -> R.id.nav_archive
            TAB_SETTINGS -> R.id.nav_settings
            else -> R.id.nav_home
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_ARCHIVE_FILTER = "archive_filter"
        const val TAB_HOME = "home"
        const val TAB_ARCHIVE = "archive"
        const val TAB_SETTINGS = "settings"
    }
}
