package com.jongwook.siteboard // ⚠️ 주의: 이 부분은 본인 프로젝트의 실제 패키지명으로 유지하세요!

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 1.5초 대기 후 MainActivity로 이동
        lifecycleScope.launch {
            delay(1500)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish() // 뒤로가기 했을 때 스플래시가 다시 안 나오도록 종료
        }
    }
}