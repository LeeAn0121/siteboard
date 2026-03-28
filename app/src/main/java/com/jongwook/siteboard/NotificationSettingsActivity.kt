package com.jongwook.siteboard

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jongwook.siteboard.databinding.ActivityNotificationSettingsBinding

class NotificationSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationSettingsBinding

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        binding.switchDailyReminder.isChecked = granted
        ReminderScheduler.setEnabled(this, granted)
        if (!granted) {
            Toast.makeText(this, "알림 권한이 없으면 리마인더를 보낼 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        SiteboardNotificationManager.ensureChannels(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvReminderTime.text = "매일 18:00"
        binding.switchDailyReminder.isChecked = ReminderScheduler.isEnabled(this)
        binding.switchDailyReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableReminder()
            } else {
                ReminderScheduler.setEnabled(this, false)
            }
        }
        binding.btnTestNotification.setOnClickListener {
            SiteboardNotificationManager.showReminderNotification(this)
        }
    }

    private fun enableReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !SiteboardNotificationManager.canPostNotifications(this)
        ) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        ReminderScheduler.setEnabled(this, true)
    }
}
