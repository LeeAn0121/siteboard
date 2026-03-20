package com.jongwook.siteboard

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jongwook.siteboard.databinding.ActivityWatermarkSettingsBinding

class WatermarkSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWatermarkSettingsBinding

    // 워터마크 설정 변수들
    private var isTop = false
    private var isLeft = true
    private var marginX = 50
    private var marginY = 50
    private var fontSize = 40f
    private var useBgBox = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatermarkSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // 기존 설정 불러오기
        loadPreferences()

        // UI에 설정값 세팅하기
        setupUI()

        // 슬라이더(SeekBar) 조작 이벤트
        setupListeners()

        // 최초 미리보기 그리기
        updatePreview()

        // 저장 버튼
        binding.btnSaveSettings.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "워터마크 설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        isTop = prefs.getBoolean("wm_is_top", false) // 기본 하단
        isLeft = prefs.getBoolean("wm_is_left", true) // 기본 좌측
        marginX = prefs.getInt("wm_margin_x", 50)
        marginY = prefs.getInt("wm_margin_y", 50)
        fontSize = prefs.getFloat("wm_font_size", 40f)
        useBgBox = prefs.getBoolean("wm_use_bg", true)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE).edit()
        prefs.putBoolean("wm_is_top", isTop)
        prefs.putBoolean("wm_is_left", isLeft)
        prefs.putInt("wm_margin_x", marginX)
        prefs.putInt("wm_margin_y", marginY)
        prefs.putFloat("wm_font_size", fontSize)
        prefs.putBoolean("wm_use_bg", useBgBox)
        prefs.apply()
    }

    private fun setupUI() {
        if (isTop) binding.rbTop.isChecked = true else binding.rbBottom.isChecked = true
        if (isLeft) binding.rbLeft.isChecked = true else binding.rbRight.isChecked = true

        binding.sbMarginX.progress = marginX
        binding.sbMarginY.progress = marginY
        binding.sbFontSize.progress = fontSize.toInt()
        binding.switchBgBox.isChecked = useBgBox

        binding.tvMarginX.text = "가로 여백 (X): ${marginX}px"
        binding.tvMarginY.text = "세로 여백 (Y): ${marginY}px"
        binding.tvFontSize.text = "글씨 크기: ${fontSize.toInt()}pt"
    }

    private fun setupListeners() {
        binding.rgVertical.setOnCheckedChangeListener { _, checkedId ->
            isTop = checkedId == R.id.rbTop
            updatePreview()
        }
        binding.rgHorizontal.setOnCheckedChangeListener { _, checkedId ->
            isLeft = checkedId == R.id.rbLeft
            updatePreview()
        }
        binding.switchBgBox.setOnCheckedChangeListener { _, isChecked ->
            useBgBox = isChecked
            updatePreview()
        }

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                when (seekBar?.id) {
                    R.id.sbMarginX -> { marginX = progress; binding.tvMarginX.text = "가로 여백 (X): ${progress}px" }
                    R.id.sbMarginY -> { marginY = progress; binding.tvMarginY.text = "세로 여백 (Y): ${progress}px" }
                    R.id.sbFontSize -> {
                        // 글씨 크기가 0이 되지 않도록 최소값 설정
                        val actualSize = if (progress < 10) 10 else progress
                        fontSize = actualSize.toFloat()
                        binding.tvFontSize.text = "글씨 크기: ${actualSize}pt"
                    }
                }
                updatePreview() // 값 바뀔 때마다 다시 그리기!
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.sbMarginX.setOnSeekBarChangeListener(seekBarListener)
        binding.sbMarginY.setOnSeekBarChangeListener(seekBarListener)
        binding.sbFontSize.setOnSeekBarChangeListener(seekBarListener)
    }

    // 💡 캔버스를 이용해 미리보기 이미지에 직접 워터마크를 그리는 핵심 로직
    private fun updatePreview() {
        // 더미 배경 이미지(회색) 생성
        val width = 1000
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#888888")) // 회색 배경

        // 텍스트 설정
        val textPaint = Paint().apply {
            color = Color.YELLOW
            textSize = fontSize
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val text1 = "Project : 지하 2층 전기실"
        val text2 = "Date : 2026-03-20 16:34"

        // 텍스트가 차지하는 영역 계산
        val bounds = Rect()
        textPaint.getTextBounds(text1, 0, text1.length, bounds)
        val textHeight = bounds.height()
        val textWidth = Math.max(textPaint.measureText(text1), textPaint.measureText(text2))

        // 위치 계산 로직 (상단/하단, 좌측/우측)
        val drawX = if (isLeft) marginX.toFloat() else width - textWidth - marginX
        var drawY = if (isTop) marginY.toFloat() + textHeight else height - marginY.toFloat() - textHeight - 20f

        // 배경 박스 그리기
        if (useBgBox) {
            val bgPaint = Paint().apply { color = Color.parseColor("#66000000") } // 반투명 검정
            val padding = 10f
            val bgRect = RectF(
                drawX - padding,
                drawY - textHeight - padding,
                drawX + textWidth + padding,
                drawY + textHeight + 20f + padding
            )
            canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
        }

        // 텍스트 그리기
        canvas.drawText(text1, drawX, drawY, textPaint)
        canvas.drawText(text2, drawX, drawY + textHeight + 20f, textPaint)

        // 이미지뷰에 반영
        binding.ivPreview.setImageBitmap(bitmap)
    }
}