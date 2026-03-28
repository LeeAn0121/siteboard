package com.jongwook.siteboard

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.jongwook.siteboard.databinding.ActivityBatchEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class BatchEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBatchEditBinding
    private val db by lazy { AppDatabase.getDatabase(this) }

    private var selectedUris: List<Uri> = emptyList()
    private var selectedTheme = 0
    private var anchorPosition = 6 // default: bottom-left
    private var bgOpacity = 40     // 0-100
    private var savedLocation: String = ""

    private lateinit var themePreviewViews: List<ImageView>
    private lateinit var anchorViews: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // URI 목록 수신
        val parcelableUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("selected_uris", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>("selected_uris")
        }
        selectedUris = parcelableUris ?: emptyList()

        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "선택된 사진이 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 이전 화면에서 전달된 정보 채우기
        binding.etTitle.setText(intent.getStringExtra("edit_title") ?: "")
        savedLocation = intent.getStringExtra("edit_loc") ?: ""
        binding.etDesc.setText(intent.getStringExtra("edit_desc") ?: "")

        binding.tvPhotoCount.text = "${selectedUris.size}장 선택"

        binding.btnBack.setOnClickListener { finish() }

        setupThumbnails()
        setupThemeSelector()
        setupAnchorGrid()
        setupOpacitySlider()

        binding.btnDetailSettings.setOnClickListener {
            startActivity(android.content.Intent(this, WatermarkSettingsActivity::class.java))
        }

        binding.btnApplyAll.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "현장명을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            applyToAllPhotos()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAllThemePreviews()
    }

    // ── 썸네일 표시 ──────────────────────────────────────────────────────────────
    private fun setupThumbnails() {
        val size = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            .coerceAtLeast(96).let { 96 * 3 } // ~288px
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        val cornerPx = (12 * resources.displayMetrics.density)

        binding.layoutThumbnails.removeAllViews()

        lifecycleScope.launch(Dispatchers.IO) {
            for (uri in selectedUris) {
                try {
                    val bmp = loadThumbnail(uri, 288)
                    withContext(Dispatchers.Main) {
                        val imgView = ImageView(this@BatchEditActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                                marginEnd = marginPx
                            }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageBitmap(bmp)
                            // 둥근 모서리를 위해 클립패스 사용
                            outlineProvider = object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: android.graphics.Outline) {
                                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                                }
                            }
                            clipToOutline = true
                        }
                        binding.layoutThumbnails.addView(imgView)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadThumbnail(uri: Uri, size: Int): Bitmap? {
        return try {
            var stream: InputStream? = contentResolver.openInputStream(uri)
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, opts)
            stream?.close()

            val scale = maxOf(opts.outWidth / size, opts.outHeight / size).coerceAtLeast(1)
            val opts2 = BitmapFactory.Options().apply { inSampleSize = scale }
            stream = contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream, null, opts2)
            stream?.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    // ── 테마 선택기 ───────────────────────────────────────────────────────────────
    private fun setupThemeSelector() {
        val themeLayouts = listOf(binding.layoutTheme0, binding.layoutTheme1, binding.layoutTheme2, binding.layoutTheme3)
        val themeCardViews = listOf(binding.viewTheme0, binding.viewTheme1, binding.viewTheme2, binding.viewTheme3)
        val themeLabels = listOf(binding.tvThemeLabel0, binding.tvThemeLabel1, binding.tvThemeLabel2, binding.tvThemeLabel3)
        themePreviewViews = listOf(binding.ivThemePreview0, binding.ivThemePreview1, binding.ivThemePreview2, binding.ivThemePreview3)

        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        selectedTheme = prefs.getInt("wm_theme_index", 0)
        updateAllThemePreviews()
        updateThemeSelection(themeCardViews, themeLabels, selectedTheme)

        themeLayouts.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                selectedTheme = index
                updateThemeSelection(themeCardViews, themeLabels, index)
                applyThemePreset(index)
            }
        }
    }

    private fun updateThemeSelection(cards: List<MaterialCardView>, labels: List<TextView>, selectedIndex: Int) {
        val strokePx = (2 * resources.displayMetrics.density).toInt()
        cards.forEachIndexed { i, card ->
            card.strokeWidth = if (i == selectedIndex) strokePx else 0
        }
        labels.forEachIndexed { i, label ->
            label.setTextColor(if (i == selectedIndex) getColor(R.color.orange_primary) else Color.parseColor("#A0A0A5"))
        }
    }

    private fun updateAllThemePreviews() {
        if (!::themePreviewViews.isInitialized) return
        themePreviewViews.forEachIndexed { index, iv ->
            iv.setImageBitmap(createThemePreviewBitmap(index))
        }
    }

    private fun createThemePreviewBitmap(themeIndex: Int): Bitmap {
        val w = 240; val h = 240
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#1A1A1E"))

        data class ThemeCfg(val color: Int, val font: String, val size: Float, val bg: Boolean)
        val themes = arrayOf(
            ThemeCfg(Color.WHITE, "SANS_SERIF_LIGHT", 20f, false),
            ThemeCfg(Color.YELLOW, "MONOSPACE", 22f, true),
            ThemeCfg(Color.WHITE, "SERIF", 21f, true),
            ThemeCfg(Color.YELLOW, "SANS_SERIF_BLACK", 26f, true)
        )
        val cfg = themes[themeIndex]
        val typeface = when (cfg.font) {
            "SERIF" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            "MONOSPACE" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            "SANS_SERIF_LIGHT" -> Typeface.create("sans-serif-light", Typeface.BOLD)
            "SANS_SERIF_BLACK" -> Typeface.create("sans-serif-black", Typeface.BOLD)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cfg.color; textSize = cfg.size; this.typeface = typeface
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        val lines = listOf("현장명", "2026-03-23")
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val spacing = cfg.size * 0.4f
        val totalH = lines.size * lineH + (lines.size - 1) * spacing
        val maxW = lines.maxOf { paint.measureText(it) }
        val sx = 14f; val sy = h - 14f - totalH
        if (cfg.bg) {
            val bg = Paint().apply { color = Color.parseColor("#88000000") }
            val pad = cfg.size * 0.3f
            canvas.drawRoundRect(RectF(sx - pad, sy - pad, sx + maxW + pad, sy + totalH + pad), 5f, 5f, bg)
        }
        var y = sy - fm.ascent
        for (line in lines) { canvas.drawText(line, sx, y, paint); y += lineH + spacing }
        return bmp
    }

    private fun applyThemePreset(index: Int) {
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE).edit()
        prefs.putInt("wm_theme_index", index)
        when (index) {
            0 -> { prefs.putBoolean("wm_use_bg", false); prefs.putInt("wm_color", Color.WHITE); prefs.putString("wm_font", "SANS_SERIF_LIGHT"); prefs.putFloat("wm_font_size", 28f) }
            1 -> { prefs.putBoolean("wm_use_bg", true); prefs.putInt("wm_color", Color.YELLOW); prefs.putString("wm_font", "MONOSPACE"); prefs.putFloat("wm_font_size", 40f) }
            2 -> { prefs.putBoolean("wm_use_bg", true); prefs.putInt("wm_color", Color.WHITE); prefs.putString("wm_font", "SERIF"); prefs.putFloat("wm_font_size", 36f) }
            3 -> { prefs.putBoolean("wm_use_bg", true); prefs.putInt("wm_color", Color.YELLOW); prefs.putString("wm_font", "SANS_SERIF_BLACK"); prefs.putFloat("wm_font_size", 50f) }
        }
        prefs.apply()
    }

    // ── 앵커 그리드 ───────────────────────────────────────────────────────────────
    private fun setupAnchorGrid() {
        anchorViews = listOf(
            binding.anchor0, binding.anchor1, binding.anchor2,
            binding.anchor3, binding.anchor4, binding.anchor5,
            binding.anchor6, binding.anchor7, binding.anchor8
        )
        // prefs에서 저장된 앵커 위치 불러오기
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        anchorPosition = prefs.getInt("wm_anchor_pos", 6)
        updateAnchorUI()

        anchorViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                anchorPosition = index
                updateAnchorUI()
            }
        }
    }

    private fun updateAnchorUI() {
        anchorViews.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index == anchorPosition) R.drawable.anchor_cell_selected
                else R.drawable.anchor_cell_normal
            )
        }
    }

    // ── 투명도 슬라이더 ───────────────────────────────────────────────────────────
    private fun setupOpacitySlider() {
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        bgOpacity = prefs.getInt("wm_bg_opacity", 40)
        binding.seekbarOpacity.progress = bgOpacity
        binding.tvOpacityValue.text = "$bgOpacity%"

        binding.seekbarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                bgOpacity = progress
                binding.tvOpacityValue.text = "$bgOpacity%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    // ── 배치 처리 ─────────────────────────────────────────────────────────────────
    private fun applyToAllPhotos() {
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val loc = savedLocation

        // 앵커 위치와 투명도 저장
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE).edit()
        prefs.putInt("wm_anchor_pos", anchorPosition)
        prefs.putInt("wm_bg_opacity", bgOpacity)
        prefs.apply()

        binding.layoutLoading.visibility = View.VISIBLE
        binding.btnApplyAll.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0

            selectedUris.forEachIndexed { index, uri ->
                withContext(Dispatchers.Main) {
                    binding.tvLoadingMessage.text = "처리 중... (${index + 1}/${selectedUris.size})"
                }
                try {
                    val orientedBitmap = loadOrientedBitmapFromUri(uri) ?: throw Exception("이미지 로드 실패")

                    // 개인정보 마스킹 설정 확인
                    val siteboardPrefs = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
                    val isPrivacyBlurEnabled = siteboardPrefs.getBoolean("privacy_blur_mode", true)

                    val bitmapToStamp = if (isPrivacyBlurEnabled) {
                        detectAndBlurPrivacy(orientedBitmap).also {
                            if (it != orientedBitmap && !orientedBitmap.isRecycled) orientedBitmap.recycle()
                        }
                    } else {
                        orientedBitmap
                    }

                    val stampedBitmap = stampTextOnBitmap(bitmapToStamp, title, desc, loc)
                    if (bitmapToStamp != stampedBitmap && !bitmapToStamp.isRecycled) bitmapToStamp.recycle()

                    val savedUri = saveBitmapToGallery(stampedBitmap, title) ?: throw Exception("저장 실패")
                    if (!stampedBitmap.isRecycled) stampedBitmap.recycle()

                    val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    db.postDao().insert(PostEntity(title = title, description = desc, location = loc, imageUri = savedUri.toString(), date = currentDate))
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                    failCount++
                }
            }

            if (successCount > 0) {
                AppDatabase.backupNow(applicationContext)
                SiteboardWidgetManager.refreshAll(applicationContext)
            }

            withContext(Dispatchers.Main) {
                binding.layoutLoading.visibility = View.GONE
                binding.btnApplyAll.isEnabled = true
                val msg = if (failCount == 0)
                    "${successCount}장이 성공적으로 저장되었습니다."
                else
                    "${successCount}장 저장 완료, ${failCount}장 실패"
                Toast.makeText(this@BatchEditActivity, msg, Toast.LENGTH_LONG).show()
                if (successCount > 0) finish()
            }
        }
    }

    // ── 이미지 처리 유틸 ─────────────────────────────────────────────────────────
    private fun loadOrientedBitmapFromUri(uri: Uri): Bitmap? {
        var stream: InputStream? = null
        return try {
            stream = contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bmp == null) return null

            stream = contentResolver.openInputStream(uri)
            val exif = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.media.ExifInterface(stream!!)
            } else null
            stream?.close()

            val orientation = exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                ?: android.media.ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
                else -> return bmp
            }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (bmp != rotated) bmp.recycle()
            rotated
        } catch (e: Exception) {
            null
        } finally {
            stream?.close()
        }
    }

    private fun stampTextOnBitmap(bitmap: Bitmap, title: String, desc: String, loc: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val wmPrefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        val baseFontSize = wmPrefs.getFloat("wm_font_size", 30f)
        val useBgBox = wmPrefs.getBoolean("wm_use_bg", true)
        val textColorCode = wmPrefs.getInt("wm_color", Color.WHITE)
        val fontType = wmPrefs.getString("wm_font", "DEFAULT") ?: "DEFAULT"
        val baseMarginX = wmPrefs.getInt("wm_margin_x", 10)
        val baseMarginY = wmPrefs.getInt("wm_margin_y", 50)

        val scaleX = result.width / 1000f
        val scaleY = result.height / 800f
        val actualFontSize = baseFontSize * scaleX
        val actualMarginX = baseMarginX * scaleX
        val actualMarginY = baseMarginY * scaleY

        val typeface = when (fontType) {
            "SERIF" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            "MONOSPACE" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            "SANS_SERIF_LIGHT" -> Typeface.create("sans-serif-light", Typeface.BOLD)
            "SANS_SERIF_BLACK" -> Typeface.create("sans-serif-black", Typeface.BOLD)
            "CURSIVE" -> Typeface.create("cursive", Typeface.BOLD)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorCode
            textSize = actualFontSize
            this.typeface = typeface
            setShadowLayer(5f, 3f, 3f, Color.BLACK)
        }

        val lines = mutableListOf("제목 : $title")
        if (loc.isNotEmpty()) lines.add("위치 : $loc")
        if (desc.isNotEmpty()) lines.add("작업내용 : ${desc.replace("\n", " ")}")
        lines.add("날짜 : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        val fm = textPaint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val spacing = actualFontSize * 0.5f
        val totalH = lines.size * lineH + (lines.size - 1) * spacing
        val maxW = lines.maxOf { textPaint.measureText(it) }

        // anchorPosition: 0~8 → row=0~2, col=0~2
        val col = anchorPosition % 3
        val row = anchorPosition / 3

        val startX = when (col) {
            0 -> actualMarginX
            1 -> (result.width - maxW) / 2f
            else -> result.width - actualMarginX - maxW
        }
        val startY = when (row) {
            0 -> actualMarginY
            1 -> (result.height - totalH) / 2f
            else -> result.height - actualMarginY - totalH
        }

        val padding = actualFontSize * 0.4f

        if (useBgBox) {
            val alphaHex = ((bgOpacity / 100f) * 255).toInt().coerceIn(0, 255)
            val bgColor = Color.argb(alphaHex, 0, 0, 0)
            val bgPaint = Paint().apply { color = bgColor }
            canvas.drawRoundRect(
                RectF(startX - padding, startY - padding, startX + maxW + padding, startY + totalH + padding),
                10f * scaleX, 10f * scaleX, bgPaint
            )
        }

        var drawY = startY - fm.ascent
        for (line in lines) {
            canvas.drawText(line, startX, drawY, textPaint)
            drawY += lineH + spacing
        }
        return result
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, titleText: String): Uri? {
        val fileName = "${titleText}_batch_${System.currentTimeMillis()}.jpg"
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
        return uri
    }

    // ML Kit 개인정보 보호 (SubActivity와 동일 로직)
    private suspend fun detectAndBlurPrivacy(originalBitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        val blurred = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(blurred)
        try {
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(originalBitmap, 0)
            val faceDetector = com.google.mlkit.vision.face.FaceDetection.getClient(
                com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                    .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
            )
            val textRecognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions.Builder().build()
            )
            val faces = faceDetector.process(inputImage).await()
            val textResult = textRecognizer.process(inputImage).await()

            for (face in faces) blurBitmapArea(blurred, canvas, face.boundingBox)

            for (block in textResult.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.replace(" ", "")
                    val platePattern = "^(\\d{2,3}[가-힣]\\d{4})|([가-힣]{2}\\d{2}[가-힣]\\d{4})$".toRegex()
                    if (text.matches(platePattern) || text.length in 7..9) {
                        line.boundingBox?.let { blurBitmapArea(blurred, canvas, it) }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        blurred
    }

    private fun blurBitmapArea(bitmap: Bitmap, canvas: Canvas, rect: android.graphics.Rect) {
        try {
            val safeRect = android.graphics.Rect(
                rect.left.coerceAtLeast(0), rect.top.coerceAtLeast(0),
                rect.right.coerceAtMost(bitmap.width), rect.bottom.coerceAtMost(bitmap.height)
            )
            if (safeRect.width() <= 0 || safeRect.height() <= 0) return

            val region = Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
            val small = Bitmap.createScaledBitmap(region, (safeRect.width() / 15).coerceAtLeast(1), (safeRect.height() / 15).coerceAtLeast(1), false)
            val reScaled = Bitmap.createScaledBitmap(small, safeRect.width(), safeRect.height(), false)
            canvas.drawBitmap(reScaled, safeRect.left.toFloat(), safeRect.top.toFloat(), null)
            region.recycle(); small.recycle(); reScaled.recycle()
        } catch (e: Exception) {
            val p = Paint().apply { color = Color.argb(200, 0, 0, 0) }
            canvas.drawRect(android.graphics.RectF(rect), p)
        }
    }

}
