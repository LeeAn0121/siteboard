package com.jongwook.siteboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jongwook.siteboard.databinding.ActivityPermissionBinding

class PermissionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionBinding

    // 💡 필요한 권한 리스트 (안드로이드 버전에 따라 다르게 세팅)
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 안드로이드 13 이상
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        // 안드로이드 12 이하
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    // 💡 권한 요청 결과 처리를 위한 런처 (ActivityResultLauncher)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 모든 권한이 허용되었는지 확인
        val allGranted = permissions.entries.all { it.value }

        if (allGranted) {
            // ✅ 성공: 메인 화면으로 이동
            startMainActivity()
        } else {
            // ❌ 실패: 사용자에게 알림 (필수 권한임을 강조)
            Toast.makeText(this, "필수 권한을 허용해야 앱을 사용할 수 있습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 💡 [동의하고 시작하기] 버튼 클릭 시
        binding.btnAgree.setOnClickListener {
            if (checkAllPermissions()) {
                // 이미 권한이 다 있다면 바로 메인으로
                startMainActivity()
            } else {
                // 권한 요청 팝업 띄우기
                requestPermissionLauncher.launch(requiredPermissions)
            }
        }
    }

    // 💡 모든 권한이 있는지 체크하는 함수
    private fun checkAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 💡 메인 화면 이동 및 현재 화면 종료
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}