package com.jongwook.siteboard

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jongwook.siteboard.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val postAdapter = PostAdapter()
    private val db by lazy { AppDatabase.getDatabase(this) }

    // 원본 데이터를 보관할 리스트
    private var allPosts: List<PostEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecyclerView()
        observePosts()
        setupSearch()

        binding.btnOpenSub.setOnClickListener {
            startActivity(Intent(this, SubActivity::class.java))
        }

        binding.btnExportPdf.setOnClickListener {
            exportToPdf()
        }
    }

    private fun initRecyclerView() {
        binding.rvPostList.apply {
            // [UX 개선] 1열 리스트에서 2열 격자(Grid) 뷰로 변경!
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = postAdapter
            setHasFixedSize(true)
        }
    }

    private fun observePosts() {
        lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                allPosts = postList
                filterList(binding.etSearch.text.toString()) // 데이터가 갱신되면 검색어에 맞게 필터링
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // [기능 추가] 검색 필터링 및 텅 빈 화면(Empty State) 처리
    private fun filterList(query: String) {
        val filtered = if (query.isEmpty()) {
            allPosts
        } else {
            allPosts.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.location?.contains(query, ignoreCase = true) == true
            }
        }

        postAdapter.submitList(filtered)

        // 검색 결과가 없거나 게시글이 0개면 안내 문구 표시
        if (filtered.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvPostList.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvPostList.visibility = View.VISIBLE
        }
    }

    // 클래스 맨 아래에 PDF 생성 함수 추가
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToPdf() {
        if (allPosts.isEmpty()) {
            android.widget.Toast.makeText(this, "내보낼 데이터가 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(this@MainActivity, "PDF 보고서를 생성 중입니다...", android.widget.Toast.LENGTH_SHORT).show()
                }

                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 사이즈 규격

                // 모든 게시글을 순회하며 페이지 생성
                for ((index, post) in allPosts.withIndex()) {
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    val paint = android.graphics.Paint().apply {
                        textSize = 18f
                        color = android.graphics.Color.BLACK
                        isFakeBoldText = true
                    }

                    // 텍스트 정보 입력
                    canvas.drawText("📋 SITEBOARD 현장 작업 보고서", 50f, 60f, paint)
                    paint.textSize = 14f
                    paint.isFakeBoldText = false
                    canvas.drawText("• 현장명(제목): ${post.title}", 50f, 110f, paint)
                    canvas.drawText("• 위치: ${post.location}", 50f, 140f, paint)
                    canvas.drawText("• 일시: ${post.date}", 50f, 170f, paint)
                    canvas.drawText("• 작업 내용: ${post.description}", 50f, 200f, paint)

                    // 사진 입력 (A4 사이즈에 맞게 비율 조정)
                    try {
                        val uri = android.net.Uri.parse(post.imageUri)
                        val inputStream = contentResolver.openInputStream(uri)
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 495, (495f * bitmap.height / bitmap.width).toInt(), true)
                            canvas.drawBitmap(scaledBitmap, 50f, 240f, null)
                        }
                        inputStream?.close()
                    } catch (e: Exception) { e.printStackTrace() }

                    pdfDocument.finishPage(page)
                }

                // 스마트폰 [다운로드] 폴더에 PDF 저장
                val fileName = "SITEBOARD_Report_${System.currentTimeMillis()}.pdf"
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream -> pdfDocument.writeTo(outputStream) }
                }
                pdfDocument.close()

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(this@MainActivity, "다운로드 폴더에 PDF가 저장되었습니다!", android.widget.Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(this@MainActivity, "PDF 변환 실패: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}