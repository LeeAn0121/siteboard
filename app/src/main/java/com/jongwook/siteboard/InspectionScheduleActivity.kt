package com.jongwook.siteboard

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityInspectionScheduleBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class InspectionScheduleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInspectionScheduleBinding
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var selectedDate = dateFormat.format(Calendar.getInstance().time)
    private var projectTitles: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInspectionScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvInspectionDateValue.text = selectedDate
        binding.tvInspectionDateValue.setOnClickListener { openDatePicker() }
        binding.btnSaveInspection.setOnClickListener { saveInspectionSchedule() }

        setupCycleSpinner()
        loadProjectTitles()
        renderScheduleList()
    }

    private fun setupCycleSpinner() {
        binding.spinnerInspectionCycle.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("월별", "분기별", "반기별", "연간")
        )
    }

    private fun loadProjectTitles() {
        CoroutineScope(Dispatchers.IO).launch {
            val titles = AppDatabase.getDatabase(this@InspectionScheduleActivity)
                .postDao()
                .getAllPostsOnce()
                .map { it.title.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()

            withContext(Dispatchers.Main) {
                projectTitles = titles
                val spinnerItems = if (titles.isEmpty()) {
                    listOf("현장명을 먼저 등록해 주세요")
                } else {
                    titles
                }
                binding.spinnerInspectionProject.adapter = ArrayAdapter(
                    this@InspectionScheduleActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    spinnerItems
                )
                binding.btnSaveInspection.isEnabled = titles.isNotEmpty()
            }
        }
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance().apply {
            time = runCatching { dateFormat.parse(selectedDate) }.getOrNull() ?: Calendar.getInstance().time
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDate = dateFormat.format(picked.time)
                binding.tvInspectionDateValue.text = selectedDate
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveInspectionSchedule() {
        val projectTitle = binding.spinnerInspectionProject.selectedItem?.toString().orEmpty()
        if (projectTitles.isEmpty() || projectTitle == "현장명을 먼저 등록해 주세요") {
            Toast.makeText(this, "먼저 현장 기록을 등록해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val intervalMonths = when (binding.spinnerInspectionCycle.selectedItemPosition) {
            0 -> 1
            1 -> 3
            2 -> 6
            else -> 12
        }
        val note = binding.etInspectionNote.text?.toString()?.trim().orEmpty().ifBlank { "정기 점검" }

        InspectionScheduleStore.save(
            this,
            InspectionScheduleEntry(
                id = InspectionScheduleStore.nextId(),
                projectTitle = projectTitle,
                baseDate = selectedDate,
                intervalMonths = intervalMonths,
                note = note
            )
        )
        SiteboardWidgetManager.refreshAll(applicationContext)
        Toast.makeText(this, "점검 일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        renderScheduleList()
    }

    private fun renderScheduleList() {
        val items = InspectionScheduleStore.getUpcoming(this, limit = 20)
        binding.layoutInspectionItems.removeAllViews()
        binding.tvInspectionEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { snapshot ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@InspectionScheduleActivity, R.drawable.bg_action_row)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(10)
                }
                setOnLongClickListener {
                    confirmDelete(snapshot.entry)
                    true
                }
            }

            row.addView(TextView(this).apply {
                text = snapshot.entry.projectTitle
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@InspectionScheduleActivity, R.color.text_primary))
            })
            row.addView(TextView(this).apply {
                text = "${snapshot.entry.note} · ${InspectionScheduleStore.formatIntervalLabel(snapshot.entry.intervalMonths)}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@InspectionScheduleActivity, R.color.orange_primary))
            })
            row.addView(TextView(this).apply {
                text = "다음 일정 ${snapshot.nextDateText} · ${formatCountdown(snapshot.daysUntil)}"
                textSize = 12f
                setPadding(0, dp(4), 0, 0)
                setTextColor(ContextCompat.getColor(this@InspectionScheduleActivity, R.color.text_secondary))
            })
            binding.layoutInspectionItems.addView(row)
        }
    }

    private fun confirmDelete(entry: InspectionScheduleEntry) {
        AlertDialog.Builder(this)
            .setTitle("점검 일정 삭제")
            .setMessage("${entry.projectTitle} 일정 항목을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                InspectionScheduleStore.delete(this, entry.id)
                SiteboardWidgetManager.refreshAll(applicationContext)
                renderScheduleList()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun formatCountdown(daysUntil: Long): String {
        return when {
            daysUntil > 0 -> "D-$daysUntil"
            daysUntil == 0L -> "오늘"
            else -> "${abs(daysUntil)}일 지남"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
