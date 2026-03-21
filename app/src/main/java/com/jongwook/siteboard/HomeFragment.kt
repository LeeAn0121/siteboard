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

        // 💡 1. 어댑터 초기화 및 선택 모드 상태 관리
        postAdapter = PostAdapter { selectedCount ->
            if (selectedCount > 0) {
                // 선택 모드 ON: 추가 버튼 숨기고 삭제 메뉴 노출
                binding.layoutSelectionMode.visibility = View.VISIBLE
                binding.layoutTop.visibility = View.GONE
                binding.btnOpenSub.hide() // FAB 숨기기 (로직 충돌 방지)
                binding.tvSelectedCount.text = "${selectedCount}개 선택됨"
            } else {
                // 선택 모드 OFF: 추가 버튼 다시 보이고 일반 메뉴 노출
                binding.layoutSelectionMode.visibility = View.GONE
                binding.layoutTop.visibility = View.VISIBLE
                binding.btnOpenSub.show() // FAB 다시 보이기
            }
        }

        // 💡 2. 리사이클러뷰 세팅
        binding.rvPostList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPostList.adapter = postAdapter

        // 💡 3. [가장 중요] 추가 버튼(+) 클릭 리스너 및 우선순위 확보
        binding.btnOpenSub.bringToFront() // 다른 레이아웃보다 위로 올리기
        binding.btnOpenSub.setOnClickListener {
            val intent = Intent(requireContext(), SubActivity::class.java)
            startActivity(intent)
        }

        // 💡 4. 선택 모드 취소/삭제 버튼 리스너
        binding.btnCancelSelection.setOnClickListener {
            postAdapter.exitSelectionMode()
        }

        binding.btnDeleteSelected.setOnClickListener {
            val postsToDelete = postAdapter.selectedItems.toList()

            // 1. 빌더로 다이얼로그를 생성만 합니다 (show 대신 create 사용)
            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setTitle("일괄 삭제")
                .setMessage("선택한 ${postsToDelete.size}개의 기록과 원본 사진을 완전히 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            for (post in postsToDelete) {
                                if (post.imageUri.isNotEmpty()) {
                                    requireContext().contentResolver.delete(android.net.Uri.parse(post.imageUri), null, null)
                                }
                            }
                            db.postDao().deleteList(postsToDelete)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                postAdapter.exitSelectionMode()
                                android.widget.Toast.makeText(requireContext(), "${postsToDelete.size}개가 삭제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                .setNegativeButton("취소", null)
                .create()

            // 🚀 2. [핵심] 팝업창이 화면에 뜰 때 버튼 색상을 주황색(#FF6F00)으로 덮어씌웁니다.
            dialog.setOnShowListener {
                // BUTTON_POSITIVE = "삭제" 버튼
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#FF6F00"))

                // BUTTON_NEGATIVE = "취소" 버튼
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#FF6F00"))
                // (만약 취소 버튼은 회색 등 다른 색으로 하고 싶으시면 위 색상 코드를 "#888888" 등으로 바꾸시면 됩니다)
            }

            // 3. 화면에 띄우기
            dialog.show()
        }

        // 💡 5. 검색창 및 PDF 버튼 (기존 유지)
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnExportPdf.setOnClickListener {
            if (allPosts.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "추출할 현장 기록이 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                exportToPdf()
            }
        }

        // 💡 6. 실시간 데이터 관찰 (기존 유지)
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