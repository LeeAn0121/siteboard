package com.jongwook.siteboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsBackupManager {
    private val SETTINGS_PREF_NAMES = listOf(
        "SiteboardPrefs",
        "WatermarkPrefs",
        "SiteboardInspectionSchedules",
        "SiteboardProjectMeta",
        "SiteboardTemplates",
        "SiteboardWidgetPrefs"
    )

    fun buildSettingsBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("format", "siteboard-settings-v2")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        val prefsRoot = JSONObject()
        SETTINGS_PREF_NAMES.forEach { prefName ->
            prefsRoot.put(prefName, prefsToJson(context, prefName))
        }
        root.put("preferences", prefsRoot)
        return root.toString(2)
    }

    fun applySettingsBackupJson(context: Context, raw: String) {
        val root = JSONObject(raw)
        val prefContainer = root.optJSONObject("preferences")
        if (prefContainer != null) {
            SETTINGS_PREF_NAMES.forEach { prefName ->
                prefContainer.optJSONObject(prefName)?.let { applyPrefsJson(context, prefName, it) }
            }
            return
        }

        // v1 compatibility
        root.optJSONObject("SiteboardPrefs")?.let { applyPrefsJson(context, "SiteboardPrefs", it) }
        root.optJSONObject("WatermarkPrefs")?.let { applyPrefsJson(context, "WatermarkPrefs", it) }
    }

    private fun prefsToJson(context: Context, prefName: String): JSONObject {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val root = JSONObject()
        for ((key, value) in prefs.all) {
            val item = JSONObject()
            when (value) {
                is String -> { item.put("type", "string"); item.put("value", value) }
                is Int -> { item.put("type", "int"); item.put("value", value) }
                is Long -> { item.put("type", "long"); item.put("value", value) }
                is Float -> { item.put("type", "float"); item.put("value", value.toDouble()) }
                is Boolean -> { item.put("type", "boolean"); item.put("value", value) }
                is Set<*> -> {
                    item.put("type", "string_set")
                    item.put("value", JSONArray(value.filterIsInstance<String>()))
                }
                else -> continue
            }
            root.put(key, item)
        }
        return root
    }

    private fun applyPrefsJson(context: Context, prefName: String, json: JSONObject) {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = json.optJSONObject(key) ?: continue
            when (item.optString("type")) {
                "string" -> editor.putString(key, item.optString("value"))
                "int" -> editor.putInt(key, item.optInt("value"))
                "long" -> editor.putLong(key, item.optLong("value"))
                "float" -> editor.putFloat(key, item.optDouble("value").toFloat())
                "boolean" -> editor.putBoolean(key, item.optBoolean("value"))
                "string_set" -> {
                    val arr = item.optJSONArray("value") ?: JSONArray()
                    val values = mutableSetOf<String>()
                    for (i in 0 until arr.length()) values += arr.optString(i)
                    editor.putStringSet(key, values)
                }
            }
        }
        editor.apply()
    }
}
