package com.jongwook.siteboard

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityCapturePrivacySettingsBinding

class CapturePrivacySettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCapturePrivacySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCapturePrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        val prefs = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
        binding.switchPdfMode.isChecked = prefs.getBoolean("pdf_multi_mode", false)
        binding.switchPrivacyBlur.isChecked = prefs.getBoolean("privacy_blur_mode", false)
        binding.switchGpsEnabled.isChecked = prefs.getBoolean("gps_enabled", false)

        binding.switchPdfMode.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("pdf_multi_mode", checked).apply()
        }
        binding.switchPrivacyBlur.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("privacy_blur_mode", checked).apply()
        }
        binding.switchGpsEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("gps_enabled", checked).apply()
        }
    }
}
