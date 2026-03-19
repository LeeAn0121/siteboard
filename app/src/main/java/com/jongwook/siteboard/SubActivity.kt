package com.jongwook.siteboard

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivitySubBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class SubActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubBinding
    private var currentPhotoUri: Uri? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    // [추가] 수정 모드 판별 변수
    private var isEditMode = false
    private var editPostId = 0

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    // [변경] 사진을 찍거나 고르면 바로 processAndSaveImage() 호출하여 자동 저장!
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) currentPhotoUri?.let { processAndSaveImage(it) }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            processAndSaveImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // GPS 버튼 이벤트 관련 코드는 삭제되었습니다.

        // [수정됨] 수정(Edit)으로 들어왔을 경우
        if (intent.hasExtra("edit_id")) {
            isEditMode = true
            editPostId = intent.getIntExtra("edit_id", 0)
            binding.etTitle.setText(intent.getStringExtra("edit_title"))
            binding.etDesc.setText(intent.getStringExtra("edit_desc"))
            binding.etLocation.setText(intent.getStringExtra("edit_loc"))

            // 사진 첨부 및 테마 관련 UI 싹 다 숨기기
            binding.tvThemeLabel.visibility = android.view.View.GONE
            binding.rgTheme.visibility = android.view.View.GONE
            binding.tvPhotoLabel.visibility = android.view.View.GONE
            binding.layoutPhotoButtons.visibility = android.view.View.GONE

            // 텍스트 수정 전용 버튼 띄우기
            binding.btnEditSave.visibility = android.view.View.VISIBLE

            Toast.makeText(this, "앱 내부의 텍스트 정보만 수정됩니다. (사진 워터마크 변경 불가)", Toast.LENGTH_LONG).show()
        }

        binding.btnCamera.setOnClickListener {
            if (validateInputs()) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnGallery.setOnClickListener {
            if (validateInputs()) pickImageLauncher.launch(arrayOf("image/*"))
        }

        // [추가됨] 수정 완료 버튼 눌렀을 때의 동작
        binding.btnEditSave.setOnClickListener {
            if (validateInputs()) {
                updateTextOnly()
            }
        }
    }

    // [추가됨] 사진 변경 없이 DB의 텍스트 정보만 업데이트하는 함수
    private fun updateTextOnly() {
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val loc = binding.etLocation.text.toString().trim()

        // DetailActivity에서 넘겨준 원본 사진 경로와 날짜를 그대로 받아서 씁니다.
        val originalImageUri = intent.getStringExtra("edit_imageUri") ?: ""
        val originalDate = intent.getStringExtra("edit_date") ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            val updatedPost = PostEntity(
                id = editPostId,
                title = title,
                description = desc,
                location = loc,
                imageUri = originalImageUri,
                date = originalDate
            )

            db.postDao().update(updatedPost)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@SubActivity, "정보가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (binding.etTitle.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun launchCamera() {
        try {
            val tempFile = java.io.File(cacheDir, "temp_camera.jpg")
            currentPhotoUri = androidx.core.content.FileProvider.getUriForFile(this, "com.jongwook.siteboard.fileprovider", tempFile)
            currentPhotoUri?.let { takePictureLauncher.launch(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "카메라 실행 오류", Toast.LENGTH_SHORT).show()
        }
    }

    // 이미지 합성 및 자동 저장 로직 (로딩 스피너 적용 및 테마 값 전달)
    private fun processAndSaveImage(uri: Uri) {
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val loc = binding.etLocation.text.toString().trim()

        // 라디오 버튼 상태 확인 (어두운 테마인지?)
        val isDarkTheme = binding.rbDark.isChecked

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // UI 스레드에서 로딩 창 띄우기
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = android.view.View.VISIBLE
                }

                val originalBitmap = loadBitmapFromUri(uri) ?: throw Exception("이미지 로드 실패")
                // 테마 변수(isDarkTheme) 추가 전달
                val stampedBitmap = stampTextOnBitmap(originalBitmap, title, desc, loc, isDarkTheme)
                val newSavedUri = saveBitmapToGallery(stampedBitmap, title) ?: throw Exception("저장 실패")

                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val post = PostEntity(
                    id = if (isEditMode) editPostId else 0,
                    title = title, description = desc, location = loc,
                    imageUri = newSavedUri.toString(), date = currentDate
                )

                if (isEditMode) db.postDao().update(post) else db.postDao().insert(post)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, if(isEditMode) "수정 완료!" else "저장 완료!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.layoutLoading.visibility = android.view.View.GONE // 에러 시 로딩 숨김
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } finally { inputStream?.close() }
    }

    // 🌟 수정됨: 완전 좌측 끝, 최하단에 붙는 배경 박스와 텍스트 로직 (테마에 따른 색상 반전)
    private fun stampTextOnBitmap(bitmap: Bitmap, title: String, desc: String, loc: String, isDarkTheme: Boolean): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val calcSize = (resultBitmap.height * 0.035f).coerceAtLeast(30f)

        // ✨ 테마에 따른 글자색/배경색 결정
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
        val bgColor = if (isDarkTheme) Color.argb(160, 0, 0, 0) else Color.argb(160, 255, 255, 255)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = calcSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val lines = mutableListOf("제목: $title")
        if (desc.isNotEmpty()) lines.add("설명: $desc")
        if (loc.isNotEmpty()) lines.add("위치: $loc")
        lines.add("일시: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))

        var maxTextWidth = 0f
        for (line in lines) {
            val width = textPaint.measureText(line)
            if (width > maxTextWidth) maxTextWidth = width
        }

        val paddingX = calcSize * 0.8f
        val paddingY = calcSize * 0.8f
        val lineSpacing = calcSize * 1.4f
        val totalTextHeight = (lines.size - 1) * lineSpacing + calcSize

        val bgBottom = resultBitmap.height.toFloat()
        val bgTop = bgBottom - totalTextHeight - (paddingY * 2)
        val bgRight = maxTextWidth + (paddingX * 2)

        val bgPaint = Paint().apply { color = bgColor }
        val bgRect = RectF(-30f, bgTop, bgRight, bgBottom + 30f)
        canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)

        var textY = bgTop + paddingY + (calcSize * 0.85f)
        for (line in lines) {
            canvas.drawText(line, paddingX, textY, textPaint)
            textY += lineSpacing
        }

        return resultBitmap
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, titleText: String): Uri? {
        val fileName = "${titleText}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD_Docs")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
        return uri
    }
}