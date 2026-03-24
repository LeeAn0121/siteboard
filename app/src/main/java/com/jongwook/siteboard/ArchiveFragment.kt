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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jongwook.siteboard.databinding.FragmentArchiveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
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

    private var groupedPosts: Map<String, List<PostEntity>> = emptyMap()
    private var allTitles: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArchiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 시스템 상단바 간격 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutHeader) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (20 * resources.displayMetrics.density).toInt())
            insets
        }

        db = AppDatabase.getDatabase(requireContext())

        // 검색
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                groupedPosts = postList.groupBy { it.title }
                allTitles = groupedPosts.keys.toList()
                applyFilter(binding.etSearch.text.toString())
            }
        }
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isBlank()) allTitles
        else allTitles.filter { it.contains(query, ignoreCase = true) }

        if (filtered.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.lvProjects.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.lvProjects.visibility = View.VISIBLE
        }

        val adapter = object : android.widget.ArrayAdapter<String>(
            requireContext(), R.layout.item_project_list, R.id.tvProjectItem, filtered
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val title = filtered[position]
                val count = groupedPosts[title]?.size ?: 0
                v.findViewById<TextView>(R.id.tvProjectCount)?.text = "사진 ${count}장"
                v.setOnClickListener {
                    val intent = android.content.Intent(requireContext(), ProjectDetailActivity::class.java)
                    intent.putExtra("PROJECT_TITLE", title)
                    startActivity(intent)
                }
                v.findViewById<TextView>(R.id.btnExportPdf)?.setOnClickListener {
                    val posts = groupedPosts[title] ?: emptyList()
                    if (posts.isEmpty()) {
                        Toast.makeText(requireContext(), "내보낼 기록이 없습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        exportToPdf(title, posts)
                    }
                }
                return v
            }
        }

        binding.lvProjects.adapter = adapter
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
                    textSize = 24f; isFakeBoldText = true; color = Color.BLACK
                }
                val textPaint = Paint().apply {
                    textSize = 14f; color = Color.DKGRAY
                }

                for ((index, post) in posts.withIndex()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawText("SITEBOARD 현장 보고서  |  $siteTitle", margin, margin, textPaint)
                    canvas.drawLine(margin, margin + 10f, pageWidth - margin, margin + 10f, textPaint)
                    canvas.drawText(post.title, margin, margin + 50f, titlePaint)

                    var y = margin + 90f
                    canvas.drawText("• 위치 : ${post.location ?: "미입력"}", margin, y, textPaint); y += 25f
                    canvas.drawText("• 일시 : ${post.date}", margin, y, textPaint); y += 25f
                    canvas.drawText("• 작업 내용 : ${post.description.replace("\n", " ")}", margin, y, textPaint); y += 40f

                    try {
                        val uri = android.net.Uri.parse(post.imageUri)
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(requireContext().contentResolver, uri)
                            ) { dec, _, _ -> dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                        }
                        val targetW = pageWidth - margin * 2
                        val scale = targetW / bitmap.width.toFloat()
                        val imgPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
                        canvas.drawBitmap(bitmap, null, RectF(margin, y, margin + targetW, y + bitmap.height * scale), imgPaint)
                    } catch (e: Exception) {
                        canvas.drawText("[이미지 로드 실패]", margin, y, textPaint)
                    }

                    pdfDocument.finishPage(page)
                }

                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Siteboard_${siteTitle}_$stamp.pdf"
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SITEBOARD")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                withContext(Dispatchers.Main) {
                    val snackbar = Snackbar.make(requireView(), "[$siteTitle] PDF 저장 완료 📄", Snackbar.LENGTH_LONG)
                    snackbar.setAction("폴더 열기") {
                        try {
                            val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        } catch (e: Exception) {
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
