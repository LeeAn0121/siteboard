package com.jongwook.siteboard

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityFieldSettingsBinding

class FieldSettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "SiteboardPrefs"
        const val KEY_LABEL_1 = "field_label_1"
        const val KEY_LABEL_2 = "field_label_2"
        const val KEY_LABEL_3 = "field_label_3"
        const val KEY_LABEL_4 = "field_label_4"
        const val KEY_ENABLED_2 = "field_enabled_2"
        const val KEY_ENABLED_3 = "field_enabled_3"
        const val KEY_ENABLED_4 = "field_enabled_4"

        const val DEFAULT_LABEL_1 = "현장명"
        const val DEFAULT_LABEL_2 = "작업 내용"
        const val DEFAULT_LABEL_3 = "상세 위치 기록"
        const val DEFAULT_LABEL_4 = "메모"

        fun getLabel(context: Context, index: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return when (index) {
                1 -> prefs.getString(KEY_LABEL_1, DEFAULT_LABEL_1) ?: DEFAULT_LABEL_1
                2 -> prefs.getString(KEY_LABEL_2, DEFAULT_LABEL_2) ?: DEFAULT_LABEL_2
                3 -> prefs.getString(KEY_LABEL_3, DEFAULT_LABEL_3) ?: DEFAULT_LABEL_3
                4 -> prefs.getString(KEY_LABEL_4, DEFAULT_LABEL_4) ?: DEFAULT_LABEL_4
                else -> ""
            }
        }

        fun isEnabled(context: Context, index: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return when (index) {
                1 -> true
                2 -> prefs.getBoolean(KEY_ENABLED_2, true)
                3 -> prefs.getBoolean(KEY_ENABLED_3, true)
                4 -> prefs.getBoolean(KEY_ENABLED_4, true)
                else -> false
            }
        }
    }

    private lateinit var binding: ActivityFieldSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityFieldSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutFieldHeader) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (16 * resources.displayMetrics.density).toInt())
            insets
        }

        loadAndDisplay()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplay()
    }

    private fun loadAndDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        binding.tvField1Label.text = prefs.getString(KEY_LABEL_1, DEFAULT_LABEL_1) ?: DEFAULT_LABEL_1

        val label2 = prefs.getString(KEY_LABEL_2, DEFAULT_LABEL_2) ?: DEFAULT_LABEL_2
        val enabled2 = prefs.getBoolean(KEY_ENABLED_2, true)
        binding.tvField2Label.text = label2
        binding.switchField2.isChecked = enabled2
        binding.tvField2Status.text = if (enabled2) "표시 중" else "숨김"

        val label3 = prefs.getString(KEY_LABEL_3, DEFAULT_LABEL_3) ?: DEFAULT_LABEL_3
        val enabled3 = prefs.getBoolean(KEY_ENABLED_3, true)
        binding.tvField3Label.text = label3
        binding.switchField3.isChecked = enabled3
        binding.tvField3Status.text = if (enabled3) "표시 중" else "숨김"

        val label4 = prefs.getString(KEY_LABEL_4, DEFAULT_LABEL_4) ?: DEFAULT_LABEL_4
        val enabled4 = prefs.getBoolean(KEY_ENABLED_4, true)
        binding.tvField4Label.text = label4
        binding.switchField4.isChecked = enabled4
        binding.tvField4Status.text = if (enabled4) "표시 중" else "숨김"
    }

    private fun setupListeners() {
        binding.btnRenameField1.setOnClickListener {
            showRenameDialog(1, binding.tvField1Label)
        }
        binding.btnRenameField2.setOnClickListener {
            showRenameDialog(2, binding.tvField2Label)
        }
        binding.btnRenameField3.setOnClickListener {
            showRenameDialog(3, binding.tvField3Label)
        }
        binding.btnRenameField4.setOnClickListener {
            showRenameDialog(4, binding.tvField4Label)
        }

        binding.switchField2.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED_2, checked).apply()
            binding.tvField2Status.text = if (checked) "표시 중" else "숨김"
        }
        binding.switchField3.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED_3, checked).apply()
            binding.tvField3Status.text = if (checked) "표시 중" else "숨김"
        }
        binding.switchField4.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED_4, checked).apply()
            binding.tvField4Status.text = if (checked) "표시 중" else "숨김"
        }
    }

    private fun showRenameDialog(fieldIndex: Int, labelView: TextView) {
        val currentLabel = labelView.text.toString()
        val input = EditText(this).apply {
            setText(currentLabel)
            setSelection(currentLabel.length)
            hint = "항목 이름 입력"
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("항목 ${fieldIndex} 이름 변경")
            .setView(container)
            .setPositiveButton("저장") { _, _ ->
                val newLabel = input.text.toString().trim()
                if (newLabel.isBlank()) return@setPositiveButton
                val key = when (fieldIndex) {
                    1 -> KEY_LABEL_1
                    2 -> KEY_LABEL_2
                    3 -> KEY_LABEL_3
                    4 -> KEY_LABEL_4
                    else -> return@setPositiveButton
                }
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(key, newLabel).apply()
                labelView.text = newLabel
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
