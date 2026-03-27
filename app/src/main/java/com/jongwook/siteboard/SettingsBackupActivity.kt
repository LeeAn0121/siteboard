package com.jongwook.siteboard

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivitySettingsBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsBackupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBackupBinding

    private val exportSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) exportSettingsToUri(uri)
    }

    private val importSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importSettingsFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnExportSettings.setOnClickListener {
            val name = "siteboard_settings_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            exportSettingsLauncher.launch(name)
        }
        binding.btnImportSettings.setOnClickListener {
            importSettingsLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    private fun exportSettingsToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = SettingsBackupManager.buildSettingsBackupJson(this@SettingsBackupActivity)
                contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("출력 스트림을 열 수 없습니다.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정 파일을 저장했습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정 내보내기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importSettingsFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("파일을 읽을 수 없습니다.")
                SettingsBackupManager.applySettingsBackupJson(this@SettingsBackupActivity, raw)
                AppDatabase.backupNow(applicationContext)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정을 불러왔습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
