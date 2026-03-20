package com.jongwook.siteboard

import android.content.Intent
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

    // 어댑터와 DB 변수 선언
    private lateinit var postAdapter: PostAdapter
    private lateinit var db: AppDatabase

    // 💡 [추가] 검색을 위해 원본 데이터를 저장해둘 변수
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

        // 💡 1. 종욱님의 클래스에 맞게 DB 객체 가져오기 (getDatabase)
        db = AppDatabase.getDatabase(requireContext())

        // 💡 2. 파라미터 없는 ListAdapter 초기화 및 2열 격자 세팅
        postAdapter = PostAdapter()
        binding.rvPostList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPostList.adapter = postAdapter

        // 💡 3. 플로팅 버튼 클릭 시 서브 화면(글쓰기) 열기
        binding.btnOpenSub.setOnClickListener {
            val intent = Intent(requireContext(), SubActivity::class.java)
            startActivity(intent)
        }

        // 💡 [추가 1] 검색창 실시간 타이핑 감지
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 글자가 입력될 때마다 필터링 함수 실행
                filterList(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // 💡 [추가 2] PDF 추출 버튼 클릭 이벤트
        binding.btnExportPdf.setOnClickListener {
            if (allPosts.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "추출할 데이터가 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                // 종욱님이 기존에 만들어두셨던 PDF 생성 함수 호출!
                exportToPdf()
            }
        }

        // 💡 4. [핵심] Room DB의 Flow 관찰하여 자동 새로고침!
        // 종욱님이 Dao에 Flow를 선언하셨기 때문에, 여기서 collect만 해두면
        // 새 사진을 찍고 돌아왔을 때 알아서 UI가 최신화됩니다.
        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                if (postList.isEmpty()) {
                    // 데이터가 없으면 '카메라 아이콘' 띄우기
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvPostList.visibility = View.GONE
                } else {
                    allPosts = postList // 원본 데이터 백업
                    filterList(binding.etSearch.text.toString()) // 현재 검색어에 맞게 리스트 업데이트

                    // 데이터가 있으면 2열 리스트 띄우기
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvPostList.visibility = View.VISIBLE

                    // ListAdapter 전용 함수인 submitList()로 데이터 밀어넣기
                    postAdapter.submitList(postList)
                }
            }
        }
    }

    // 💡 PDF 추출 메인 로직
    private fun exportToPdf() {
        if (allPosts.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "추출할 현장 기록이 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 사용자가 여러 번 누르지 않도록 안내
        android.widget.Toast.makeText(requireContext(), "PDF 보고서를 생성 중입니다. 잠시만 기다려주세요...", android.widget.Toast.LENGTH_LONG).show()

        // 사진 합성 및 PDF 생성은 무거운 작업이므로 백그라운드(IO)에서 실행
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 안드로이드 내장 PDF 문서 객체 생성
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageWidth = 595 // A4 표준 가로 사이즈 (포인트 단위)
                val pageHeight = 842 // A4 표준 세로 사이즈
                val margin = 50f    // 상하좌우 여백

                // 텍스트를 그릴 붓(Paint) 세팅
                val titlePaint = android.graphics.Paint().apply {
                    textSize = 24f
                    isFakeBoldText = true // 볼드체
                    color = android.graphics.Color.BLACK
                }

                val textPaint = android.graphics.Paint().apply {
                    textSize = 14f
                    color = android.graphics.Color.DKGRAY
                }

                // 모든 현장 기록을 순회하며 한 장당 하나씩 A4 용지에 그리기
                for ((index, post) in allPosts.withIndex()) {
                    // 페이지 생성 (A4 사이즈)
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // 1. 문서 헤더 및 장식 선
                    canvas.drawText("SITEBOARD 현장 보고서", margin, margin, textPaint)
                    canvas.drawLine(margin, margin + 10f, pageWidth - margin, margin + 10f, textPaint)

                    // 2. 메인 제목 (프로젝트명)
                    canvas.drawText(post.title, margin, margin + 50f, titlePaint)

                    // 3. 상세 정보 (위치, 일시, 작업 내용)
                    var currentY = margin + 90f
                    canvas.drawText("• 위치 : ${post.location ?: "미입력"}", margin, currentY, textPaint)
                    currentY += 25f
                    canvas.drawText("• 일시 : ${post.date}", margin, currentY, textPaint)
                    currentY += 25f

                    // (작업 내용이 길 경우 줄바꿈 처리가 복잡하므로 1줄로 단순화하여 표시)
                    val safeDesc = post.description.replace("\n", " ")
                    canvas.drawText("• 작업 내용 : $safeDesc", margin, currentY, textPaint)
                    currentY += 40f

                    // 4. 현장 사진 불러오기 (고화질 유지)
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
                            // 💡 고화질 인쇄를 위한 특수 붓(Paint) 세팅
                            val highQualityPaint = android.graphics.Paint().apply {
                                isAntiAlias = true       // 테두리 부드럽게 (계단현상 방지)
                                isFilterBitmap = true    // 비트맵 스무딩 처리
                                isDither = true          // 색상 깨짐 방지
                            }

                            // PDF 상에서 사진이 차지할 '물리적 너비와 높이' 계산
                            val targetCanvasWidth = pageWidth - (margin * 2)
                            val scale = targetCanvasWidth / bitmap.width.toFloat()
                            val targetCanvasHeight = bitmap.height * scale

                            // 💡 사진 픽셀을 자르지 않고, 고화질 원본을 지정된 영역(RectF)에 압축해서 그려넣음!
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

                    // 한 페이지 작업 끝
                    pdfDocument.finishPage(page)
                }

                // 5. 완성된 PDF를 스마트폰 '다운로드' 폴더에 파일로 저장
                val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val fileName = "Siteboard_Report_$timeStamp.pdf"
                val path = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(path, fileName)

                pdfDocument.writeTo(java.io.FileOutputStream(file))
                pdfDocument.close() // 메모리 해제 필수!

                // 저장이 완료되면 사용자에게 알림
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

    // 💡 [추가 3] 검색 필터링 함수
    private fun filterList(query: String) {
        val filteredList = if (query.isBlank()) {
            allPosts // 검색어가 없으면 전체 다 보여줌
        } else {
            allPosts.filter { post ->
                // 제목이나 위치에 검색어가 포함되어 있으면 합격! (대소문자 무시)
                post.title.contains(query, ignoreCase = true) ||
                        (post.location?.contains(query, ignoreCase = true) == true)
            }
        }

        // 결과에 따라 빈 화면(카메라 아이콘) 처리
        if (filteredList.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvPostList.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvPostList.visibility = View.VISIBLE
        }

        // 걸러진 리스트를 어댑터에 쏴주기
        postAdapter.submitList(filteredList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 메모리 누수 방지
    }
}