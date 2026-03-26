package com.jongwook.siteboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.jongwook.siteboard.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    private var selectedTheme = 0
    private lateinit var themePreviewViews: List<ImageView>

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        importFromUris(uris)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (20 * resources.displayMetrics.density).toInt())
            insets
        }

        db = AppDatabase.getDatabase(requireContext())
        val sharedPref = requireActivity().getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)

        // ─── 워터마크 테마 선택 ───────────────────────────────────────
        setupThemeSelector()

        // 워터마크 상세 설정 링크
        binding.btnWatermarkDetail.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), WatermarkSettingsActivity::class.java))
        }

        // 원본 삭제 옵션
        binding.switchDeleteOriginal.isChecked = sharedPref.getBoolean("delete_original_mode", false)
        binding.switchDeleteOriginal.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("delete_original_mode", isChecked).apply()
        }

        // GPS 수집 토글
        binding.switchGpsEnabled.isChecked = sharedPref.getBoolean("gps_enabled", true)
        binding.switchGpsEnabled.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("gps_enabled", isChecked).apply()
        }

        // ─── PDF 모드 ─────────────────────────────────────────────────
        binding.switchPdfMode.isChecked = sharedPref.getBoolean("pdf_multi_mode", false)
        binding.switchPdfMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("pdf_multi_mode", isChecked).apply()
        }

        // ─── 개인정보 마스킹 ──────────────────────────────────────────
        binding.switchPrivacyBlur.isChecked = sharedPref.getBoolean("privacy_blur_mode", true)
        binding.switchPrivacyBlur.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("privacy_blur_mode", isChecked).apply()
        }

        // ─── 갤러리에서 가져오기 ──────────────────────────────────────
        binding.btnImportFromGallery.setOnClickListener { confirmImportFromGallery() }

        // ─── CSV 내보내기 ─────────────────────────────────────────────
        binding.btnExportCsv.setOnClickListener { exportDataToCsv() }

        // ─── 현재 버전 표시 ────────────────────────────────────────────
        binding.tvAppVersion.text = BuildConfig.VERSION_NAME_FULL

        // ─── 업데이트 확인 ─────────────────────────────────────────────
        binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
    }

    // GitHub 레포의 version.properties를 직접 읽어 버전을 비교합니다.
    // 별도 릴리즈 없이 푸시만 해도 최신 버전이 반영됩니다.
    private fun checkForUpdate() {
        binding.tvUpdateStatus.text = "확인 중..."
        lifecycleScope.launch(Dispatchers.IO) {
            var errorMsg = ""
            val result = runCatching {
                val url = URL("https://raw.githubusercontent.com/LeeAn0121/siteboard/master/app/version.properties")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"

                val code = conn.responseCode
                if (code == 200) {
                    val props = java.util.Properties()
                    props.load(InputStreamReader(conn.inputStream))
                    val major = props.getProperty("VERSION_MAJOR", "1").toInt()
                    val minor = props.getProperty("VERSION_MINOR", "0").toInt()
                    val patch = props.getProperty("VERSION_PATCH", "0").toInt()
                    val build = props.getProperty("BUILD_NUMBER", "0").toInt()
                    "$major.$minor.$patch" to build
                } else {
                    errorMsg = "HTTP $code"
                    null
                }
            }.onFailure { errorMsg = it.javaClass.simpleName + ": " + it.message }.getOrNull()

            withContext(Dispatchers.Main) {
                if (result == null) {
                    binding.tvUpdateStatus.text = "확인 실패 ($errorMsg)"
                    return@withContext
                }
                val (latestVersion, latestBuild) = result
                val currentVersion = BuildConfig.VERSION_NAME
                val currentBuild = BuildConfig.BUILD_NUMBER

                if (latestBuild <= currentBuild) {
                    binding.tvUpdateStatus.text = "최신 버전입니다 ($currentVersion)"
                } else {
                    binding.tvUpdateStatus.text = "새 버전 $latestVersion (build $latestBuild) 이 있습니다!"
                    AlertDialog.Builder(requireContext())
                        .setTitle("업데이트 가능")
                        .setMessage("현재: $currentVersion (build $currentBuild)\n최신: $latestVersion (build $latestBuild)\n\n지금 GitHub 페이지로 이동하시겠습니까?")
                        .setPositiveButton("업데이트") { _, _ ->
                            val packageName = requireContext().packageName
                            try {
                                startActivity(android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("market://details?id=$packageName")
                                ))
                            } catch (e: android.content.ActivityNotFoundException) {
                                startActivity(android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                                ))
                            }
                        }
                        .setNegativeButton("나중에", null)
                        .show()
                }
            }
        }
    }

    // ─── 갤러리 가져오기 ─────────────────────────────────────────────────
    private fun confirmImportFromGallery() {
        pickImagesLauncher.launch(arrayOf("image/*"))
    }

    private fun importFromUris(uris: List<Uri>) {
        Toast.makeText(requireContext(), "${uris.size}장 분석 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cr = requireContext().applicationContext.contentResolver

                // DB에 이미 등록된 imageUri 목록 (중복 방지)
                val existingUris = db.postDao().getAllPosts().first()
                    .map { it.imageUri }.toSet()

                val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                var importedCount = 0
                var skippedCount = 0
                var failedCount = 0

                for (uri in uris) {
                    if (uri.toString() in existingUris) { skippedCount++; continue }

                    try {
                        // URI 영구 접근 권한 획득
                        try { cr.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}

                        val bitmap = android.graphics.BitmapFactory.decodeStream(
                            cr.openInputStream(uri)
                        ) ?: run { failedCount++; continue }

                        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                        bitmap.recycle()

                        val allLines = result.textBlocks
                            .flatMap { it.lines }
                            .map { it.text.trim() }

                        val parsed = parseWatermarkLines(allLines)

                        // 날짜: OCR 실패 시 현재 시각
                        val date = parsed["날짜"]?.ifEmpty { null }
                            ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                        db.postDao().insert(PostEntity(
                            title          = parsed["제목"] ?: "",
                            description    = parsed["작업내용"] ?: "",
                            location       = parsed["위치"]?.ifEmpty { null },
                            imageUri       = uri.toString(),
                            date           = date,
                            detailLocation = parsed["상세위치"]?.ifEmpty { null },
                            memo           = parsed["메모"]?.ifEmpty { null }
                        ))
                        importedCount++
                    } catch (e: Exception) {
                        failedCount++
                    }
                }

                recognizer.close()

                withContext(Dispatchers.Main) {
                    val parts = mutableListOf("${importedCount}개 가져옴")
                    if (skippedCount > 0) parts.add("${skippedCount}개 이미 등록됨")
                    if (failedCount > 0) parts.add("${failedCount}개 실패")
                    Toast.makeText(requireContext(), parts.joinToString(", "), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "가져오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 워터마크 텍스트 줄 목록에서 각 필드 추출
    // 형식: "제목 : value", "위치(GPS) : value", "상세위치 : value",
    //       "작업내용 : value", "메모 : value", "날짜 : value"
    private fun parseWatermarkLines(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) continue
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim()
            when {
                key.contains("제목")     -> result["제목"] = value
                key.contains("위치(GPS)") || key.contains("위치(gps)") || key == "위치" -> result["위치"] = value
                key.contains("상세위치")  -> result["상세위치"] = value
                key.contains("작업내용")  -> result["작업내용"] = value
                key.contains("메모")     -> result["메모"] = value
                key.contains("날짜")     -> result["날짜"] = value
            }
        }
        return result
    }

    // ─── 워터마크 테마 선택 로직 ──────────────────────────────────────────
    private fun setupThemeSelector() {
        themePreviewViews = listOf(
            binding.ivThemePreview0, binding.ivThemePreview1,
            binding.ivThemePreview2, binding.ivThemePreview3
        )
        val themeViews = listOf(
            binding.viewTheme0, binding.viewTheme1,
            binding.viewTheme2, binding.viewTheme3
        )
        val themeLabels = listOf(
            binding.tvThemeLabel0, binding.tvThemeLabel1,
            binding.tvThemeLabel2, binding.tvThemeLabel3
        )
        val themeLayouts = listOf(
            binding.layoutTheme0, binding.layoutTheme1,
            binding.layoutTheme2, binding.layoutTheme3
        )

        val prefs = requireActivity().getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        selectedTheme = prefs.getInt("wm_theme_index", 0)

        // 썸네일 렌더링
        themePreviewViews.forEachIndexed { index, imageView ->
            imageView.setImageBitmap(createThemePreviewBitmap(index))
        }
        updateThemeSelection(themeViews, themeLabels, selectedTheme)

        themeLayouts.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                selectedTheme = index
                updateThemeSelection(themeViews, themeLabels, index)
                applyThemePreset(index)
            }
        }
    }

    private fun updateThemeSelection(views: List<View>, labels: List<android.widget.TextView>, selectedIndex: Int) {
        val density = resources.displayMetrics.density
        val strokePx = (2 * density).toInt()
        views.forEachIndexed { index, view ->
            (view as MaterialCardView).strokeWidth = if (index == selectedIndex) strokePx else 0
        }
        labels.forEachIndexed { index, label ->
            label.setTextColor(
                if (index == selectedIndex) requireContext().getColor(R.color.orange_primary)
                else Color.parseColor("#A0A0A5")
            )
        }
    }

    private fun applyThemePreset(index: Int) {
        val prefs = requireActivity().getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE).edit()
        prefs.putInt("wm_theme_index", index)
        when (index) {
            0 -> { prefs.putBoolean("wm_use_bg", false); prefs.putInt("wm_color", Color.WHITE); prefs.putString("wm_font", "SANS_SERIF_LIGHT"); prefs.putFloat("wm_font_size", 28f) }
            1 -> { prefs.putBoolean("wm_use_bg", true);  prefs.putInt("wm_color", Color.YELLOW); prefs.putString("wm_font", "MONOSPACE"); prefs.putFloat("wm_font_size", 40f) }
            2 -> { prefs.putBoolean("wm_use_bg", true);  prefs.putInt("wm_color", Color.WHITE); prefs.putString("wm_font", "SERIF"); prefs.putFloat("wm_font_size", 36f) }
            3 -> { prefs.putBoolean("wm_use_bg", true);  prefs.putInt("wm_color", Color.YELLOW); prefs.putString("wm_font", "SANS_SERIF_BLACK"); prefs.putFloat("wm_font_size", 50f) }
        }
        prefs.apply()
        Toast.makeText(requireContext(), "테마가 변경되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun createThemePreviewBitmap(themeIndex: Int): Bitmap {
        val w = 240; val h = 240
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1A1A1E"))

        data class ThemeCfg(val color: Int, val font: String, val size: Float, val bg: Boolean)
        val themes = arrayOf(
            ThemeCfg(Color.WHITE,  "SANS_SERIF_LIGHT",  20f, false),
            ThemeCfg(Color.YELLOW, "MONOSPACE",          24f, true),
            ThemeCfg(Color.WHITE,  "SERIF",              22f, true),
            ThemeCfg(Color.YELLOW, "SANS_SERIF_BLACK",   28f, true)
        )
        val cfg = themes[themeIndex]
        val typeface = when (cfg.font) {
            "SERIF"            -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            "MONOSPACE"        -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            "SANS_SERIF_LIGHT" -> Typeface.create("sans-serif-light", Typeface.BOLD)
            "SANS_SERIF_BLACK" -> Typeface.create("sans-serif-black", Typeface.BOLD)
            else               -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cfg.color; textSize = cfg.size; this.typeface = typeface
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        val lines = listOf("현장명", "2026-03-25")
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val spacing = cfg.size * 0.4f
        val totalH = lines.size * lineH + (lines.size - 1) * spacing
        val maxW = lines.maxOf { paint.measureText(it) }
        val startX = 14f; val startY = h - 14f - totalH

        if (cfg.bg) {
            val pad = cfg.size * 0.3f
            val bgPaint = Paint().apply { color = Color.parseColor("#88000000") }
            canvas.drawRoundRect(RectF(startX - pad, startY - pad, startX + maxW + pad, startY + totalH + pad), 5f, 5f, bgPaint)
        }
        var y = startY - fm.ascent
        for (line in lines) { canvas.drawText(line, startX, y, paint); y += lineH + spacing }
        return bitmap
    }

    // ─── CSV 내보내기 ────────────────────────────────────────────────────
    private fun exportDataToCsv() {
        Toast.makeText(requireContext(), "데이터 추출 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val postList = db.postDao().getAllPosts().first()
                val csvData = StringBuilder()
                csvData.append("ID,현장명,작업내용,위치(GPS),상세위치,메모,날짜,원본파일명\n")
                for (post in postList) {
                    val title     = post.title.replace(",", " ")
                    val desc      = post.description.replace(",", " ").replace("\n", " ")
                    val loc       = post.location?.replace(",", " ") ?: ""
                    val detailLoc = post.detailLocation?.replace(",", " ") ?: ""
                    val memo      = post.memo?.replace(",", " ") ?: ""
                    val origName  = post.originalFileName ?: ""
                    csvData.append("${post.id},$title,$desc,$loc,$detailLoc,$memo,${post.date},$origName\n")
                }

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val siteboardDir = File(baseDir, "SITEBOARD")
                if (!siteboardDir.exists()) siteboardDir.mkdirs()
                val file = File(siteboardDir, "Siteboard_Data_$timeStamp.csv")
                FileWriter(file).use { it.write(csvData.toString()) }

                withContext(Dispatchers.Main) {
                    val snackbar = com.google.android.material.snackbar.Snackbar.make(
                        requireView(), "CSV(엑셀) 데이터가 저장되었습니다!",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )
                    snackbar.setAction("폴더 열기") {
                        var isOpened = false
                        try {
                            val intent = android.content.Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
                            intent.putExtra("samsung.myfiles.intent.extra.START_PATH", siteboardDir.absolutePath)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            if (intent.resolveActivity(requireContext().packageManager) != null) { startActivity(intent); isOpened = true }
                        } catch (e: Exception) { }
                        if (!isOpened) {
                            try {
                                startActivity(android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK })
                                android.widget.Toast.makeText(requireContext(), "목록에서 [SITEBOARD] 폴더를 확인하세요.", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(requireContext(), "파일 관리자 앱을 찾을 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    snackbar.setActionTextColor(android.graphics.Color.parseColor("#FF6F00"))
                    snackbar.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
