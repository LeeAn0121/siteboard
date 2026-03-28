package com.jongwook.siteboard

import android.content.Context

object NotificationPreferences {
    private const val PREF_NAME = "SiteboardPrefs"
    private const val KEY_SAVE_SUCCESS = "notify_save_success"
    private const val KEY_PDF_COMPLETE = "notify_pdf_complete"
    private const val KEY_MISSED_DAY = "notify_missed_day"

    fun isSaveSuccessEnabled(context: Context): Boolean = read(context, KEY_SAVE_SUCCESS, true)
    fun isPdfCompleteEnabled(context: Context): Boolean = read(context, KEY_PDF_COMPLETE, true)
    fun isMissedDayEnabled(context: Context): Boolean = read(context, KEY_MISSED_DAY, true)

    fun setSaveSuccessEnabled(context: Context, enabled: Boolean) = write(context, KEY_SAVE_SUCCESS, enabled)
    fun setPdfCompleteEnabled(context: Context, enabled: Boolean) = write(context, KEY_PDF_COMPLETE, enabled)
    fun setMissedDayEnabled(context: Context, enabled: Boolean) = write(context, KEY_MISSED_DAY, enabled)

    private fun read(context: Context, key: String, defaultValue: Boolean): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(key, defaultValue)
    }

    private fun write(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
}
