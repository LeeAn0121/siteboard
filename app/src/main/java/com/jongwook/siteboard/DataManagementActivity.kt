package com.jongwook.siteboard

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.jongwook.siteboard.databinding.ActivityDataManagementBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDataManagementBinding
    private lateinit var db: AppDatabase

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        importFromUris(uris)
    }

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
        binding.btnImportFromGallery.setOnClickListener { pickImagesLauncher.launch(arrayOf("image/*")) }
        binding.btnExportCsv.setOnClickListener { exportDataToCsv() }
        binding.btnSettingsBackupMenu.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsBackupActivity::class.java))
        }
    }

    private fun importFromUris(uris: List<Uri>) {
        Toast.makeText(this, "${uris.size}장 분석 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cr = applicationContext.contentResolver
                val existingUris = db.postDao().getAllPosts().first().map { it.imageUri }.toSet()
                val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                var importedCount = 0
                var skippedCount = 0
                var failedCount = 0

                for (uri in uris) {
                    if (uri.toString() in existingUris) {
                        skippedCount++
                        continue
                    }

                    try {
                        try {
                            cr.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {
                        }

                        val bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri)) ?: run {
                            failedCount++
                            continue
                        }
                        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                        bitmap.recycle()

                        val allLines = result.textBlocks.flatMap { it.lines }.map { it.text.trim() }
                        val parsed = parseWatermarkLines(allLines)
                        val date = parsed["날짜"]?.ifEmpty { null }
                            ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                        db.postDao().insert(
                            PostEntity(
                                title = parsed["제목"] ?: "",
                                description = parsed["작업내용"] ?: "",
                                location = parsed["위치"]?.ifEmpty { null },
                                imageUri = uri.toString(),
                                date = date,
                                detailLocation = parsed["상세위치"]?.ifEmpty { null },
                                memo = parsed["메모"]?.ifEmpty { null }
                            )
                        )
                        importedCount++
                    } catch (_: Exception) {
                        failedCount++
                    }
                }

                recognizer.close()

                if (importedCount > 0) {
                    AppDatabase.backupNow(applicationContext)
                }

                withContext(Dispatchers.Main) {
                    val parts = mutableListOf("${importedCount}개 가져옴")
                    if (skippedCount > 0) parts.add("${skippedCount}개 이미 등록됨")
                    if (failedCount > 0) parts.add("${failedCount}개 실패")
                    Toast.makeText(this@DataManagementActivity, parts.joinToString(", "), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DataManagementActivity, "가져오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseWatermarkLines(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) continue
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim()
            when {
                key.contains("제목") -> result["제목"] = value
                key.contains("위치(GPS)") || key.contains("위치(gps)") || key == "위치" -> result["위치"] = value
                key.contains("상세위치") -> result["상세위치"] = value
                key.contains("작업내용") -> result["작업내용"] = value
                key.contains("메모") -> result["메모"] = value
                key.contains("날짜") -> result["날짜"] = value
            }
        }
        return result
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
