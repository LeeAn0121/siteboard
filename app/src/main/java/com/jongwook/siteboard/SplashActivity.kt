package com.jongwook.siteboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.jvm.java

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 💡 스플래시 레이아웃 설정 (아까 그 주황색 화면)
        setContentView(R.layout.activity_splash)

        // 1.5초 동안 로고 보여주면서 권한 체크 진행
        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionsAndNavigate()
        }, 1500)
    }

    private fun checkPermissionsAndNavigate() {
        // 필수 권한들 리스트
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES // Android 13+ 기준
        )

        // 모든 권한이 허용되었는지 확인
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            // ✅ 권한이 이미 있다면? -> 바로 메인으로!
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // ❌ 권한이 하나라도 없다면? -> 권한 안내 화면으로!
            startActivity(Intent(this, PermissionActivity::class.java))
        }
        finish() // 스플래시는 이제 안녕!
    }
}