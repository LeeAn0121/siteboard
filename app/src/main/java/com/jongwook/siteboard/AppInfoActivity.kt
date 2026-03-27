package com.jongwook.siteboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityAppInfoBinding

class AppInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvAppVersion.text = BuildConfig.VERSION_NAME_FULL
    }
}
