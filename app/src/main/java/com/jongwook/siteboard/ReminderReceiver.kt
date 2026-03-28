package com.jongwook.siteboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SiteboardNotificationManager.showReminderNotification(context)
    }
}

