package com.jongwook.siteboard

import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivityDataManagementBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDataManagementBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        db = AppDatabase.getDatabase(this)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnExportCsv.setOnClickListener { exportDataToCsv() }
        binding.btnSettingsBackupMenu.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsBackupActivity::class.java))
        }
    }

    private fun exportDataToCsv() {
        Toast.makeText(this, "데이터 추출 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val postList = db.postDao().getAllPosts().first()
                val csvData = StringBuilder()
                csvData.append("ID,현장명,작업내용,위치(GPS),상세위치,메모,날짜,원본파일명\n")
                for (post in postList) {
                    val title = post.title.replace(",", " ")
                    val desc = post.description.replace(",", " ").replace("\n", " ")
                    val loc = post.location?.replace(",", " ") ?: ""
                    val detailLoc = post.detailLocation?.replace(",", " ") ?: ""
                    val memo = post.memo?.replace(",", " ") ?: ""
                    val origName = post.originalFileName ?: ""
                    csvData.append("${post.id},$title,$desc,$loc,$detailLoc,$memo,${post.date},$origName\n")
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val siteboardDir = File(baseDir, "SITEBOARD")
                if (!siteboardDir.exists()) siteboardDir.mkdirs()
                val file = File(siteboardDir, "Siteboard_Data_$timeStamp.csv")
                FileWriter(file).use { it.write(csvData.toString()) }

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
