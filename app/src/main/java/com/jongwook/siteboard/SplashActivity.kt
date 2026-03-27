package com.jongwook.siteboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val isFirstInstall = AppDatabase.isFirstInstall(applicationContext)
            // 1.5초 대기와 백업 확인을 동시에 실행
            val delayJob = async(Dispatchers.Default) { kotlinx.coroutines.delay(1500) }
            val backupJob = async(Dispatchers.IO) {
                if (isFirstInstall)
                    AppDatabase.findDownloadsBackup(applicationContext)
                else null
            }
            delayJob.await()
            val backupUri = backupJob.await()

            if (isFinishing) return@launch

            if (backupUri != null) {
                restoreBackupAndNavigate(backupUri)
            } else {
                if (isFirstInstall) {
                    Toast.makeText(
                        this@SplashActivity,
                        "복원 가능한 백업 파일을 찾지 못했습니다. 새로 시작합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                checkPermissionsAndNavigate()
            }
        }
    }

    private fun restoreBackupAndNavigate(backupUri: Uri) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                AppDatabase.restoreFromBackup(applicationContext, backupUri)
            }
            Toast.makeText(
                this@SplashActivity,
                if (success) "이전 기록 데이터를 자동으로 복원했습니다." else "복원에 실패했습니다. 새로 시작합니다.",
                Toast.LENGTH_SHORT
            ).show()
            checkPermissionsAndNavigate()
        }
    }

    private fun checkPermissionsAndNavigate() {
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        startActivity(Intent(this, if (allGranted) MainActivity::class.java else PermissionActivity::class.java))
        finish()
    }
}
