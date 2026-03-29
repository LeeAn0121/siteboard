package com.jongwook.siteboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecordTemplate(
    val id: Long,
    val name: String,
    val title: String,
    val description: String,
    val detailLocation: String,
    val memo: String
)

object RecordTemplateStore {
    private const val PREF_NAME = "SiteboardTemplates"
    private const val KEY_ITEMS = "record_templates"

    fun nextId(): Long = System.currentTimeMillis()

    fun getAll(context: Context): List<RecordTemplate> {
        val raw = prefs(context).getString(KEY_ITEMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    RecordTemplate(
                        id = item.optLong("id"),
                        name = item.optString("name"),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        detailLocation = item.optString("detailLocation"),
                        memo = item.optString("memo")
                    )
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun save(context: Context, template: RecordTemplate) {
        val items = getAll(context).toMutableList()
        val index = items.indexOfFirst { it.id == template.id }
        if (index >= 0) items[index] = template else items.add(template)
        writeAll(context, items)
    }

    fun delete(context: Context, templateId: Long) {
        writeAll(context, getAll(context).filterNot { it.id == templateId })
    }

    private fun writeAll(context: Context, items: List<RecordTemplate>) {
        val array = JSONArray()
        items.forEach { template ->
            array.put(
                JSONObject().apply {
                    put("id", template.id)
                    put("name", template.name)
                    put("title", template.title)
                    put("description", template.description)
                    put("detailLocation", template.detailLocation)
                    put("memo", template.memo)
                }
            )
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
