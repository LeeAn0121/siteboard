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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jongwook.siteboard.databinding.FragmentArchiveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchiveFragment : Fragment() {
    private var _binding: FragmentArchiveBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var archiveAdapter: ArchiveProjectAdapter

    private var allProjects: List<ProjectSummary> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArchiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutHeader) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (20 * resources.displayMetrics.density).toInt())
            insets
        }

        db = AppDatabase.getDatabase(requireContext())
        archiveAdapter = ArchiveProjectAdapter(
            onOpenProject = { summary ->
                val intent = android.content.Intent(requireContext(), ProjectDetailActivity::class.java)
                intent.putExtra("PROJECT_TITLE", summary.title)
                startActivity(intent)
            },
            onExportPdf = { summary ->
                if (summary.posts.isEmpty()) {
                    Toast.makeText(requireContext(), "내보낼 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    exportToPdf(summary.title, summary.posts)
                }
            }
        )

        binding.rvProjects.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProjects.adapter = archiveAdapter

        binding.btnClearSearch.setOnClickListener { binding.etSearch.text?.clear() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                allProjects = postList
                    .groupBy { it.title }
                    .map { (title, posts) ->
                        val recentPost = posts.maxByOrNull { it.id }!!
                        ProjectSummary(
                            title = title,
                            count = posts.size,
                            recentDate = recentPost.date,
                            recentLocation = recentPost.detailLocation?.takeIf { it.isNotBlank() }
                                ?: recentPost.location?.takeIf { it.isNotBlank() }
                                ?: "위치 미입력",
                            recentPostId = recentPost.id,
                            posts = posts.sortedByDescending { it.id }
                        )
                    }
                    .sortedWith(compareByDescending<ProjectSummary> { it.recentPostId }.thenBy { it.title.lowercase(Locale.getDefault()) })

                binding.tvProjectCount.text = allProjects.size.toString()
                binding.tvArchiveCount.text = postList.size.toString()
                applyFilter(binding.etSearch.text?.toString().orEmpty())
            }
        }
    }

    private fun applyFilter(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isBlank()) {
            allProjects
        } else {
            allProjects.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.recentLocation.contains(trimmed, ignoreCase = true)
            }
        }

        archiveAdapter.submitList(filtered)
        binding.tvResultSummary.text = "${filtered.size}개 현장 · 최신 업데이트순"
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvProjects.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun exportToPdf(siteTitle: String, posts: List<PostEntity>) {
        Toast.makeText(requireContext(), "PDF 보고서를 생성 중입니다...", Toast.LENGTH_SHORT).show()

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

                    canvas.drawText("SITEBOARD 현장 보고서  |  $siteTitle", margin, margin, textPaint)
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
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(requireContext().contentResolver, uri)) { dec, _, _ ->
                                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
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
                val file = File(dir, "Siteboard_${siteTitle}_$stamp.pdf")
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                withContext(Dispatchers.Main) {
                    val snackbar = Snackbar.make(requireView(), "[$siteTitle] PDF 저장 완료 📄", Snackbar.LENGTH_LONG)
                    snackbar.setAction("폴더 열기") {
                        try {
                            val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), "파일 관리자를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    snackbar.setActionTextColor(Color.parseColor("#FF6F00"))
                    snackbar.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "PDF 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
