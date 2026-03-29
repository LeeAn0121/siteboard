package com.jongwook.siteboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ProjectMeta(
    val favorite: Boolean = false,
    val status: String = STATUS_NORMAL,
    val reminderHour: Int = 18
) {
    companion object {
        const val STATUS_NORMAL = "정상"
        const val STATUS_CHECK = "점검 예정"
        const val STATUS_REPAIR = "보수 필요"
        const val STATUS_DONE = "완료"
        val ALL_STATUSES = listOf(STATUS_NORMAL, STATUS_CHECK, STATUS_REPAIR, STATUS_DONE)
    }
}

object ProjectMetaStore {
    private const val PREF_NAME = "SiteboardProjectMeta"
    private const val KEY_ITEMS = "project_meta_items"

    fun get(context: Context, projectTitle: String): ProjectMeta {
        val root = readRoot(context)
        val item = root.optJSONObject(projectTitle.trim()).takeIf { projectTitle.isNotBlank() } ?: return ProjectMeta()
        return ProjectMeta(
            favorite = item.optBoolean("favorite", false),
            status = item.optString("status", ProjectMeta.STATUS_NORMAL).ifBlank { ProjectMeta.STATUS_NORMAL },
            reminderHour = item.optInt("reminderHour", 18).coerceIn(0, 23)
        )
    }

    fun setFavorite(context: Context, projectTitle: String, favorite: Boolean) {
        update(context, projectTitle) { current -> current.copy(favorite = favorite) }
    }

    fun setStatus(context: Context, projectTitle: String, status: String) {
        update(context, projectTitle) { current -> current.copy(status = status) }
    }

    fun setReminderHour(context: Context, projectTitle: String, hour: Int) {
        update(context, projectTitle) { current -> current.copy(reminderHour = hour.coerceIn(0, 23)) }
    }

    fun getFavoriteProjects(context: Context): List<String> {
        val root = readRoot(context)
        return buildList {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (root.optJSONObject(key)?.optBoolean("favorite", false) == true) add(key)
            }
        }.sorted()
    }

    fun getProjectsByStatus(context: Context, status: String): List<String> {
        val root = readRoot(context)
        return buildList {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (root.optJSONObject(key)?.optString("status") == status) add(key)
            }
        }.sorted()
    }

    fun exportStatusSummary(context: Context): JSONArray {
        val root = readRoot(context)
        val result = JSONArray()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val meta = get(context, key)
            result.put(
                JSONObject().apply {
                    put("title", key)
                    put("favorite", meta.favorite)
                    put("status", meta.status)
                    put("reminderHour", meta.reminderHour)
                }
            )
        }
        return result
    }

    private fun update(context: Context, projectTitle: String, transform: (ProjectMeta) -> ProjectMeta) {
        val title = projectTitle.trim()
        if (title.isBlank()) return
        val root = readRoot(context)
        val next = transform(get(context, title))
        root.put(
            title,
            JSONObject().apply {
                put("favorite", next.favorite)
                put("status", next.status)
                put("reminderHour", next.reminderHour)
            }
        )
        prefs(context).edit().putString(KEY_ITEMS, root.toString()).apply()
    }

    private fun readRoot(context: Context): JSONObject {
        return JSONObject(prefs(context).getString(KEY_ITEMS, "{}") ?: "{}")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
