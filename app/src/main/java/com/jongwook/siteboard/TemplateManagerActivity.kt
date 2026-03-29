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
import com.jongwook.siteboard.databinding.ActivityTemplateManagerBinding

class TemplateManagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTemplateManagerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        renderTemplates()
    }

    override fun onResume() {
        super.onResume()
        renderTemplates()
    }

    private fun renderTemplates() {
        val items = RecordTemplateStore.getAll(this)
        binding.layoutTemplateItems.removeAllViews()
        binding.tvTemplateEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { template ->
            binding.layoutTemplateItems.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(this@TemplateManagerActivity, R.drawable.bg_action_row)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(10)
                    }
                    addView(TextView(context).apply {
                        text = template.name
                        textSize = 16f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    })
                    addView(TextView(context).apply {
                        text = "${template.title.ifBlank { "현장명 미지정" }} · ${template.description.take(40)}"
                        textSize = 12f
                        setPadding(0, dp(4), 0, 0)
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    })
                    setOnLongClickListener {
                        androidx.appcompat.app.AlertDialog.Builder(this@TemplateManagerActivity)
                            .setTitle("템플릿 삭제")
                            .setMessage("${template.name} 템플릿을 삭제할까요?")
                            .setPositiveButton("삭제") { _, _ ->
                                RecordTemplateStore.delete(this@TemplateManagerActivity, template.id)
                                renderTemplates()
                            }
                            .setNegativeButton("취소", null)
                            .show()
                        true
                    }
                }
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
