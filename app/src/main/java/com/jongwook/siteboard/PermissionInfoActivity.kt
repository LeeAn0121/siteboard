package com.jongwook.siteboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityPermissionInfoBinding

class PermissionInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnOpenSystemPermission.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }

        refreshPermissionState()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        updatePermissionRow(
            isGranted = isGranted(Manifest.permission.CAMERA),
            titleView = binding.tvCameraPermissionState,
            detailView = binding.tvCameraPermissionDetail,
            grantedText = "촬영과 편집에 사용 가능",
            deniedText = "사진 촬영을 하려면 허용이 필요합니다"
        )

        val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        updatePermissionRow(
            isGranted = mediaGranted,
            titleView = binding.tvPhotoPermissionState,
            detailView = binding.tvPhotoPermissionDetail,
            grantedText = "사진 불러오기와 PDF 작업에 사용 가능",
            deniedText = "기존 사진을 불러오려면 허용이 필요합니다"
        )

        val locationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        updatePermissionRow(
            isGranted = locationGranted,
            titleView = binding.tvLocationPermissionState,
            detailView = binding.tvLocationPermissionDetail,
            grantedText = "워터마크 위치 정보 기능 사용 가능",
            deniedText = "위치 자동 입력 기능은 사용할 수 없습니다"
        )
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun updatePermissionRow(
        isGranted: Boolean,
        titleView: android.widget.TextView,
        detailView: android.widget.TextView,
        grantedText: String,
        deniedText: String
    ) {
        titleView.text = if (isGranted) "허용됨" else "허용 안 됨"
        titleView.setTextColor(
            if (isGranted) getColor(R.color.mint_accent) else getColor(R.color.danger_accent)
        )
        detailView.text = if (isGranted) grantedText else deniedText
    }
}
