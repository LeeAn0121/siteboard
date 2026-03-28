package com.jongwook.siteboard

import android.content.Intent
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
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.jongwook.siteboard.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var postAdapter: PostAdapter
    private lateinit var db: AppDatabase
    private var allPosts: List<PostEntity> = emptyList()
    private var filteredPosts: List<PostEntity> = emptyList()
    private var currentFilter = HomeFilter.ALL
    private var currentSort = HomeSort.RECENT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (20 * resources.displayMetrics.density).toInt())
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutSelectionMode) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBars.top + (10 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        db = AppDatabase.getDatabase(requireContext())
        postAdapter = PostAdapter { updateSelectionUi(it) }

        binding.rvPostList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPostList.adapter = postAdapter

        binding.btnOpenSub.bringToFront()
        binding.btnOpenSub.setOnClickListener {
            startActivity(Intent(requireContext(), SubActivity::class.java))
        }
        binding.btnExportPdf.setOnClickListener {
            if (allPosts.isEmpty()) {
                Toast.makeText(requireContext(), "추출할 현장 기록이 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                exportToPdf()
            }
        }
        binding.btnClearSearch.setOnClickListener { binding.etSearch.text?.clear() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnCancelSelection.setOnClickListener { postAdapter.exitSelectionMode() }
        binding.btnSelectAll.setOnClickListener { postAdapter.toggleSelectAll(filteredPosts) }
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }

        setupFilterChips()
        setupSortControls()

        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                allPosts = postList
                updateDashboard()
                applyFilters()
            }
        }
    }

    private fun setupFilterChips() {
        binding.chipFilterAll.setOnClickListener { setFilter(HomeFilter.ALL) }
        binding.chipFilterToday.setOnClickListener { setFilter(HomeFilter.TODAY) }
        binding.chipFilterMemo.setOnClickListener { setFilter(HomeFilter.MEMO) }
        binding.chipFilterMissingDetail.setOnClickListener { setFilter(HomeFilter.MISSING_DETAIL) }
        setFilter(HomeFilter.ALL)
    }

    private fun setupSortControls() {
        binding.toggleSort.check(R.id.btnSortRecent)
        binding.toggleSort.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentSort = when (checkedId) {
                R.id.btnSortName -> HomeSort.NAME
                R.id.btnSortOldest -> HomeSort.OLDEST
                else -> HomeSort.RECENT
            }
            applyFilters()
        }
    }

    private fun setFilter(filter: HomeFilter) {
        currentFilter = filter
        updateChipState(binding.chipFilterAll, filter == HomeFilter.ALL)
        updateChipState(binding.chipFilterToday, filter == HomeFilter.TODAY)
        updateChipState(binding.chipFilterMemo, filter == HomeFilter.MEMO)
        updateChipState(binding.chipFilterMissingDetail, filter == HomeFilter.MISSING_DETAIL)
        applyFilters()
    }

    private fun updateChipState(chip: Chip, checked: Boolean) {
        chip.isChecked = checked
        val bgColor = if (checked) R.color.orange_primary else R.color.surface_dark
        val textColor = if (checked) R.color.bg_dark else R.color.text_primary
        chip.setChipBackgroundColorResource(bgColor)
        chip.setTextColor(resources.getColor(textColor, null))
    }

    private fun updateDashboard() {
        val projectCount = allPosts.map { it.title.trim() }.filter { it.isNotEmpty() }.distinct().size
        val memoCount = allPosts.count { !it.memo.isNullOrBlank() }
        binding.tvTotalCount.text = allPosts.size.toString()
        binding.tvProjectCount.text = projectCount.toString()
        binding.tvMemoCount.text = memoCount.toString()
    }

    private fun applyFilters() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        val searchFiltered = allPosts.filter { post ->
            query.isBlank() ||
                post.title.contains(query, ignoreCase = true) ||
                post.description.contains(query, ignoreCase = true) ||
                (post.location?.contains(query, ignoreCase = true) == true) ||
                (post.detailLocation?.contains(query, ignoreCase = true) == true) ||
                (post.memo?.contains(query, ignoreCase = true) == true) ||
                post.date.contains(query, ignoreCase = true)
        }

        val filterApplied = searchFiltered.filter { post ->
            when (currentFilter) {
                HomeFilter.ALL -> true
                HomeFilter.TODAY -> todayPatterns().any { post.date.contains(it) }
                HomeFilter.MEMO -> !post.memo.isNullOrBlank()
                HomeFilter.MISSING_DETAIL -> post.detailLocation.isNullOrBlank()
            }
        }

        filteredPosts = when (currentSort) {
            HomeSort.RECENT -> filterApplied.sortedByDescending { it.id }
            HomeSort.NAME -> filterApplied.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            HomeSort.OLDEST -> filterApplied.sortedBy { it.id }
        }

        postAdapter.syncSelection(filteredPosts)
        postAdapter.submitList(filteredPosts)

        val isEmpty = filteredPosts.isEmpty()
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvPostList.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.tvResultSummary.text =
            "${filteredPosts.size}개 기록 · ${currentFilter.label} · ${currentSort.label}"
    }

    private fun updateSelectionUi(selectedCount: Int) {
        if (selectedCount > 0) {
            binding.layoutSelectionMode.visibility = View.VISIBLE
            binding.layoutTop.visibility = View.GONE
            binding.btnOpenSub.hide()
            binding.tvSelectedCount.text = "${selectedCount}개 선택됨"
            binding.btnSelectAll.text = if (selectedCount == filteredPosts.size && filteredPosts.isNotEmpty()) "해제" else "전체"
        } else {
            binding.layoutSelectionMode.visibility = View.GONE
            binding.layoutTop.visibility = View.VISIBLE
            binding.btnOpenSub.show()
        }
    }

    private fun confirmDeleteSelected() {
        val postsToDelete = postAdapter.selectedItems.toList()
        if (postsToDelete.isEmpty()) return

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("일괄 삭제")
            .setMessage("선택한 ${postsToDelete.size}개의 기록과 원본 사진을 완전히 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    postsToDelete.forEach { post ->
                        if (post.imageUri.isNotEmpty()) {
                            try {
                                requireContext().contentResolver.delete(android.net.Uri.parse(post.imageUri), null, null)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    try {
                        db.postDao().deleteList(postsToDelete)
                        AppDatabase.backupNow(requireContext().applicationContext)
                        SiteboardWidgetManager.refreshAll(requireContext().applicationContext)
                    } catch (_: Exception) {
                    }

                    withContext(Dispatchers.Main) {
                        postAdapter.exitSelectionMode()
                        Toast.makeText(requireContext(), "${postsToDelete.size}개가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#FF6F00"))
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#FF6F00"))
        }
        dialog.show()
    }

    private fun exportToPdf() {
        Toast.makeText(requireContext(), "PDF 보고서를 생성 중입니다. 잠시만 기다려주세요...", Toast.LENGTH_LONG).show()

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

                for ((index, post) in allPosts.withIndex()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
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
                    canvas.drawText("• 작업 내용 : ${post.description.replace("\n", " ")}", margin, currentY, textPaint)
                    currentY += 25f
                    if (!post.detailLocation.isNullOrBlank()) {
                        canvas.drawText("• 상세 위치 : ${post.detailLocation}", margin, currentY, textPaint)
                        currentY += 25f
                    }
                    if (!post.memo.isNullOrBlank()) {
                        canvas.drawText("• 메모 : ${post.memo.replace("\n", " ")}", margin, currentY, textPaint)
                        currentY += 25f
                    }
                    currentY += 8f

                    try {
                        val bitmap = loadBitmap(post.imageUri)
                        if (bitmap != null) {
                            val availableWidth = pageWidth - (margin * 2)
                            val availableHeight = pageHeight - margin - currentY
                            val scale = minOf(
                                availableWidth / bitmap.width.toFloat(),
                                availableHeight / bitmap.height.toFloat()
                            )

                            if (scale <= 0f) {
                                canvas.drawText("[! 페이지 여백이 부족하여 이미지를 배치할 수 없습니다 !]", margin, currentY, textPaint)
                                pdfDocument.finishPage(page)
                                continue
                            }

                            val targetCanvasWidth = bitmap.width * scale
                            val targetCanvasHeight = bitmap.height * scale
                            val imageLeft = margin + ((availableWidth - targetCanvasWidth) / 2f)
                            canvas.drawBitmap(
                                bitmap,
                                null,
                                RectF(imageLeft, currentY, imageLeft + targetCanvasWidth, currentY + targetCanvasHeight),
                                Paint().apply {
                                    isAntiAlias = true
                                    isFilterBitmap = true
                                    isDither = true
                                }
                            )
                        } else {
                            canvas.drawText("[! 이미지 파일을 찾을 수 없습니다 !]", margin, currentY, textPaint)
                        }
                    } catch (e: Exception) {
                        canvas.drawText("[! 이미지 로드 실패: ${e.localizedMessage} !]", margin, currentY, textPaint)
                    }

                    pdfDocument.finishPage(page)
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val siteboardDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "SITEBOARD"
                )
                if (!siteboardDir.exists()) {
                    siteboardDir.mkdirs()
                }

                val file = File(siteboardDir, "Siteboard_Report_$timeStamp.pdf")
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                withContext(Dispatchers.Main) {
                    val snackbar = Snackbar.make(requireView(), "PDF 보고서가 저장되었습니다! 📄", Snackbar.LENGTH_LONG)
                    snackbar.setAction("폴더 열기") { openSiteboardFolder(siteboardDir) }
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

    private fun loadBitmap(imageUri: String): android.graphics.Bitmap? {
        return if (imageUri.startsWith("content://") || imageUri.startsWith("file://")) {
            val uri = android.net.Uri.parse(imageUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(requireContext().contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            }
        } else {
            android.graphics.BitmapFactory.decodeFile(imageUri)
        }
    }

    private fun openSiteboardFolder(siteboardDir: File) {
        var isOpened = false
        if (siteboardDir.exists()) {
            try {
                val intent = Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
                intent.putExtra("samsung.myfiles.intent.extra.START_PATH", siteboardDir.absolutePath)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                    isOpened = true
                }
            } catch (_: Exception) {
                isOpened = false
            }
        }

        if (!isOpened) {
            try {
                val fallbackIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(fallbackIntent)
                Toast.makeText(requireContext(), "목록에서 [SITEBOARD] 폴더를 확인하세요.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "파일 관리자 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun todayPatterns(): List<String> {
        val now = Date()
        return listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(now),
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(now)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private enum class HomeFilter(val label: String) {
    ALL("전체"),
    TODAY("오늘 기록"),
    MEMO("메모 포함"),
    MISSING_DETAIL("상세 위치 미입력")
}

private enum class HomeSort(val label: String) {
    RECENT("최신순"),
    NAME("이름순"),
    OLDEST("오래된순")
}
