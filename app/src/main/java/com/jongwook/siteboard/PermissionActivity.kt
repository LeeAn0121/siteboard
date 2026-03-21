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

    // 💡 1. 필수 권한 리스트 (버전별 분리)
    private val mandatoryPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // 💡 2. 선택 권한 리스트 (위치)
    private val optionalPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // 요청할 전체 권한 합치기
    private val allPermissionsToRequest = mandatoryPermissions + optionalPermissions

    // 💡 3. 권한 요청 결과 처리
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 필수 권한들이 모두 허용되었는지 확인! (선택 권한은 무시)
        val isMandatoryGranted = mandatoryPermissions.all { permissions[it] == true }

        if (isMandatoryGranted) {
            // 위치 권한이 거부되었는지 살짝 체크해서 안내 (옵션)
            val isLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (!isLocationGranted) {
                Toast.makeText(this, "위치 권한 없이 시작합니다. (주소 자동입력 불가)", Toast.LENGTH_SHORT).show()
            }
            // ✅ 필수 권한 통과: 메인 화면으로 이동
            startMainActivity()
        } else {
            // ❌ 필수 권한 실패: 사용자에게 알림
            Toast.makeText(this, "카메라와 저장공간 권한(필수)을 허용해야 앱을 사용할 수 있습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAgree.setOnClickListener {
            if (checkMandatoryPermissions()) {
                // 이미 필수 권한이 다 있다면 바로 메인으로
                startMainActivity()
            } else {
                // 권한 요청 팝업 띄우기 (전체 권한 다 물어봄)
                requestPermissionLauncher.launch(allPermissionsToRequest)
            }
        }
    }

    // 💡 필수 권한만 체크하는 함수 (위치는 없어도 넘어감)
    private fun checkMandatoryPermissions(): Boolean {
        return mandatoryPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}