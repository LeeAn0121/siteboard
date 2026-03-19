package com.jongwook.siteboard

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

        // [추가] 기존 글 수정(Edit)으로 들어왔을 경우 데이터 채워넣기
        if (intent.hasExtra("edit_id")) {
            isEditMode = true
            editPostId = intent.getIntExtra("edit_id", 0)
            binding.etTitle.setText(intent.getStringExtra("edit_title"))
            binding.etDesc.setText(intent.getStringExtra("edit_desc"))
            binding.etLocation.setText(intent.getStringExtra("edit_loc"))
            Toast.makeText(this, "내용 수정 후 사진을 다시 촬영/선택하면 덮어쓰기 됩니다.", Toast.LENGTH_LONG).show()
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

    // 🌟 핵심: 이미지 합성 및 자동 저장 로직
    private fun processAndSaveImage(uri: Uri) {
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val loc = binding.etLocation.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val originalBitmap = loadBitmapFromUri(uri) ?: throw Exception("이미지 로드 실패")
                val stampedBitmap = stampTextOnBitmap(originalBitmap, title, desc, loc)
                val newSavedUri = saveBitmapToGallery(stampedBitmap, title) ?: throw Exception("저장 실패")

                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val post = PostEntity(
                    id = if (isEditMode) editPostId else 0, // 수정 모드면 기존 ID 덮어쓰기
                    title = title, description = desc, location = loc,
                    imageUri = newSavedUri.toString(), date = currentDate
                )

                // DB 저장 또는 덮어쓰기(업데이트)
                if (isEditMode) db.postDao().update(post) else db.postDao().insert(post)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, if(isEditMode) "수정 완료!" else "저장 완료!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish() // 완료 즉시 화면 닫고 메인으로!
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@SubActivity, "오류: ${e.message}", Toast.LENGTH_SHORT).show() }
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

    // 🌟 수정됨: 완전 좌측 끝, 최하단에 붙는 배경 박스와 텍스트 로직
    private fun stampTextOnBitmap(bitmap: Bitmap, title: String, desc: String, loc: String): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 글자 크기 (사진 높이의 3.5%)
        val calcSize = (resultBitmap.height * 0.035f).coerceAtLeast(30f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK // 검은 글씨
            textSize = calcSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 입력된 텍스트 목록 생성
        val lines = mutableListOf("제목: $title")
        if (desc.isNotEmpty()) lines.add("설명: $desc")
        if (loc.isNotEmpty()) lines.add("위치: $loc")
        lines.add("일시: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))

        // 텍스트 중 가장 긴 가로 길이 구하기
        var maxTextWidth = 0f
        for (line in lines) {
            val width = textPaint.measureText(line)
            if (width > maxTextWidth) maxTextWidth = width
        }

        // 박스 내부 여백 및 줄간격 설정
        val paddingX = calcSize * 0.8f // 좌우 여백
        val paddingY = calcSize * 0.8f // 상하 여백
        val lineSpacing = calcSize * 1.4f // 줄 간격
        val totalTextHeight = (lines.size - 1) * lineSpacing + calcSize // 전체 글자 높이

        // 💡 박스 좌표 계산 (좌측: 0, 하단: 사진의 끝)
        val bgBottom = resultBitmap.height.toFloat()
        val bgTop = bgBottom - totalTextHeight - (paddingY * 2)
        val bgRight = maxTextWidth + (paddingX * 2)

        // 반투명 흰색 배경 (투명도 160/255)
        val bgPaint = Paint().apply { color = Color.argb(160, 255, 255, 255) }

        // 꼼수: 왼쪽(-30f)과 아래(+30f)를 화면 밖으로 살짝 빼서 둥근 모서리를 잘라버립니다.
        // 이렇게 하면 왼쪽과 아래는 직각으로 벽에 딱 붙고, 우측 상단만 예쁘게 둥글어집니다.
        val bgRect = RectF(-30f, bgTop, bgRight, bgBottom + 30f)
        canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)

        // 텍스트 그리기 좌표 (첫 줄의 위치)
        // drawText는 글자의 아랫부분(Baseline)을 기준으로 그려지므로 약간의 높이 보정이 필요합니다.
        var textY = bgTop + paddingY + (calcSize * 0.85f)

        for (line in lines) {
            // 좌측 여백(paddingX)부터 글씨 시작
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