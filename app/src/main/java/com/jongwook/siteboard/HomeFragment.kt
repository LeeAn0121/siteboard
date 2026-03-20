package com.jongwook.siteboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jongwook.siteboard.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var postAdapter: PostAdapter
    private lateinit var db: AppDatabase
    private var allPosts: List<PostEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        // 💡 [수정] 1. 어댑터 초기화 (다중 선택 콜백 함수 연결)
        postAdapter = PostAdapter { selectedCount ->
            if (selectedCount > 0) {
                // 선택 모드 켜기 (새로 추가한 일괄 삭제 UI 띄우기)
                binding.layoutSelectionMode.visibility = View.VISIBLE
                binding.layoutTop.visibility = View.GONE
                binding.tvSelectedCount.text = "${selectedCount}개 선택됨"
            } else {
                // 선택 모드 끄기 (검색/PDF UI로 복구)
                binding.layoutSelectionMode.visibility = View.GONE
                binding.layoutTop.visibility = View.VISIBLE
            }
        }

        binding.rvPostList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPostList.adapter = postAdapter

        // 💡 [추가] 2. 다중 선택 '취소' 버튼 누를 때
        binding.btnCancelSelection.setOnClickListener {
            postAdapter.exitSelectionMode()
        }

        // 💡 [추가] 3. 다중 선택 '일괄 삭제' 버튼 누를 때
        binding.btnDeleteSelected.setOnClickListener {
            val postsToDelete = postAdapter.selectedItems.toList()

            android.app.AlertDialog.Builder(requireContext())
                .setTitle("일괄 삭제")
                .setMessage("선택한 ${postsToDelete.size}개의 기록과 원본 사진을 완전히 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // 1) 앨범(MediaStore)에서 원본 사진 싹 지우기
                            for (post in postsToDelete) {
                                if (post.imageUri.isNotEmpty()) {
                                    try {
                                        requireContext().contentResolver.delete(Uri.parse(post.imageUri), null, null)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            // 2) DB에서 데이터 일괄 삭제 (PostDao에 추가해둔 함수 사용)
                            db.postDao().deleteList(postsToDelete)

                            // 3) 삭제 끝난 뒤 UI 원래대로 돌려놓기
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                postAdapter.exitSelectionMode()
                                android.widget.Toast.makeText(requireContext(), "${postsToDelete.size}개가 삭제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }

        binding.btnOpenSub.setOnClickListener {
            val intent = Intent(requireContext(), SubActivity::class.java)
            startActivity(intent)
        }

        // 검색창 실시간 타이핑 감지
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // PDF 추출 버튼
        binding.btnExportPdf.setOnClickListener {
            if (allPosts.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "추출할 데이터가 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                exportToPdf()
            }
        }

        // DB Flow 자동 새로고침 (변경 없음)
        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                if (postList.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvPostList.visibility = View.GONE
                } else {
                    allPosts = postList
                    filterList(binding.etSearch.text.toString())

                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvPostList.visibility = View.VISIBLE
                    postAdapter.submitList(postList)
                }
            }
        }
    }

    private fun exportToPdf() {
        if (allPosts.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "추출할 현장 기록이 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        android.widget.Toast.makeText(requireContext(), "PDF 보고서를 생성 중입니다. 잠시만 기다려주세요...", android.widget.Toast.LENGTH_LONG).show()

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val margin = 50f

                val titlePaint = android.graphics.Paint().apply {
                    textSize = 24f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }

                val textPaint = android.graphics.Paint().apply {
                    textSize = 14f
                    color = android.graphics.Color.DKGRAY
                }

                for ((index, post) in allPosts.withIndex()) {
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawText("SITEBOARD 현장 보고서", margin, margin, textPaint)
                    canvas.drawLine(margin, margin + 10f, pageWidth - margin, margin + 10f, textPaint)

                    canvas.drawText(post.title, margin, margin + 50f, titlePaint)

                    var currentY = margin + 90f
                    canvas.drawText("• 위치 : ${post.location ?: "미입력"}", margin, currentY, textPaint)
                    currentY += 25f
                    canvas.drawText("• 일시 : ${post.date}", margin, currentY, textPaint)
                    currentY += 25f

                    val safeDesc = post.description.replace("\n", " ")
                    canvas.drawText("• 작업 내용 : $safeDesc", margin, currentY, textPaint)
                    currentY += 40f

                    try {
                        val imageString = post.imageUri
                        var bitmap: android.graphics.Bitmap? = null

                        if (imageString.startsWith("content://") || imageString.startsWith("file://")) {
                            val uri = android.net.Uri.parse(imageString)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                val source = android.graphics.ImageDecoder.createSource(requireContext().contentResolver, uri)
                                bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                bitmap = android.provider.MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                            }
                        } else {
                            bitmap = android.graphics.BitmapFactory.decodeFile(imageString)
                        }

                        if (bitmap != null) {
                            val highQualityPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                isFilterBitmap = true
                                isDither = true
                            }

                            val targetCanvasWidth = pageWidth - (margin * 2)
                            val scale = targetCanvasWidth / bitmap.width.toFloat()
                            val targetCanvasHeight = bitmap.height * scale

                            val dstRect = android.graphics.RectF(
                                margin,
                                currentY,
                                margin + targetCanvasWidth,
                                currentY + targetCanvasHeight
                            )

                            canvas.drawBitmap(bitmap, null, dstRect, highQualityPaint)
                        } else {
                            canvas.drawText("[! 이미지 파일을 찾을 수 없습니다 !]", margin, currentY, textPaint)
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        canvas.drawText("[! 이미지 로드 실패: ${e.localizedMessage} !]", margin, currentY, textPaint)
                    }

                    pdfDocument.finishPage(page)
                }

                val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val fileName = "Siteboard_Report_$timeStamp.pdf"
                val path = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(path, fileName)

                pdfDocument.writeTo(java.io.FileOutputStream(file))
                pdfDocument.close()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), "다운로드 폴더에 PDF 보고서가 저장되었습니다! 📄", android.widget.Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), "PDF 생성 실패: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterList(query: String) {
        val filteredList = if (query.isBlank()) {
            allPosts
        } else {
            allPosts.filter { post ->
                post.title.contains(query, ignoreCase = true) ||
                        (post.location?.contains(query, ignoreCase = true) == true)
            }
        }

        if (filteredList.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvPostList.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvPostList.visibility = View.VISIBLE
        }

        postAdapter.submitList(filteredList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}