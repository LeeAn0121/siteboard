package com.jongwook.siteboard

import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jongwook.siteboard.databinding.ActivityProjectDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProjectDetailBinding
    private lateinit var postAdapter: PostAdapter
    private lateinit var db: AppDatabase
    private var projectPosts: List<PostEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 시스템 상단바 간격 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        val projectTitle = intent.getStringExtra("PROJECT_TITLE") ?: "알 수 없는 현장"
        binding.tvProjectTitle.text = projectTitle
        binding.btnExportProjectPdf.setOnClickListener {
            if (projectPosts.isEmpty()) {
                Toast.makeText(this, "내보낼 기록이 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                exportProjectPdf(projectTitle, projectPosts)
            }
        }

        // 2. 뒤로가기 버튼 동작
        binding.btnBack.setOnClickListener { finish() }

        // 3. 종욱님의 PostAdapter 재활용! (격자 세팅)
        // 💡 어댑터가 요구하는 필수 파라미터(콜백)를 넣어줍니다.
        postAdapter = PostAdapter { selectedCount ->
            // 폴더 상세 화면에는 아직 '일괄 삭제 상단바 UI'를 만들지 않았으므로,
            // 일단 에러가 나지 않도록 빈 칸(비활성화)으로 둡니다!
            // 나중에 폴더 안에서도 일괄 삭제가 필요해지면 여기에 로직을 넣으면 됩니다.
        }
        binding.rvProjectPosts.layoutManager = GridLayoutManager(this, 2)
        binding.rvProjectPosts.adapter = postAdapter

        // 4. DB에서 해당 프로젝트명과 일치하는 데이터만 걸러서 보여주기
        db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            db.postDao().getAllPosts().collect { allPosts ->
                // 전체 데이터 중 제목이 클릭한 프로젝트명과 똑같은 것만 필터링!
                projectPosts = allPosts.filter { it.title == projectTitle }.sortedByDescending { it.id }
                postAdapter.submitList(projectPosts)

                binding.tvProjectMeta.text = "사진 ${projectPosts.size}장"
                val recentPost = projectPosts.firstOrNull()
                binding.tvProjectSubMeta.text = if (recentPost == null) {
                    "아직 저장된 기록이 없습니다."
                } else {
                    "최근 기록 ${recentPost.date} · ${
                        recentPost.detailLocation?.takeIf { it.isNotBlank() }
                            ?: recentPost.location?.takeIf { it.isNotBlank() }
                            ?: "위치 미입력"
                    }"
                }
            }
        }
    }

    private fun exportProjectPdf(projectTitle: String, posts: List<PostEntity>) {
        Toast.makeText(this, "[$projectTitle] PDF 보고서를 생성 중입니다...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val margin = 50f

                val titlePaint = Paint().apply {
                    textSize = 24f
                    isFakeBoldText = true
                    color = Color.BLACK
                }
                val textPaint = Paint().apply {
                    textSize = 14f
                    color = Color.DKGRAY
                }

                for ((index, post) in posts.withIndex()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawText("SITEBOARD 현장 보고서  |  $projectTitle", margin, margin, textPaint)
                    canvas.drawLine(margin, margin + 10f, pageWidth - margin, margin + 10f, textPaint)
                    canvas.drawText(post.title, margin, margin + 50f, titlePaint)

                    var y = margin + 90f
                    canvas.drawText("• 위치 : ${post.location ?: "미입력"}", margin, y, textPaint)
                    y += 25f
                    canvas.drawText("• 일시 : ${post.date}", margin, y, textPaint)
                    y += 25f
                    canvas.drawText("• 작업 내용 : ${post.description.replace("\n", " ")}", margin, y, textPaint)
                    y += 25f
                    if (!post.detailLocation.isNullOrBlank()) {
                        canvas.drawText("• 상세 위치 : ${post.detailLocation}", margin, y, textPaint)
                        y += 25f
                    }
                    if (!post.memo.isNullOrBlank()) {
                        canvas.drawText("• 메모 : ${post.memo.replace("\n", " ")}", margin, y, textPaint)
                        y += 25f
                    }
                    y += 8f

                    try {
                        val uri = android.net.Uri.parse(post.imageUri)
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, _, _ ->
                                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        }
                        val availableWidth = pageWidth - margin * 2
                        val availableHeight = pageHeight - margin - y
                        val scale = minOf(
                            availableWidth / bitmap.width.toFloat(),
                            availableHeight / bitmap.height.toFloat()
                        )
                        if (scale <= 0f) {
                            canvas.drawText("[이미지 배치 공간 부족]", margin, y, textPaint)
                            pdfDocument.finishPage(page)
                            continue
                        }
                        val targetW = bitmap.width * scale
                        val targetH = bitmap.height * scale
                        val imageLeft = margin + ((availableWidth - targetW) / 2f)
                        canvas.drawBitmap(
                            bitmap,
                            null,
                            RectF(imageLeft, y, imageLeft + targetW, y + targetH),
                            Paint().apply {
                                isAntiAlias = true
                                isFilterBitmap = true
                            }
                        )
                    } catch (_: Exception) {
                        canvas.drawText("[이미지 로드 실패]", margin, y, textPaint)
                    }

                    pdfDocument.finishPage(page)
                }

                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SITEBOARD")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "Siteboard_${projectTitle}_$stamp.pdf")
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                withContext(Dispatchers.Main) {
                    val snackbar = Snackbar.make(binding.root, "[$projectTitle] PDF 저장 완료 📄", Snackbar.LENGTH_LONG)
                    snackbar.setAction("폴더 열기") {
                        try {
                            val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(this@ProjectDetailActivity, "파일 관리자를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    snackbar.setActionTextColor(Color.parseColor("#FF6F00"))
                    snackbar.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProjectDetailActivity, "PDF 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
