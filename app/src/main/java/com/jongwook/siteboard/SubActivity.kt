package com.jongwook.siteboard

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
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

    private var isEditMode = false
    private var editPostId = 0

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

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

        if (intent.hasExtra("edit_id")) {
            isEditMode = true
            editPostId = intent.getIntExtra("edit_id", 0)
            binding.etTitle.setText(intent.getStringExtra("edit_title"))
            binding.etDesc.setText(intent.getStringExtra("edit_desc"))
            binding.etLocation.setText(intent.getStringExtra("edit_loc"))
            Toast.makeText(this, "내용을 수정한 후 사진을 다시 촬영/선택하면 워터마크가 변경됩니다.", Toast.LENGTH_LONG).show()
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

    private fun processAndSaveImage(uri: Uri) {
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val loc = binding.etLocation.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.VISIBLE
                }

                val originalBitmap = loadBitmapFromUri(uri) ?: throw Exception("이미지 로드 실패")

                // 💡 [핵심] 이제 환경 설정값을 stampTextOnBitmap 내부에서 직접 불러옵니다!
                val stampedBitmap = stampTextOnBitmap(originalBitmap, title, desc, loc)

                val newSavedUri = saveBitmapToGallery(stampedBitmap, title) ?: throw Exception("저장 실패")

                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val post = PostEntity(
                    id = if (isEditMode) editPostId else 0,
                    title = title, description = desc, location = loc,
                    imageUri = newSavedUri.toString(), date = currentDate
                )

                if (isEditMode) db.postDao().update(post) else db.postDao().insert(post)

                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    Toast.makeText(this@SubActivity, "현장 기록이 저장되었습니다.", Toast.LENGTH_SHORT).show()

                    if (binding.switchContinuous.isChecked) {
                        currentPhotoUri = null
                        Toast.makeText(this@SubActivity, "다음 사진을 촬영하세요", Toast.LENGTH_SHORT).show()
                    } else {
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.layoutLoading.visibility = View.GONE
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

    // 🌟 100% 싱크로율 보장: 미리보기와 완벽히 똑같은 여백과 비율로 도장 찍기
    private fun stampTextOnBitmap(bitmap: Bitmap, title: String, desc: String, loc: String): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 1. 설정값 불러오기
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        val isTop = prefs.getBoolean("wm_is_top", false)
        val isLeft = prefs.getBoolean("wm_is_left", true)
        val baseMarginX = prefs.getInt("wm_margin_x", 50)
        val baseMarginY = prefs.getInt("wm_margin_y", 50)
        val baseFontSize = prefs.getFloat("wm_font_size", 40f)
        val useBgBox = prefs.getBoolean("wm_use_bg", true)
        val textColorCode = prefs.getInt("wm_color",  Color.YELLOW)
        val fontType = prefs.getString("wm_font", "DEFAULT")

        // 2. 가로/세로 각각의 해상도 스케일링 (1000x800 기준)
        // 이 계산법이 들어가야 사진 해상도가 달라도 여백이 정확하게 맞습니다!
        val scaleX = resultBitmap.width / 1000f
        val scaleY = resultBitmap.height / 800f

        val actualFontSize = baseFontSize * scaleX
        val actualMarginX = baseMarginX * scaleX
        val actualMarginY = baseMarginY * scaleY

        // 3. 텍스트 세팅
        val lines = mutableListOf("제목 : $title")
        if (loc.isNotEmpty()) lines.add("위치 : $loc")
        if (desc.isNotEmpty()) lines.add("작업내용 : ${desc.replace("\n", " ")}")
        lines.add("날짜 : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())) // 초 단위까지 추가!

        val typefaceSelection = when (fontType) {
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
            typeface = typefaceSelection
            setShadowLayer(5f, 3f, 3f, Color.BLACK)
        }

        // 4. 오차 없는 블록 높이/너비 계산 (미리보기와 동일 공식)
        val fm = textPaint.fontMetrics
        val singleTextHeight = fm.descent - fm.ascent
        val lineSpacing = actualFontSize * 0.5f
        val totalHeight = (lines.size * singleTextHeight) + ((lines.size - 1) * lineSpacing)
        val maxTextWidth = lines.maxOf { textPaint.measureText(it) }

        // 5. 시작점 X, Y 지정
        val startX = if (isLeft) actualMarginX else resultBitmap.width - actualMarginX - maxTextWidth
        val startY = if (isTop) actualMarginY else resultBitmap.height - actualMarginY - totalHeight

        val padding = actualFontSize * 0.4f

        // 6. 박스 그리기
        if (useBgBox) {
            val bgPaint = Paint().apply { color = Color.parseColor("#66000000") }
            val bgRect = RectF(
                startX - padding,
                startY - padding,
                startX + maxTextWidth + padding,
                startY + totalHeight + padding
            )
            canvas.drawRoundRect(bgRect, 10f * scaleX, 10f * scaleX, bgPaint)
        }

        // 7. 텍스트 그리기
        var textDrawY = startY - fm.ascent
        for (line in lines) {
            canvas.drawText(line, startX, textDrawY, textPaint)
            textDrawY += (singleTextHeight + lineSpacing)
        }

        return resultBitmap
    }


    private fun saveBitmapToGallery(bitmap: Bitmap, titleText: String): Uri? {
        val fileName = "${titleText}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/KOOLSAFE_Docs") // 폴더명 변경
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
        return uri
    }
}