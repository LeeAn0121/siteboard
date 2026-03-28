package com.jongwook.siteboard

import android.content.Context

object WidgetPreferences {
    private const val PREF_NAME = "SiteboardWidgetPrefs"
    private const val KEY_SELECTED_PROJECT_PREFIX = "selected_project_"

    fun getSelectedProject(context: Context, appWidgetId: Int): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_SELECTED_PROJECT_PREFIX$appWidgetId", null)
    }

    fun setSelectedProject(context: Context, appWidgetId: Int, title: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_SELECTED_PROJECT_PREFIX$appWidgetId", title)
            .apply()
    }

    fun clearSelectedProject(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_SELECTED_PROJECT_PREFIX$appWidgetId")
            .apply()
    }
}

