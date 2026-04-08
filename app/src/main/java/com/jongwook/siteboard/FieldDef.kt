package com.jongwook.siteboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FieldDef(
    val id: String,
    val label: String,
    val enabled: Boolean
)

object FieldDefManager {
    const val ID_TITLE      = "title"
    const val ID_DESC       = "desc"
    const val ID_DETAIL_LOC = "detail_loc"
    const val ID_MEMO       = "memo"

    private val BUILT_IN_IDS = setOf(ID_TITLE, ID_DESC, ID_DETAIL_LOC, ID_MEMO)
    private val REQUIRED_IDS = setOf(ID_TITLE)

    private const val PREFS_NAME = "SiteboardPrefs"
    private const val KEY_FIELDS = "field_defs_json"

    private val DEFAULTS = listOf(
        FieldDef(ID_TITLE,      "현장명",         true),
        FieldDef(ID_DESC,       "작업 내용",       true),
        FieldDef(ID_DETAIL_LOC, "상세 위치 기록",  true),
        FieldDef(ID_MEMO,       "메모",           true)
    )

    fun isRequired(id: String) = id in REQUIRED_IDS
    fun isBuiltIn(id: String) = id in BUILT_IN_IDS

    fun getFields(context: Context): List<FieldDef> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FIELDS, null)
        if (json != null) {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    FieldDef(
                        id      = obj.getString("id"),
                        label   = obj.getString("label"),
                        enabled = obj.optBoolean("enabled", true)
                    )
                }
            } catch (e: Exception) { DEFAULTS }
        }
        // 구버전 SharedPreferences 키에서 마이그레이션
        val old1 = prefs.getString("field_label_1", null)
        if (old1 != null) {
            val migrated = listOf(
                FieldDef(ID_TITLE,      prefs.getString("field_label_1", "현장명")         ?: "현장명",         true),
                FieldDef(ID_DESC,       prefs.getString("field_label_2", "작업 내용")       ?: "작업 내용",       prefs.getBoolean("field_enabled_2", true)),
                FieldDef(ID_DETAIL_LOC, prefs.getString("field_label_3", "상세 위치 기록")  ?: "상세 위치 기록",  prefs.getBoolean("field_enabled_3", true)),
                FieldDef(ID_MEMO,       prefs.getString("field_label_4", "메모")           ?: "메모",           prefs.getBoolean("field_enabled_4", true))
            )
            saveFields(context, migrated)
            return migrated
        }
        return DEFAULTS
    }

    fun saveFields(context: Context, fields: List<FieldDef>) {
        val arr = JSONArray()
        fields.forEach { f ->
            arr.put(JSONObject().apply {
                put("id",      f.id)
                put("label",   f.label)
                put("enabled", f.enabled)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FIELDS, arr.toString()).apply()
    }

    fun getLabel(context: Context, id: String): String =
        getFields(context).find { it.id == id }?.label
            ?: DEFAULTS.find { it.id == id }?.label ?: id

    fun isEnabled(context: Context, id: String): Boolean {
        if (id == ID_TITLE) return true
        return getFields(context).find { it.id == id }?.enabled ?: true
    }

    fun generateCustomId(): String = "custom_${System.currentTimeMillis()}"
}
