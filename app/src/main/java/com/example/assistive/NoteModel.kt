package com.example.assistive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class NoteModel(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var content: String = "",
    var timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("content", content)
            put("timestamp", timestamp)
        }
    }

    companion object {
        private const val PREFS_NAME = "AssistiveNotesPrefs"
        private const val KEY_NOTES = "saved_notes_list"

        fun fromJson(json: JSONObject): NoteModel {
            return NoteModel(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", ""),
                content = json.optString("content", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }

        fun loadNotes(context: Context): MutableList<NoteModel> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_NOTES, null) ?: return mutableListOf()
            val list = mutableListOf<NoteModel>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(fromJson(obj))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list.sortByDescending { it.timestamp }
            return list
        }

        fun saveNotes(context: Context, notes: List<NoteModel>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            for (note in notes) {
                array.put(note.toJson())
            }
            prefs.edit().putString(KEY_NOTES, array.toString()).apply()
        }
    }
}
