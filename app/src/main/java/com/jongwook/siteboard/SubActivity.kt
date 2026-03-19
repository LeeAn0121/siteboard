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

    // 권한 요청 (카메라)
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    // [수정] 카메라 촬영 결과 처리
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { isSuccess ->
        if (isSuccess) {
            currentPhotoUri?.let { uri ->
                try {
                    // 캐시에 저장된 임시 사진을 비트맵으로 불러와 화면에 강제 표출
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    binding.ivPreview.setImageBitmap(bitmap)
                    inputStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "사진 미리보기를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // 촬영 취소 시
            currentPhotoUri = null
        }
    }

    // 갤러리 선택
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            currentPhotoUri = it
            binding.ivPreview.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCamera.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val desc = binding.etDesc.text.toString().trim()
            val loc = binding.etLocation.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentPhotoUri == null) {
                Toast.makeText(this, "사진을 먼저 선택하거나 촬영해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 사진 합성 및 DB 저장 코루틴 실행
            processAndSaveImage(title, desc, loc)
        }
    }

    // [수정] 카메라 실행 로직 (FileProvider 캐시 방식)
    private fun launchCamera() {
        try {
            // 1. 앱 내부 캐시 폴더에 임시 파일 생성
            val tempFile = java.io.File(cacheDir, "temp_camera_image.jpg")

            // 2. FileProvider를 통해 안전한 URI 생성 (매니페스트에 등록된 authorities 사용)
            currentPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.jongwook.siteboard.fileprovider",
                tempFile
            )

            // 3. 카메라 앱 호출
            currentPhotoUri?.let { uri ->
                takePictureLauncher.launch(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "카메라를 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // (문제 2 해결) 사진에 텍스트를 합성하고 저장하는 메인 함수
    private fun processAndSaveImage(title: String, desc: String, loc: String) {
        binding.btnSave.isEnabled = false // 저장 중 중복 클릭 방지
        binding.btnSave.text = "저장 중..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 원본 이미지 불러오기
                val originalBitmap = loadBitmapFromUri(currentPhotoUri!!)
                if (originalBitmap == null) throw Exception("이미지를 불러올 수 없습니다.")

                // 2. 이미지 위에 텍스트 합성
                val stampedBitmap = stampTextOnBitmap(originalBitmap, title, desc, loc)

                // 3. 합성된 새 이미지를 갤러리(SITEBOARD 폴더)에 저장
                val newSavedUri = saveBitmapToGallery(stampedBitmap, title)
                if (newSavedUri == null) throw Exception("합성된 이미지 저장에 실패했습니다.")

                // 4. Room DB에 기록 (새로 생성된 이미지 경로 저장)
                val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                db.postDao().insert(
                    PostEntity(
                        title = title,
                        description = desc,         // 추가된 설명 파라미터 전달
                        location = loc,             // 추가된 위치 파라미터 전달
                        imageUri = newSavedUri.toString(), // imagePath 대신 변경된 imageUri 사용
                        date = currentDate
                    )
                )

                // 5. 원본이 방금 찍은 임시 카메라 사진이라면 원본은 삭제 (용량 확보)
                if (currentPhotoUri.toString().contains("SITEBOARD")) {
                    contentResolver.delete(currentPhotoUri!!, null, null)
                }

                // UI 업데이트 및 액티비티 종료
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, "성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SubActivity, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "SITEBOARD 저장"
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        } finally {
            inputStream?.close()
        }
    }

    // 비트맵에 텍스트를 그리는 핵심 로직
    private fun stampTextOnBitmap(bitmap: Bitmap, title: String, desc: String, loc: String): Bitmap {
        // 원본 이미지를 복사하여 수정 가능한 캔버스 생성
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 해상도에 맞춰 글자 크기 동적 설정 (사진 세로 길이의 약 3.5%)
        val calculatedTextSize = (resultBitmap.height * 0.035f).coerceAtLeast(30f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE // 글자색 흰색
            textSize = calculatedTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // 가독성을 위한 검은색 그림자 테두리 효과
            setShadowLayer(8f, 2f, 2f, Color.BLACK)
        }

        // 그릴 텍스트 목록 정리
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val linesToDraw = mutableListOf<String>()
        linesToDraw.add("제목: $title")
        if (desc.isNotEmpty()) linesToDraw.add("설명: $desc")
        if (loc.isNotEmpty()) linesToDraw.add("위치: $loc")
        linesToDraw.add("일시: $timeStamp")

        // 좌측 하단 시작 좌표 설정
        val padding = resultBitmap.width * 0.03f
        var startY = resultBitmap.height - padding - (linesToDraw.size * calculatedTextSize * 1.3f)

        // 한 줄씩 사진에 텍스트 각인
        for (line in linesToDraw) {
            startY += calculatedTextSize * 1.3f
            canvas.drawText(line, padding, startY, textPaint)
        }

        return resultBitmap
    }

    // 합성된 비트맵을 갤러리에 저장하는 로직
    private fun saveBitmapToGallery(bitmap: Bitmap, titleText: String): Uri? {
        val safeTitle = titleText.replace(Regex("[^a-zA-Z0-9가-힣]"), "_")
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD_Docs")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                // 화질 95%로 압축하여 저장
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
        }
        return uri
    }

    // --- 추가할 코드: 메모리 부족으로 앱이 죽어도 Uri를 기억하는 기능 ---
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 화면이 꺼지기 직전에 현재 Uri 값을 Bundle에 안전하게 보관합니다.
        currentPhotoUri?.let { outState.putString("photo_uri_backup", it.toString()) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // 화면이 다시 켜질 때 보관했던 Uri 값을 복구합니다.
        val backupUriString = savedInstanceState.getString("photo_uri_backup")
        if (backupUriString != null) {
            currentPhotoUri = Uri.parse(backupUriString)
        }
    }
}