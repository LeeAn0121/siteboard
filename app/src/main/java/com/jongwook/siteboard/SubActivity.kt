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

        // ==========================================
        // 🚀 [추가] 위치 입력칸 강제 수정 불가 처리 (터치 및 키보드 입력 차단)
        // ==========================================
        binding.etLocation.isFocusable = false
        binding.etLocation.isFocusableInTouchMode = false
        binding.etLocation.isCursorVisible = false
        binding.etLocation.setOnClickListener {
            // 사용자가 터치했을 때 안내 메시지
            Toast.makeText(this, "위치는 GPS에 의해 자동 기록되며 임의로 수정할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
        // ==========================================

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

        // 💡 화면이 켜지자마자 위치를 가져오도록 onCreate 맨 마지막에 추가!
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
                }

                // 💡 [수정] 단순히 비트맵만 가져오지 않고, EXIF 정보를 기반으로 회전까지 완료된 비트맵을 가져옵니다!
                val orientedBitmap = loadOrientedBitmapFromUri(uri) ?: throw Exception("이미지 로드 실패")

                // 💡 [핵심] 이제 똑바로 서 있는 비트맵(orientedBitmap)에 워터마크를 새깁니다.
                val stampedBitmap = stampTextOnBitmap(orientedBitmap, title, desc, loc)

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

    // 💡 [추가] 꼬리표(EXIF) 정보를 읽어서, 필요하면 회전시켜서 똑바로 세워주는 함수
    private fun loadOrientedBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            // 1. 원본 비트맵 로드
            inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // 💡 2. 다시 파일을 열어서 EXIF 정보(방향 꼬리표) 읽기
            inputStream = contentResolver.openInputStream(uri)
            val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 안드로이드 7.0 이상
                android.media.ExifInterface(inputStream!!)
            } else {
                // 안드로이드 6.0 이하 (file:// 경로가 필요할 수 있음)
                // 현재 앱은 Q(10) 이상 타겟이므로 InputStream 방식만 있어도 무방합니다.
                null
            }
            inputStream?.close()

            // 💡 3. 방향 정보 알아내기
            val orientation = exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                ?: android.media.ExifInterface.ORIENTATION_NORMAL

            // 💡 4. 정보에 따라 회전시켜서 반환
            rotateBitmap(originalBitmap, orientation)

        } catch (e: Exception) {
            Log.e("SubActivity", "이미지 회전 실패", e)
            null
        } finally {
            inputStream?.close()
        }
    }

    // 💡 [추가] EXIF 정보에 따라 비트맵을 실제로 회전시키는 유틸 함수
    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)   // 시계 90도
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f) // 180도 (뒤집힘)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f) // 시계 270도
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f) // 좌우 반전
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)   // 상하 반전
            else -> return bitmap // 회전 필요 없음 (기본 가로 상태)
        }

        return try {
            // Matrix를 적용해 새로운 회전된 비트맵 생성
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            // 메모리 아끼기 위해 원본 누워있는 비트맵은 삭제
            if (bitmap != rotatedBitmap) bitmap.recycle()
            rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap // 실패 시 원본이라도 반환
        }
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
                // 🚀 [추가] 가짜 위치(Fake GPS) 방지 로직 적용
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
                            // 🚀 [추가] 가짜 위치(Fake GPS) 방지 로직 적용
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

    // 💡 [분리된 함수] 위도/경도를 한글 주소로 바꿔주는 역할만 전담
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SITEBOARD") // 폴더명 변경
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
        return uri
    }

    // 💡 [추가] 가짜 GPS(Mock Location) 사용 여부 확인 함수
    private fun isMockLocation(location: android.location.Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
    }
}