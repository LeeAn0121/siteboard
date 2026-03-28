package com.jongwook.siteboard

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.jongwook.siteboard.databinding.ActivitySubBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class SubActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubBinding
    private var currentPhotoUri: Uri? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    private var isEditMode = false
    private var editPostId = 0
    private var isCameraPhoto = false

    private var previewBitmap: Bitmap? = null
    private var currentLocation: String = ""

    // 수정 모드에서 사용할 기존 이미지 URI 정보
    private var editImageUri: String = ""
    private var editOriginalUri: String = ""
    private var editOriginalFileName: String = ""
    private var editPreviewIsOriginal = false

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) currentPhotoUri?.let { processAndSaveImage(it) }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            isCameraPhoto = false
            processAndSaveImage(it)
        }
    }

    private val pickMultipleImagesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            }
            val intent = android.content.Intent(this, BatchEditActivity::class.java)
            intent.putParcelableArrayListExtra("selected_uris", ArrayList(uris))
            intent.putExtra("edit_title", binding.etTitle.text.toString().trim())
            intent.putExtra("edit_desc", binding.etDesc.text.toString().trim())
            intent.putExtra("edit_loc", currentLocation)
            intent.putExtra("edit_detail_loc", binding.etDetailLocation.text.toString().trim())
            intent.putExtra("edit_memo", binding.etMemo.text.toString().trim())
            startActivity(intent)
        }
    }

    private val previewImagePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            lifecycleScope.launch(Dispatchers.IO) {
                val bmp = loadOrientedBitmapFromUri(it)
                withContext(Dispatchers.Main) { previewBitmap = bmp; updatePreview() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상단 시스템바 높이만큼 헤더에 패딩 추가
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutSubHeader) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (16 * resources.displayMetrics.density).toInt())
            insets
        }
        // 하단 네비게이션바 패딩
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottom) { v, insets ->
            val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = nb.bottom + (16 * resources.displayMetrics.density).toInt())
            insets
        }

        if (intent.hasExtra("edit_id")) {
            isEditMode = true
            editPostId = intent.getIntExtra("edit_id", 0)
            binding.etTitle.setText(intent.getStringExtra("edit_title"))
            binding.etDesc.setText(intent.getStringExtra("edit_desc"))
            binding.etDetailLocation.setText(intent.getStringExtra("edit_detail_loc") ?: "")
            binding.etMemo.setText(intent.getStringExtra("edit_memo") ?: "")
            currentLocation = intent.getStringExtra("edit_loc") ?: ""
            binding.tvGpsLocation.text = if (currentLocation.isEmpty()) "위치 정보 없음" else currentLocation

            // 기존 이미지 URI 저장
            editImageUri = intent.getStringExtra("edit_imageUri") ?: ""
            editOriginalUri = intent.getStringExtra("edit_originalUri") ?: ""
            editOriginalFileName = intent.getStringExtra("edit_originalFileName") ?: ""

            // 원본 사진(원본 URI) 우선 로드, 없으면 워터마크 이미지 사용
            val uriToLoad = if (editOriginalUri.isNotEmpty()) editOriginalUri else editImageUri
            editPreviewIsOriginal = editOriginalUri.isNotEmpty()
            if (uriToLoad.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    var bmp = try { loadOrientedBitmapFromUri(Uri.parse(uriToLoad)) } catch (e: Exception) { null }
                    // 원본 로드 실패 시 워터마크 이미지로 폴백
                    if (bmp == null && editOriginalUri.isNotEmpty() && editImageUri.isNotEmpty()) {
                        editPreviewIsOriginal = false
                        bmp = try { loadOrientedBitmapFromUri(Uri.parse(editImageUri)) } catch (e: Exception) { null }
                    }
                    withContext(Dispatchers.Main) {
                        if (bmp != null) { previewBitmap = bmp; updatePreview() }
                    }
                }
            }

            // 수정 모드: "현재 사진으로 저장" 버튼 표시
            binding.btnSaveKeepPhoto.visibility = View.VISIBLE
            binding.btnSaveKeepPhoto.setOnClickListener {
                if (validateInputs()) saveWithExistingPhoto()
            }
        } else {
            val prefs = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
            val savedSiteName = prefs.getString("last_site_name", "")
            if (!savedSiteName.isNullOrEmpty()) binding.etTitle.setText(savedSiteName)
            val savedWorkContent = prefs.getString("last_work_content", "")
            if (!savedWorkContent.isNullOrEmpty()) binding.etDesc.setText(savedWorkContent)
            val savedDetailLoc = prefs.getString("last_detail_loc", "")
            if (!savedDetailLoc.isNullOrEmpty()) binding.etDetailLocation.setText(savedDetailLoc)
            val prefillTitle = intent.getStringExtra(EXTRA_PREFILL_TITLE).orEmpty()
            if (prefillTitle.isNotBlank()) {
                binding.etTitle.setText(prefillTitle)
                binding.etTitle.setSelection(prefillTitle.length)
            }
        }

        setupClearButton(binding.etTitle)
        setupClearButton(binding.etDesc)
        setupClearButton(binding.etDetailLocation)
        setupClearButton(binding.etMemo)

        updatePreview()

        binding.cardPreview.setOnClickListener {
            previewImagePickerLauncher.launch(arrayOf("image/*"))
        }

        binding.btnDetailSettings.setOnClickListener {
            startActivity(android.content.Intent(this, WatermarkSettingsActivity::class.java))
        }

        binding.btnCamera.setOnClickListener {
            if (validateInputs()) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnGallery.setOnClickListener {
            if (validateInputs()) {
                isCameraPhoto = false
                pickImageLauncher.launch(arrayOf("image/*"))
            }
        }

        binding.btnBatch.setOnClickListener {
            if (validateInputs()) pickMultipleImagesLauncher.launch(arrayOf("image/*"))
        }

        applyGpsSetting()
        fetchCurrentLocationAndAddress()
    }

    private fun applyGpsSetting() {
        val gpsEnabled = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
            .getBoolean("gps_enabled", true)
        binding.layoutGpsSection.visibility = if (gpsEnabled) View.VISIBLE else View.GONE
        if (!gpsEnabled) {
            currentLocation = ""
        }
    }

    override fun onResume() {
        super.onResume()
        updatePreview()
    }

    private fun updatePreview() {
        binding.ivPreview.imageTintList = null
        if (previewBitmap != null) {
            binding.ivPreview.setImageBitmap(previewBitmap)
            binding.tvPreviewHint.visibility = View.GONE
        } else {
            binding.ivPreview.setImageDrawable(null)
            binding.tvPreviewHint.visibility = View.VISIBLE
        }
    }

    private fun resolveTypeface(fontType: String): Typeface = when (fontType) {
        "SERIF"            -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        "MONOSPACE"        -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        "SANS_SERIF_LIGHT" -> Typeface.create("sans-serif-light", Typeface.BOLD)
        "SANS_SERIF_BLACK" -> Typeface.create("sans-serif-black", Typeface.BOLD)
        "CURSIVE"          -> Typeface.create("cursive", Typeface.BOLD)
        else               -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun scaleBitmapCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        val dstRatio = targetW.toFloat() / targetH.toFloat()
        val scaledW: Int; val scaledH: Int
        if (srcRatio > dstRatio) { scaledH = targetH; scaledW = (targetH * srcRatio).toInt() }
        else { scaledW = targetW; scaledH = (targetW / srcRatio).toInt() }
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = (scaledW - targetW) / 2; val y = (scaledH - targetH) / 2
        val cropped = Bitmap.createBitmap(scaled, x, y, targetW, targetH)
        if (scaled != cropped) scaled.recycle()
        return cropped
    }

    private fun validateInputs(): Boolean {
        if (binding.etTitle.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "현장명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun launchCamera() {
        try {
            isCameraPhoto = true
            val tempFile = java.io.File(cacheDir, "temp_camera.jpg")
            currentPhotoUri = androidx.core.content.FileProvider.getUriForFile(this, "com.jongwook.siteboard.fileprovider", tempFile)
            currentPhotoUri?.let { takePictureLauncher.launch(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "카메라 실행 오류", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processAndSaveImage(uri: Uri) {
        val title     = binding.etTitle.text.toString().trim()
        val desc      = binding.etDesc.text.toString().trim()
        val loc       = currentLocation
        val detailLoc = binding.etDetailLocation.text.toString().trim()
        val memo      = binding.etMemo.text.toString().trim()
        // 원본 삭제 여부는 설정에서 읽어옴
        val shouldDeleteOriginal = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
            .getBoolean("delete_original_mode", false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.VISIBLE
                    Toast.makeText(this@SubActivity, "보안 처리 및 이미지 저장 중...", Toast.LENGTH_SHORT).show()
                }

                // 1. 원본 저장 (카메라 사진 → 갤러리 영구 보존)
                val originalUri: Uri?
                val originalFileName: String
                if (isCameraPhoto) {
                    originalUri = saveOriginalToGallery(uri, title)
                    originalFileName = originalUri?.let { getFileNameFromUri(it) } ?: ""
                } else {
                    originalUri = uri
                    originalFileName = getFileNameFromUri(uri)
                }

                // 2. 방향 보정 원본 비트맵 로드
                val orientedBitmap = loadOrientedBitmapFromUri(uri) ?: throw Exception("이미지 로드 실패")

                // 3. 개인정보 블러 처리
                val isPrivacyBlurEnabled = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
                    .getBoolean("privacy_blur_mode", true)
                val bitmapToStamp = if (isPrivacyBlurEnabled) {
                    val blurred = detectAndBlurPrivacy(orientedBitmap)
                    if (orientedBitmap != blurred && !orientedBitmap.isRecycled) orientedBitmap.recycle()
                    blurred
                } else orientedBitmap

                // 4. 워터마크 적용 (복사본에만)
                val stampedBitmap = stampTextOnBitmap(bitmapToStamp, title, desc, loc, detailLoc, memo)
                if (bitmapToStamp != stampedBitmap && !bitmapToStamp.isRecycled) bitmapToStamp.recycle()

                // 5. 워터마크 복사본 갤러리 저장
                val newSavedUri = saveBitmapToGallery(stampedBitmap, title) ?: throw Exception("저장 실패")
                if (!stampedBitmap.isRecycled) stampedBitmap.recycle()

                // 6. 원본 삭제 옵션 (사용자가 명시적 ON한 경우만)
                if (shouldDeleteOriginal && originalUri != null) {
                    try { contentResolver.delete(originalUri, null, null) } catch (e: Exception) { }
                }

                // 7. DB 저장
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val post = PostEntity(
                    id = if (isEditMode) editPostId else 0,
                    title = title, description = desc,
                    location = loc.ifEmpty { null },
                    imageUri = newSavedUri.toString(), date = currentDate,
                    detailLocation = detailLoc.ifEmpty { null },
                    memo = memo.ifEmpty { null },
                    originalUri = if (shouldDeleteOriginal) null else originalUri?.toString(),
                    originalFileName = originalFileName.ifEmpty { null }
                )
                if (isEditMode) db.postDao().update(post) else db.postDao().insert(post)
                AppDatabase.backupNow(applicationContext)
                SiteboardWidgetManager.refreshAll(applicationContext)

                getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE).edit()
                    .putString("last_site_name", title)
                    .putString("last_work_content", desc)
                    .putString("last_detail_loc", detailLoc)
                    .apply()

                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    Toast.makeText(this@SubActivity, "현장 기록이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.layoutLoading.visibility = View.GONE
                }
            }
        }
    }

    // 수정 모드: 새 사진 없이 현재 미리보기 사진으로 워터마크를 새로 찍어 저장
    private fun saveWithExistingPhoto() {
        val bmp = previewBitmap
        if (bmp == null) {
            Toast.makeText(this, "미리보기 사진이 없습니다. 사진을 촬영하거나 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val title     = binding.etTitle.text.toString().trim()
        val desc      = binding.etDesc.text.toString().trim()
        val loc       = currentLocation
        val detailLoc = binding.etDetailLocation.text.toString().trim()
        val memo      = binding.etMemo.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.VISIBLE
                }
                // 원본 사진인 경우 개인정보 블러 재적용, 이미 워터마크된 이미지면 건너뜀
                val isPrivacyBlurEnabled = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
                    .getBoolean("privacy_blur_mode", true)
                val bitmapToStamp = if (editPreviewIsOriginal && isPrivacyBlurEnabled) {
                    val blurred = detectAndBlurPrivacy(bmp)
                    if (bmp != blurred && !bmp.isRecycled) bmp.recycle()
                    blurred
                } else bmp

                // 워터마크 적용 후 갤러리 저장
                val stampedBitmap = stampTextOnBitmap(bitmapToStamp, title, desc, loc, detailLoc, memo)
                if (bitmapToStamp != stampedBitmap && !bitmapToStamp.isRecycled) bitmapToStamp.recycle()

                val newSavedUri = saveBitmapToGallery(stampedBitmap, title) ?: throw Exception("저장 실패")
                if (!stampedBitmap.isRecycled) stampedBitmap.recycle()

                // 기존 워터마크 이미지 갤러리에서 삭제
                if (editImageUri.isNotEmpty()) {
                    try { contentResolver.delete(Uri.parse(editImageUri), null, null) } catch (e: Exception) { }
                }

                // DB 업데이트
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val post = PostEntity(
                    id = editPostId,
                    title = title, description = desc,
                    location = loc.ifEmpty { null },
                    imageUri = newSavedUri.toString(), date = currentDate,
                    detailLocation = detailLoc.ifEmpty { null },
                    memo = memo.ifEmpty { null },
                    originalUri = editOriginalUri.ifEmpty { null },
                    originalFileName = editOriginalFileName.ifEmpty { null }
                )
                db.postDao().update(post)
                AppDatabase.backupNow(applicationContext)
                SiteboardWidgetManager.refreshAll(applicationContext)

                getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE).edit()
                    .putString("last_site_name", title)
                    .putString("last_work_content", desc)
                    .putString("last_detail_loc", detailLoc)
                    .apply()

                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    Toast.makeText(this@SubActivity, "현장 기록이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.layoutLoading.visibility = View.GONE
                }
            }
        }
    }

    private fun saveOriginalToGallery(srcUri: Uri, titleText: String): Uri? {
        return try {
            val fileName = "ORIG_${titleText}_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD_ORIGINALS")
            }
            val destUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            destUri?.let { dest ->
                contentResolver.openInputStream(srcUri)?.use { input ->
                    contentResolver.openOutputStream(dest)?.use { out -> input.copyTo(out) }
                }
            }
            destUri
        } catch (e: Exception) { Log.e("SubActivity", "원본 저장 실패", e); null }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return when (uri.scheme) {
            "content" -> contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else ""
                } else ""
            } ?: ""
            "file" -> java.io.File(uri.path ?: "").name
            else -> ""
        }
    }

    private fun loadOrientedBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (originalBitmap == null) return null
            inputStream = contentResolver.openInputStream(uri)
            val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                android.media.ExifInterface(inputStream!!) else null
            inputStream?.close()
            val orientation = exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL) ?: android.media.ExifInterface.ORIENTATION_NORMAL
            rotateBitmap(originalBitmap, orientation)
        } catch (e: Exception) { Log.e("SubActivity", "이미지 회전 실패", e); null }
        finally { inputStream?.close() }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90  -> matrix.setRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.setScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (bitmap != rotated) bitmap.recycle()
            rotated
        } catch (e: Exception) { e.printStackTrace(); bitmap }
    }

    private fun stampTextOnBitmap(
        bitmap: Bitmap, title: String, desc: String, loc: String,
        detailLoc: String = "", memo: String = ""
    ): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        val isTop        = prefs.getBoolean("wm_is_top", false)
        val isLeft       = prefs.getBoolean("wm_is_left", true)
        val baseMarginX  = prefs.getInt("wm_margin_x", 10)
        val baseMarginY  = prefs.getInt("wm_margin_y", 50)
        val baseFontSize = prefs.getFloat("wm_font_size", 30f)
        val useBgBox     = prefs.getBoolean("wm_use_bg", true)
        val textColorCode= prefs.getInt("wm_color", Color.WHITE)
        val fontType     = prefs.getString("wm_font", "DEFAULT") ?: "DEFAULT"

        val scaleX = resultBitmap.width / 1000f
        val scaleY = resultBitmap.height / 800f
        val actualFontSize = baseFontSize * scaleX
        val actualMarginX  = baseMarginX * scaleX
        val actualMarginY  = baseMarginY * scaleY

        val lines = mutableListOf("제목 : $title")
        if (loc.isNotEmpty()) lines.add("위치(GPS) : $loc")
        if (detailLoc.isNotEmpty()) lines.add("상세위치 : $detailLoc")
        if (desc.isNotEmpty()) lines.add("작업내용 : ${desc.replace("\n", " ")}")
        if (memo.isNotEmpty()) lines.add("메모 : $memo")
        lines.add("날짜 : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorCode; textSize = actualFontSize
            typeface = resolveTypeface(fontType)
            setShadowLayer(5f, 3f, 3f, Color.BLACK)
        }
        val fm = textPaint.fontMetrics
        val singleH   = fm.descent - fm.ascent
        val spacing   = actualFontSize * 0.5f
        val totalH    = lines.size * singleH + (lines.size - 1) * spacing
        val maxW      = lines.maxOf { textPaint.measureText(it) }
        val startX    = if (isLeft) actualMarginX else resultBitmap.width - actualMarginX - maxW
        val startY    = if (isTop) actualMarginY else resultBitmap.height - actualMarginY - totalH
        val padding   = actualFontSize * 0.4f

        if (useBgBox) {
            val bgPaint = Paint().apply { color = Color.parseColor("#66000000") }
            canvas.drawRoundRect(RectF(startX - padding, startY - padding,
                startX + maxW + padding, startY + totalH + padding), 10f * scaleX, 10f * scaleX, bgPaint)
        }
        var textDrawY = startY - fm.ascent
        for (line in lines) { canvas.drawText(line, startX, textDrawY, textPaint); textDrawY += singleH + spacing }
        return resultBitmap
    }

    private fun fetchCurrentLocationAndAddress() {
        if (isEditMode) return

        // 설정에서 GPS 수집이 꺼져 있으면 스킵
        val gpsEnabled = getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
            .getBoolean("gps_enabled", true)
        if (!gpsEnabled) {
            binding.tvGpsLocation.text = "GPS 수집 꺼짐 (설정에서 켜기)"
            currentLocation = ""
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            binding.tvGpsLocation.text = "위치 권한 없음"
            return
        }
        binding.tvGpsLocation.text = "위치를 가져오는 중..."
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                if (isMockLocation(lastLoc)) { setGpsLocation("위치 조작 감지됨"); Toast.makeText(this, "가짜 위치(Fake GPS) 앱 사용이 감지되었습니다.", Toast.LENGTH_LONG).show() }
                else convertLocationToAddress(lastLoc.latitude, lastLoc.longitude)
            } else {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            if (isMockLocation(loc)) setGpsLocation("위치 조작 감지됨")
                            else convertLocationToAddress(loc.latitude, loc.longitude)
                        } else setGpsLocation("GPS 신호 없음")
                    }.addOnFailureListener { setGpsLocation("GPS 오류") }
            }
        }.addOnFailureListener { setGpsLocation("GPS 오류") }
    }

    private fun setGpsLocation(loc: String) {
        currentLocation = loc
        binding.tvGpsLocation.text = loc
        updatePreview()
    }

    private fun convertLocationToAddress(lat: Double, lng: Double) {
        val geocoder = Geocoder(this, Locale.KOREAN)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        if (addresses.isNotEmpty()) {
                            val clean = addresses[0].getAddressLine(0).replace("대한민국 ", "")
                            runOnUiThread { setGpsLocation(clean) }
                        }
                    }
                    override fun onError(errorMessage: String?) { runOnUiThread { setGpsLocation("주소 변환 실패") } }
                })
            } else {
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    setGpsLocation(addresses[0].getAddressLine(0).replace("대한민국 ", ""))
                }
            }
        } catch (e: Exception) { Log.e("SubActivity", "주소 변환 실패", e) }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, titleText: String): Uri? {
        val fileName = "${titleText}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
        return uri
    }

    private fun isMockLocation(location: android.location.Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider

    private suspend fun detectAndBlurPrivacy(originalBitmap: Bitmap): Bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val blurredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(blurredBitmap)
        val imageForMlKit = InputImage.fromBitmap(originalBitmap, 0)
        val faceDetector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
        val textRecognizer = TextRecognition.getClient(com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions.Builder().build())
        try {
            val faces = faceDetector.process(imageForMlKit).await()
            val textResult = textRecognizer.process(imageForMlKit).await()
            for (face in faces) blurBitmapArea(blurredBitmap, canvas, face.boundingBox)
            for (block in textResult.textBlocks) for (line in block.lines) {
                val text = line.text.replace(" ", "")
                val platePattern = "^(\\d{2,3}[가-힣]\\d{4})|([가-힣]{2}\\d{2}[가-힣]\\d{4})$".toRegex()
                if (text.matches(platePattern) || text.length in 7..9) blurBitmapArea(blurredBitmap, canvas, line.boundingBox)
            }
        } catch (e: Exception) {
            Log.e("SubActivity", "개인정보 감지 실패: ${e.message}")
            return@withContext originalBitmap
        } finally { faceDetector.close(); textRecognizer.close() }
        blurredBitmap
    }

    private fun setupClearButton(et: EditText) {
        val clearIcon = ContextCompat.getDrawable(this, R.drawable.ic_clear_text)
        et.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()

        fun sync() {
            val icon = if (et.hasFocus() && et.text.isNotEmpty()) clearIcon else null
            et.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null)
        }

        et.setOnFocusChangeListener { _, _ -> sync() }
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = sync()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        et.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val d = (v as EditText).compoundDrawablesRelative[2]
                if (d != null && event.x >= v.width - v.paddingEnd - d.intrinsicWidth) {
                    v.text.clear()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun blurBitmapArea(bitmap: Bitmap, canvas: Canvas, bounds: Rect?) {
        if (bounds == null) return
        val sl = maxOf(0, bounds.left); val st = maxOf(0, bounds.top)
        val sw = minOf(bounds.width(), bitmap.width - sl); val sh = minOf(bounds.height(), bitmap.height - st)
        if (sw <= 0 || sh <= 0) return
        val cropped = Bitmap.createBitmap(bitmap, sl, st, sw, sh)
        val scale = 50
        val small = Bitmap.createScaledBitmap(cropped, maxOf(1, sw / scale), maxOf(1, sh / scale), false)
        val final_ = Bitmap.createScaledBitmap(small, sw, sh, false)
        canvas.drawBitmap(final_, sl.toFloat(), st.toFloat(), null)
        cropped.recycle(); small.recycle(); final_.recycle()
    }

    companion object {
        const val EXTRA_PREFILL_TITLE = "prefill_title"
    }
}
