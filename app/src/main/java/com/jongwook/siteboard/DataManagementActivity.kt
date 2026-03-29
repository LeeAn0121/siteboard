package com.jongwook.siteboard

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivityDataManagementBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDataManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnExportCsv.setOnClickListener { exportDataToCsv() }
    }

    private fun exportDataToCsv() {
        Toast.makeText(this, "데이터 추출 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DataExportManager.exportPostsToCsv(this@DataManagementActivity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DataManagementActivity, "CSV(엑셀) 데이터가 저장되었습니다.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DataManagementActivity, "CSV 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
