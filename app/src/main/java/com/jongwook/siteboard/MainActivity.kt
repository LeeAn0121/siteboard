package com.jongwook.siteboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.jongwook.siteboard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    // 프래그먼트를 교체하는 공통 함수
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, fragment)
            .commit()
    }
}