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
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import kotlinx.coroutines.tasks.await // 💡 주의: 이 import가 빨간불이면 아래 가이드를 참고하세요!
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

        // ==========================================
        // 🚀 위치 입력칸 강제 수정 불가 처리 (터치 및 키보드 입력 차단)
        // ==========================================
        binding.etLocation.isFocusable = false
        binding.etLocation.isFocusableInTouchMode = false
        binding.etLocation.isCursorVisible = false
        binding.etLocation.setOnClickListener {
            Toast.makeText(this, "위치는 GPS에 의해 자동 기록되며 임의로 수정할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

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

        // 화면이 켜지자마자 위치를 가져오도록 호출
        fetchCurrentLocationAndAddress()
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
                    // 데이터 처리 중임을 알림
                    Toast.makeText(this@SubActivity, "보안 처리 및 이미지 저장 중...", Toast.LENGTH_SHORT).show()
                }

                // 1. 방향이 바로 잡힌 원본 비트맵 로드
                val orientedBitmap = loadOrientedBitmapFromUri(uri) ?: throw Exception("이미지 로드 실패")

                // 🛡️ 2. [추가된 핵심 로직] 워터마크 찍기 전에 개인정보 자동 블러 처리 실행
                val privacyBlurredBitmap = detectAndBlurPrivacy(orientedBitmap)

                // 메모리 아끼기 위해 블러 처리 후 필요 없어진 원본 삭제
                if (orientedBitmap != privacyBlurredBitmap && !orientedBitmap.isRecycled) {
                    orientedBitmap.recycle()
                }

                // 3. [수정됨] 블러 처리가 완료된 비트맵 위에 워터마크를 새깁니다.
                val stampedBitmap = stampTextOnBitmap(privacyBlurredBitmap, title, desc, loc)

                // 워터마크 처리 후 필요 없어진 블러 이미지 삭제
                if (privacyBlurredBitmap != stampedBitmap && !privacyBlurredBitmap.isRecycled) {
                    privacyBlurredBitmap.recycle()
                }

                // 4. 갤러리에 저장
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
                    Toast.makeText(this@SubActivity, "현장 기록이 안전하게 저장되었습니다.", Toast.LENGTH_SHORT).show()

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

    // 💡 꼬리표(EXIF) 정보를 읽어서, 필요하면 회전시켜서 똑바로 세워주는 함수
    private fun loadOrientedBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            inputStream = contentResolver.openInputStream(uri)
            val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.media.ExifInterface(inputStream!!)
            } else null
            inputStream?.close()

            val orientation = exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                ?: android.media.ExifInterface.ORIENTATION_NORMAL

            rotateBitmap(originalBitmap, orientation)
        } catch (e: Exception) {
            Log.e("SubActivity", "이미지 회전 실패", e)
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            else -> return bitmap
        }

        return try {
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (bitmap != rotatedBitmap) bitmap.recycle()
            rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    // 🌟 100% 싱크로율 보장: 미리보기와 완벽히 똑같은 여백과 비율로 도장 찍기
    private fun stampTextOnBitmap(bitmap: Bitmap, title: String, desc: String, loc: String): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        val isTop = prefs.getBoolean("wm_is_top", false)
        val isLeft = prefs.getBoolean("wm_is_left", true)
        val baseMarginX = prefs.getInt("wm_margin_x", 50)
        val baseMarginY = prefs.getInt("wm_margin_y", 50)
        val baseFontSize = prefs.getFloat("wm_font_size", 40f)
        val useBgBox = prefs.getBoolean("wm_use_bg", true)
        val textColorCode = prefs.getInt("wm_color",  Color.YELLOW)
        val fontType = prefs.getString("wm_font", "DEFAULT")

        val scaleX = resultBitmap.width / 1000f
        val scaleY = resultBitmap.height / 800f

        val actualFontSize = baseFontSize * scaleX
        val actualMarginX = baseMarginX * scaleX
        val actualMarginY = baseMarginY * scaleY

        val lines = mutableListOf("제목 : $title")
        if (loc.isNotEmpty()) lines.add("위치 : $loc")
        if (desc.isNotEmpty()) lines.add("작업내용 : ${desc.replace("\n", " ")}")
        lines.add("날짜 : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

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

        val fm = textPaint.fontMetrics
        val singleTextHeight = fm.descent - fm.ascent
        val lineSpacing = actualFontSize * 0.5f
        val totalHeight = (lines.size * singleTextHeight) + ((lines.size - 1) * lineSpacing)
        val maxTextWidth = lines.maxOf { textPaint.measureText(it) }

        val startX = if (isLeft) actualMarginX else resultBitmap.width - actualMarginX - maxTextWidth
        val startY = if (isTop) actualMarginY else resultBitmap.height - actualMarginY - totalHeight

        val padding = actualFontSize * 0.4f

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

        var textDrawY = startY - fm.ascent
        for (line in lines) {
            canvas.drawText(line, startX, textDrawY, textPaint)
            textDrawY += (singleTextHeight + lineSpacing)
        }

        return resultBitmap
    }

    // 🌟 [완벽 방어 적용] GPS로 현재 위치를 잡아 한글 주소로 변환하는 로직
    private fun fetchCurrentLocationAndAddress() {
        if (isEditMode) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "위치 권한이 없어 주소를 자동으로 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.etLocation.setText("📍 현재 위치를 찾는 중...")

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                if (isMockLocation(lastLoc)) {
                    Toast.makeText(this, "가짜 위치(Fake GPS) 앱 사용이 감지되었습니다.", Toast.LENGTH_LONG).show()
                    binding.etLocation.setText("위치 조작 감지됨")
                } else {
                    convertLocationToAddress(lastLoc.latitude, lastLoc.longitude)
                }
            } else {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { currentLoc ->
                        if (currentLoc != null) {
                            if (isMockLocation(currentLoc)) {
                                Toast.makeText(this, "가짜 위치(Fake GPS) 앱 사용이 감지되었습니다.", Toast.LENGTH_LONG).show()
                                binding.etLocation.setText("위치 조작 감지됨")
                            } else {
                                convertLocationToAddress(currentLoc.latitude, currentLoc.longitude)
                            }
                        } else {
                            binding.etLocation.setText("")
                            Toast.makeText(this, "GPS 신호가 약해 주소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { binding.etLocation.setText("") }
            }
        }.addOnFailureListener { binding.etLocation.setText("") }
    }

    private fun convertLocationToAddress(lat: Double, lng: Double) {
        val geocoder = Geocoder(this, Locale.KOREAN)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        if (addresses.isNotEmpty()) {
                            val cleanAddress = addresses[0].getAddressLine(0).replace("대한민국 ", "")
                            runOnUiThread { binding.etLocation.setText(cleanAddress) }
                        } else {
                            runOnUiThread { binding.etLocation.setText("") }
                        }
                    }
                    override fun onError(errorMessage: String?) {
                        runOnUiThread { binding.etLocation.setText("") }
                    }
                })
            } else {
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val cleanAddress = addresses[0].getAddressLine(0).replace("대한민국 ", "")
                    binding.etLocation.setText(cleanAddress)
                } else {
                    binding.etLocation.setText("")
                }
            }
        } catch (e: Exception) {
            Log.e("SubActivity", "주소 변환 실패", e)
            runOnUiThread { binding.etLocation.setText("") }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, titleText: String): Uri? {
        val fileName = "${titleText}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
        return uri
    }

    private fun isMockLocation(location: android.location.Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
    }

    // =============================================================================================
    // 🛡️ [ML Kit 개인정보 보호] 핵심 이미지 처리 함수들
    // =============================================================================================

    private suspend fun detectAndBlurPrivacy(originalBitmap: Bitmap): Bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val blurredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(blurredBitmap)
        val imageForMlKit = InputImage.fromBitmap(originalBitmap, 0)

        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val faceDetector = FaceDetection.getClient(faceOptions)

        val textRecognizer = TextRecognition.getClient(com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions.Builder().build())

        try {
            // 💡 await() 함수를 사용해 비동기 작업을 코루틴에서 기다립니다.
            val faces = faceDetector.process(imageForMlKit).await()
            val textResult = textRecognizer.process(imageForMlKit).await()

            // 얼굴 블러
            for (face in faces) {
                blurBitmapArea(blurredBitmap, canvas, face.boundingBox)
            }

            // 번호판 블러
            for (block in textResult.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.replace(" ", "")
                    val platePattern = "^(\\d{2,3}[가-힣]\\d{4})|([가-힣]{2}\\d{2}[가-힣]\\d{4})$".toRegex()

                    if (text.matches(platePattern) || text.length in 7..9) {
                        blurBitmapArea(blurredBitmap, canvas, line.boundingBox)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SubActivity", "개인정보 감지 실패: ${e.message}")
            return@withContext originalBitmap
        } finally {
            faceDetector.close()
            textRecognizer.close()
        }

        return@withContext blurredBitmap
    }

    /**
     * 옵션 1: 형체를 알아볼 수 없도록 아주 강한 모자이크(픽셀화) 처리
     */
    private fun blurBitmapArea(bitmap: Bitmap, canvas: Canvas, bounds: Rect?) {
        if (bounds == null) return

        val safeLeft = Math.max(0, bounds.left)
        val safeTop = Math.max(0, bounds.top)
        val safeWidth = Math.min(bounds.width(), bitmap.width - safeLeft)
        val safeHeight = Math.min(bounds.height(), bitmap.height - safeTop)

        if (safeWidth <= 0 || safeHeight <= 0) return

        val croppedBitmap = Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)

        // 🚀 [수정됨] 흐림 강도 대폭 증가 (숫자가 클수록 입자가 커지고 형체를 알아볼 수 없게 됨)
        // 기존 8 -> 25 로 변경. (더 강하게 원하시면 30~40으로 올리셔도 됩니다)
        val blurScale = 25
        val scaledWidth = Math.max(1, safeWidth / blurScale)
        val scaledHeight = Math.max(1, safeHeight / blurScale)

        val smallBitmap = Bitmap.createScaledBitmap(croppedBitmap, scaledWidth, scaledHeight, false)
        val finalBlurredChunk = Bitmap.createScaledBitmap(smallBitmap, safeWidth, safeHeight, true)

        canvas.drawBitmap(finalBlurredChunk, safeLeft.toFloat(), safeTop.toFloat(), null)

        croppedBitmap.recycle()
        smallBitmap.recycle()
        finalBlurredChunk.recycle()
    }
}