package com.jongwook.siteboard

import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivityDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val imgMatrix = Matrix()
    private var curScale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 시스템바 인셋 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutHeader) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottom) { v, insets ->
            val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = nb.bottom + (14 * resources.displayMetrics.density).toInt())
            insets
        }

        val id              = intent.getIntExtra("id", -1)
        val title           = intent.getStringExtra("title") ?: "제목 없음"
        val desc            = intent.getStringExtra("desc") ?: ""
        val loc             = intent.getStringExtra("loc") ?: ""
        val imageUri        = intent.getStringExtra("imageUri") ?: ""
        val date            = intent.getStringExtra("date") ?: ""
        val detailLoc       = intent.getStringExtra("detailLoc") ?: ""
        val memo            = intent.getStringExtra("memo") ?: ""
        val originalUri     = intent.getStringExtra("originalUri") ?: ""

        binding.tvDetailTitle.text    = title
        binding.tvDetailDate.text     = "📅 $date"
        binding.tvDetailLocation.text = if (loc.isBlank()) "📍 위치 미입력" else "📍 $loc"
        binding.tvDetailDesc.text     = if (desc.isBlank()) "작업 내용 없음" else desc

        // 상세 위치 표시
        if (detailLoc.isNotBlank()) {
            binding.layoutDetailLocation.visibility = android.view.View.VISIBLE
            binding.tvDetailDetailLocation.text = "📌 $detailLoc"
        }

        // 메모 표시
        if (memo.isNotBlank()) {
            binding.layoutMemo.visibility = android.view.View.VISIBLE
            binding.tvDetailMemo.text = memo
        }

        binding.tvDetailLocation.setOnClickListener {
            if (loc.isNotBlank()) {
                val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(loc)}")
                try { startActivity(Intent(Intent.ACTION_VIEW, geoUri)) }
                catch (e: Exception) { Toast.makeText(this, "실행할 수 있는 지도 앱이 없습니다.", Toast.LENGTH_SHORT).show() }
            } else {
                Toast.makeText(this, "저장된 위치 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        if (imageUri.isNotEmpty()) {
            try { binding.ivDetailImage.setImageURI(Uri.parse(imageUri)) }
            catch (e: Exception) { e.printStackTrace() }
        }

        binding.ivDetailImage.post { initMatrix() }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnOpenOriginal.setOnClickListener {
            val targetUri = when {
                originalUri.isNotBlank() -> Uri.parse(originalUri)
                imageUri.isNotBlank() -> Uri.parse(imageUri)
                else -> null
            }

            if (targetUri == null) {
                Toast.makeText(this, "표시할 원본 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(openIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "원본 이미지를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        setupZoom()

        binding.btnShare.setOnClickListener {
            if (imageUri.isEmpty()) {
                Toast.makeText(this, "공유할 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "SITEBOARD 현장 기록 공유"))
        }

        binding.btnEdit.setOnClickListener {
            startActivity(Intent(this, SubActivity::class.java).apply {
                putExtra("edit_id", id)
                putExtra("edit_title", title)
                putExtra("edit_desc", desc)
                putExtra("edit_loc", loc)
                putExtra("edit_detail_loc", detailLoc)
                putExtra("edit_memo", memo)
                putExtra("edit_imageUri", imageUri)
                putExtra("edit_originalUri", originalUri)
                putExtra("edit_date", date)
            })
            finish()
        }

        binding.btnDelete.setOnClickListener {
            if (id == -1) return@setOnClickListener
            lifecycleScope.launch(Dispatchers.IO) {
                // 워터마크 이미지 삭제
                try { if (imageUri.isNotEmpty()) contentResolver.delete(Uri.parse(imageUri), null, null) }
                catch (e: Exception) { e.printStackTrace() }
                // DB에서 레코드 삭제 (getById로 전체 엔티티 조회 후 삭제)
                try {
                    val post = db.postDao().getById(id)
                    if (post != null) {
                        db.postDao().delete(post)
                        AppDatabase.backupNow(applicationContext)
                        SiteboardWidgetManager.refreshAll(applicationContext)
                    }
                } catch (e: Exception) { e.printStackTrace() }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailActivity, "워터마크 기록이 삭제되었습니다. 원본은 갤러리에 유지됩니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /** 이미지를 고정 영역(fitCenter)에 맞게 초기 matrix 세팅 */
    private fun initMatrix() {
        val iv = binding.ivDetailImage
        val drawable = iv.drawable ?: return
        val dW = drawable.intrinsicWidth.toFloat()
        val dH = drawable.intrinsicHeight.toFloat()
        val vW = iv.width.toFloat()
        val vH = iv.height.toFloat()
        if (dW <= 0 || dH <= 0 || vW <= 0 || vH <= 0) return

        val scale = minOf(vW / dW, vH / dH)
        imgMatrix.setScale(scale, scale)
        imgMatrix.postTranslate((vW - dW * scale) / 2f, (vH - dH * scale) / 2f)
        curScale = scale
        iv.imageMatrix = imgMatrix
    }

    private fun setupZoom() {
        val iv = binding.ivDetailImage

        val scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    // 스크롤뷰가 터치를 가로채지 못하도록
                    binding.scrollAll.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val drawable = iv.drawable ?: return true
                    val dW = drawable.intrinsicWidth.toFloat()
                    val dH = drawable.intrinsicHeight.toFloat()
                    val vW = iv.width.toFloat()
                    val vH = iv.height.toFloat()
                    val minScale = minOf(vW / dW, vH / dH)   // fitCenter 기준 최소 배율
                    val maxScale = minScale * 5f

                    val newScale = (curScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                    val ratio = newScale / curScale
                    imgMatrix.postScale(ratio, ratio, detector.focusX, detector.focusY)
                    curScale = newScale
                    iv.imageMatrix = imgMatrix
                    return true
                }
                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    // 최소 배율이면 중앙으로 복귀
                    val drawable = iv.drawable ?: return
                    val dW = drawable.intrinsicWidth.toFloat()
                    val dH = drawable.intrinsicHeight.toFloat()
                    val minScale = minOf(iv.width / dW, iv.height / dH)
                    if (curScale <= minScale * 1.05f) initMatrix()
                }
            })

        iv.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    pointerId = event.getPointerId(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    // 확대 상태에서만 패닝, 단일 터치일 때
                    if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                        val idx = event.findPointerIndex(pointerId)
                        if (idx >= 0) {
                            imgMatrix.postTranslate(event.getX(idx) - lastX, event.getY(idx) - lastY)
                            iv.imageMatrix = imgMatrix
                            lastX = event.getX(idx)
                            lastY = event.getY(idx)
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // 두 번째 손가락이 닿으면 스크롤 막기
                    binding.scrollAll.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerId = MotionEvent.INVALID_POINTER_ID
                    binding.scrollAll.requestDisallowInterceptTouchEvent(false)
                }
            }
            true
        }
    }
}
