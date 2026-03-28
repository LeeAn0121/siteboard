package com.jongwook.siteboard

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityProjectWidgetConfigBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectWidgetConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProjectWidgetConfigBinding
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var titles: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveSelection() }

        loadProjects()
    }

    private fun loadProjects() {
        CoroutineScope(Dispatchers.IO).launch {
            val postTitles = AppDatabase.getDatabase(this@ProjectWidgetConfigActivity)
                .postDao()
                .getAllPostsOnce()
                .map { it.title.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()

            withContext(Dispatchers.Main) {
                titles = postTitles
                if (titles.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.spinnerProjects.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.btnSave.isEnabled = false
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.spinnerProjects.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    binding.spinnerProjects.adapter = ArrayAdapter(
                        this@ProjectWidgetConfigActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        titles
                    )
                }
            }
        }
    }

    private fun saveSelection() {
        if (titles.isEmpty()) {
            Toast.makeText(this, "선택할 현장이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val title = binding.spinnerProjects.selectedItem?.toString().orEmpty()
        if (title.isBlank()) return

        WidgetPreferences.setSelectedProject(this, appWidgetId, title)
        SiteboardWidgetManager.refreshAll(applicationContext)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

