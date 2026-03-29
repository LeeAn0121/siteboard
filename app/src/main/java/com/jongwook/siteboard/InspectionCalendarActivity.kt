package com.jongwook.siteboard

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityInspectionCalendarBinding
import java.text.SimpleDateFormat
import java.util.Locale

class InspectionCalendarActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInspectionCalendarBinding
    private val monthFormat = SimpleDateFormat("yyyy년 MM월", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInspectionCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        renderCalendar()
    }

    override fun onResume() {
        super.onResume()
        renderCalendar()
    }

    private fun renderCalendar() {
        val items = InspectionScheduleStore.getUpcoming(this, limit = 60)
        binding.layoutCalendarItems.removeAllViews()
        binding.tvCalendarEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) return

        items.groupBy { monthFormat.format(it.nextDate) }
            .forEach { (month, snapshots) ->
                binding.layoutCalendarItems.addView(header(month))
                snapshots.forEach { snapshot ->
                    binding.layoutCalendarItems.addView(
                        itemRow(
                            snapshot.entry.projectTitle,
                            "${snapshot.nextDateText} · ${snapshot.entry.note}",
                            "${InspectionScheduleStore.formatIntervalLabel(snapshot.entry.intervalMonths)} · ${formatCountdown(snapshot.daysUntil)}"
                        )
                    )
                }
            }
    }

    private fun header(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@InspectionCalendarActivity, R.color.orange_primary))
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun itemRow(title: String, line1: String, line2: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@InspectionCalendarActivity, R.drawable.bg_action_row)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            })
            addView(TextView(context).apply {
                text = line1
                textSize = 12f
                setPadding(0, dp(4), 0, 0)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            })
            addView(TextView(context).apply {
                text = line2
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
                setTextColor(ContextCompat.getColor(context, R.color.orange_primary))
            })
        }
    }

    private fun formatCountdown(daysUntil: Long): String {
        return when {
            daysUntil > 0 -> "D-$daysUntil"
            daysUntil == 0L -> "오늘"
            else -> "${kotlin.math.abs(daysUntil)}일 지남"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
