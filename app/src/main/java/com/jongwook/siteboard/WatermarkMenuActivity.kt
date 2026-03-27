package com.jongwook.siteboard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityWatermarkMenuBinding

class WatermarkMenuActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWatermarkMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatermarkMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnWatermarkDetail.setOnClickListener {
            startActivity(Intent(this, WatermarkSettingsActivity::class.java))
        }
    }
}
