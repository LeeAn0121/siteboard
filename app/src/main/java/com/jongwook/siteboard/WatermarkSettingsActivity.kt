package com.jongwook.siteboard

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
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

        loadPreferences()
        setupUI()
        setupListeners()
        updatePreview()

        binding.btnSaveSettings.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "워터마크 설정이 완벽하게 저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("WatermarkPrefs", Context.MODE_PRIVATE)
        isTop = prefs.getBoolean("wm_is_top", false)
        isLeft = prefs.getBoolean("wm_is_left", true)
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
        binding.switchBgBox.isChecked = useBgBox

        // 초기 슬라이더 및 입력창 세팅
        binding.sbMarginX.progress = marginX
        binding.etMarginX.setText(marginX.toString())

        binding.sbMarginY.progress = marginY
        binding.etMarginY.setText(marginY.toString())

        binding.sbFontSize.progress = fontSize.toInt()
        binding.etFontSize.setText(fontSize.toInt().toString())
    }

    private fun setupListeners() {
        // 1. 라디오 & 스위치 버튼 리스너
        binding.rgVertical.setOnCheckedChangeListener { _, checkedId ->
            isTop = checkedId == R.id.rbTop; updatePreview()
        }
        binding.rgHorizontal.setOnCheckedChangeListener { _, checkedId ->
            isLeft = checkedId == R.id.rbLeft; updatePreview()
        }
        binding.switchBgBox.setOnCheckedChangeListener { _, isChecked ->
            useBgBox = isChecked; updatePreview()
        }

        // 2. 슬라이더(SeekBar) <-> 입력창(EditText) 양방향 연동
        setupTwoWayBinding(binding.sbMarginX, binding.etMarginX, 0, 300) { value -> marginX = value }
        setupTwoWayBinding(binding.sbMarginY, binding.etMarginY, 0, 300) { value -> marginY = value }
        setupTwoWayBinding(binding.sbFontSize, binding.etFontSize, 10, 100) { value -> fontSize = value.toFloat() } // 폰트는 최소 10
    }

    // 💡 양방향 연동을 위한 마법의 함수 (무한 루프 방지 처리됨)
    private fun setupTwoWayBinding(seekBar: SeekBar, editText: EditText, min: Int, max: Int, onValueChanged: (Int) -> Unit) {
        // 슬라이더를 움직일 때 -> 입력창 숫자 변경
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { // 💡 사용자가 직접 바를 움직였을 때만!
                    val actualValue = if (progress < min) min else progress
                    editText.setText(actualValue.toString())
                    editText.setSelection(editText.text.length) // 커서 맨 뒤로
                    onValueChanged(actualValue)
                    updatePreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 입력창에 숫자를 칠 때 -> 슬라이더 위치 변경
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty()) {
                    var value = input.toIntOrNull() ?: min

                    // 최대/최소값 제한 (예: 500을 입력해도 300으로 고정)
                    if (value > max) value = max
                    if (value < min) value = min

                    if (seekBar.progress != value) { // 다를 때만 업데이트 (무한루프 방지)
                        seekBar.progress = value
                        onValueChanged(value)
                        updatePreview()
                    }
                }
            }
        })
    }

    // 💡 미리보기 업데이트 로직 (유지)
    private fun updatePreview() {
        val width = 1000
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#888888")) // 회색 배경

        val textPaint = Paint().apply {
            color = Color.YELLOW
            this.textSize = this@WatermarkSettingsActivity.fontSize // 클래스의 멤버 변수 명확히 지정
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val text1 = "Project : 지하 2층 전기실"
        val text2 = "Date : 2026-03-20 16:34"

        val bounds = Rect()
        textPaint.getTextBounds(text1, 0, text1.length, bounds)
        val textHeight = bounds.height()
        val textWidth = Math.max(textPaint.measureText(text1), textPaint.measureText(text2))

        val drawX = if (isLeft) marginX.toFloat() else width - textWidth - marginX
        var drawY = if (isTop) marginY.toFloat() + textHeight else height - marginY.toFloat() - textHeight - 20f

        if (useBgBox) {
            val bgPaint = Paint().apply { color = Color.parseColor("#66000000") }
            val padding = 10f
            val bgRect = RectF(
                drawX - padding,
                drawY - textHeight - padding,
                drawX + textWidth + padding,
                drawY + textHeight + 20f + padding
            )
            canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
        }

        canvas.drawText(text1, drawX, drawY, textPaint)
        canvas.drawText(text2, drawX, drawY + textHeight + 20f, textPaint)

        binding.ivPreview.setImageBitmap(bitmap)
    }
}