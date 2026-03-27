package com.jongwook.siteboard

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityWatermarkSettingsBinding

class WatermarkSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWatermarkSettingsBinding

    // 💡 기본 워터마크 설정 변수
    private var isTop = false
    private var isLeft = true
    private var marginX = 10
    private var marginY = 50
    private var fontSize = 30f
    private var useBgBox = true

    // 💡 추가된 커스텀 설정 변수 (색상, 폰트)
    private var textColor = Color.WHITE
    private var fontType = "DEFAULT"

    // 💡 스피너(드롭다운)에 보여질 폰트 목록과 실제 시스템 값
    private val fontNames = arrayOf("기본 고딕 (기본값)", "명조체 (Serif)", "모노스페이스 (Mono)", "고딕 얇게 (Light)", "고딕 아주 두껍게 (Black)", "필기체 느낌 (Cursive)")
    private val fontValues = arrayOf("DEFAULT", "SERIF", "MONOSPACE", "SANS_SERIF_LIGHT", "SANS_SERIF_BLACK", "CURSIVE")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatermarkSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        loadPreferences()
        setupUI()
        setupListeners()
        updatePreview()

        binding.btnSaveSettings.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "워터마크 설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        isTop = prefs.getBoolean("wm_is_top", false)
        isLeft = prefs.getBoolean("wm_is_left", true)
        marginX = prefs.getInt("wm_margin_x", 10)
        marginY = prefs.getInt("wm_margin_y", 50)
        fontSize = prefs.getFloat("wm_font_size", 30f)
        useBgBox = prefs.getBoolean("wm_use_bg", true)

        // 색상과 폰트도 불러옵니다 (없으면 기본값 흰색, 기본고딕)
        textColor = prefs.getInt("wm_color", Color.WHITE)
        fontType = prefs.getString("wm_font", "DEFAULT") ?: "DEFAULT"
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE).edit()
        prefs.putBoolean("wm_is_top", isTop)
        prefs.putBoolean("wm_is_left", isLeft)
        prefs.putInt("wm_margin_x", marginX)
        prefs.putInt("wm_margin_y", marginY)
        prefs.putFloat("wm_font_size", fontSize)
        prefs.putBoolean("wm_use_bg", useBgBox)

        // 색상과 폰트도 저장
        prefs.putInt("wm_color", textColor)
        prefs.putString("wm_font", fontType)
        prefs.apply()
    }

    private fun setupUI() {
        // 위치 및 배경박스 초기화
        if (isTop) binding.rbTop.isChecked = true else binding.rbBottom.isChecked = true
        if (isLeft) binding.rbLeft.isChecked = true else binding.rbRight.isChecked = true
        binding.switchBgBox.isChecked = useBgBox

        // 마진, 크기 슬라이더 초기화
        binding.sbMarginX.progress = marginX
        binding.etMarginX.setText(marginX.toString())

        binding.sbMarginY.progress = marginY
        binding.etMarginY.setText(marginY.toString())

        binding.sbFontSize.progress = fontSize.toInt()
        binding.etFontSize.setText(fontSize.toInt().toString())

        // 💡 폰트 드롭다운(Spinner) 세팅
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontNames)
        binding.spinnerFont.adapter = adapter
        val fontIndex = fontValues.indexOf(fontType)
        binding.spinnerFont.setSelection(if (fontIndex >= 0) fontIndex else 0)

        // 💡 RGB 색상 슬라이더 초기화
        val r = Color.red(textColor)
        val g = Color.green(textColor)
        val b = Color.blue(textColor)

        binding.sbRed.progress = r
        binding.sbGreen.progress = g
        binding.sbBlue.progress = b
        updateColorUI(r, g, b, true)
    }

    private fun setupListeners() {
        // 1. 라디오 & 스위치 버튼
        binding.rgVertical.setOnCheckedChangeListener { _, checkedId ->
            isTop = checkedId == R.id.rbTop; updatePreview()
        }
        binding.rgHorizontal.setOnCheckedChangeListener { _, checkedId ->
            isLeft = checkedId == R.id.rbLeft; updatePreview()
        }
        binding.switchBgBox.setOnCheckedChangeListener { _, isChecked ->
            useBgBox = isChecked; updatePreview()
        }

        // 2. 폰트 선택 드롭다운 리스너
        binding.spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fontType = fontValues[position]
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. RGB 슬라이더 조작 리스너
        val rgbListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { // 사용자가 직접 움직일 때만 작동
                    val r = binding.sbRed.progress
                    val g = binding.sbGreen.progress
                    val b = binding.sbBlue.progress
                    updateColorUI(r, g, b, true) // 색상 UI 및 HEX 코드 업데이트
                    updatePreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.sbRed.setOnSeekBarChangeListener(rgbListener)
        binding.sbGreen.setOnSeekBarChangeListener(rgbListener)
        binding.sbBlue.setOnSeekBarChangeListener(rgbListener)

        // 4. HEX 코드 텍스트 직접 입력 리스너
        binding.etHexColor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // 사용자가 키보드로 치고 있을 때만 작동 (무한루프 방지)
                if (s != null && s.length == 6 && binding.etHexColor.hasFocus()) {
                    try {
                        val parsedColor = Color.parseColor("#$s")
                        val r = Color.red(parsedColor)
                        val g = Color.green(parsedColor)
                        val b = Color.blue(parsedColor)

                        // 슬라이더 바 위치 업데이트
                        binding.sbRed.progress = r
                        binding.sbGreen.progress = g
                        binding.sbBlue.progress = b

                        updateColorUI(r, g, b, false) // false: EditText 내용은 덮어쓰지 않음
                        updatePreview()
                    } catch (e: Exception) {
                        // 잘못된 색상 코드(예: GGGGGG) 입력 시 무시
                    }
                }
            }
        })

        // 5. 마진 및 폰트 크기: 양방향 연동(SeekBar <-> EditText)
        setupTwoWayBinding(binding.sbMarginX, binding.etMarginX, 0, 300) { value -> marginX = value }
        setupTwoWayBinding(binding.sbMarginY, binding.etMarginY, 0, 300) { value -> marginY = value }
        setupTwoWayBinding(binding.sbFontSize, binding.etFontSize, 10, 100) { value -> fontSize = value.toFloat() }
    }

    // 💡 선택된 RGB 값으로 동그란 미리보기 색상 및 텍스트를 업데이트하는 함수
    private fun updateColorUI(r: Int, g: Int, b: Int, updateEditText: Boolean) {
        textColor = Color.rgb(r, g, b)
        binding.viewColorPreview.setBackgroundColor(textColor)

        binding.tvRed.text = "Red (빨강): $r"
        binding.tvGreen.text = "Green (초록): $g"
        binding.tvBlue.text = "Blue (파랑): $b"

        if (updateEditText) {
            val hex = String.format("%02X%02X%02X", r, g, b)
            binding.etHexColor.setText(hex)
        }
    }

    // 양방향 연동용 유틸 함수 (변경 없음)
    private fun setupTwoWayBinding(seekBar: SeekBar, editText: EditText, min: Int, max: Int, onValueChanged: (Int) -> Unit) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val actualValue = if (progress < min) min else progress
                    editText.setText(actualValue.toString())
                    editText.setSelection(editText.text.length)
                    onValueChanged(actualValue)
                    updatePreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty()) {
                    var value = input.toIntOrNull() ?: min
                    if (value > max) value = max
                    if (value < min) value = min

                    if (seekBar.progress != value) {
                        seekBar.progress = value
                        onValueChanged(value)
                        updatePreview()
                    }
                }
            }
        })
    }

    // 🌟 [핵심] 실제 카메라(SubActivity)와 100% 동일하게 그리는 절대 수학 공식 적용 🌟
    private fun updatePreview() {
        val width = 1000
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#888888")) // 회색 배경

        // 사용자가 선택한 폰트 적용
        val typefaceSelection = when (fontType) {
            "SERIF" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            "MONOSPACE" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            "SANS_SERIF_LIGHT" -> Typeface.create("sans-serif-light", Typeface.BOLD)
            "SANS_SERIF_BLACK" -> Typeface.create("sans-serif-black", Typeface.BOLD)
            "CURSIVE" -> Typeface.create("cursive", Typeface.BOLD)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = this@WatermarkSettingsActivity.fontSize
            typeface = typefaceSelection
            setShadowLayer(3f, 2f, 2f, Color.BLACK) // 글자 가독성 확보용 그림자
        }

        val lines = listOf("제목 : 지하 2층 전기실", "위치 : 서울시 강남구 역삼동", "날짜 : 2026-03-23 10:00:00")

        // 💡 [오차 0%] 안드로이드 FontMetrics를 이용한 정확한 글씨 높이 측정
        val fm = textPaint.fontMetrics
        val singleTextHeight = fm.descent - fm.ascent
        val lineSpacing = fontSize * 0.5f // 줄간격 (폰트 크기의 50%)

        // 전체 텍스트 블록의 절대 높이 계산
        val totalHeight = (lines.size * singleTextHeight) + ((lines.size - 1) * lineSpacing)
        val maxTextWidth = lines.maxOf { textPaint.measureText(it) }

        // 블록의 시작 위치 계산 (기준: 왼쪽 상단 모서리)
        val startX = if (isLeft) marginX.toFloat() else width - marginX.toFloat() - maxTextWidth
        val startY = if (isTop) marginY.toFloat() else height - marginY.toFloat() - totalHeight

        val padding = fontSize * 0.4f // 배경 박스 내부 여백

        // 배경 박스 그리기
        if (useBgBox) {
            val bgPaint = Paint().apply { color = Color.parseColor("#66000000") }
            val bgRect = RectF(
                startX - padding,
                startY - padding,
                startX + maxTextWidth + padding,
                startY + totalHeight + padding
            )
            canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
        }

        // 글씨 쓰기 (baseline인 ascent를 기준으로 Y좌표 조정)
        var textDrawY = startY - fm.ascent
        for (line in lines) {
            canvas.drawText(line, startX, textDrawY, textPaint)
            textDrawY += (singleTextHeight + lineSpacing)
        }

        binding.ivPreview.setImageBitmap(bitmap)
    }
}
