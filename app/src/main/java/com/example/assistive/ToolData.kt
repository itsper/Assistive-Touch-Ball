package com.example.assistive

import androidx.compose.ui.graphics.Color

data class ToolItem(
    val key: String,
    val label: String,
    val iconRes: Int,
    val enabledByDefault: Boolean,
    val tintColor: Color = Color.Unspecified
)

internal val ALL_TOOLS = listOf(
    ToolItem("btn_home",         "Home",          R.drawable.ic_home,         true),
    ToolItem("btn_back",         "Back",          R.drawable.ic_back,         true),
    ToolItem("btn_recents",      "Recents",       R.drawable.ic_recents,      true),
    ToolItem("btn_screenshot",   "Screenshot",    R.drawable.ic_screenshot,   true),
    ToolItem("btn_volume",       "Volume",        R.drawable.ic_volume,       true),
    ToolItem("btn_flashlight",   "Flashlight",    R.drawable.ic_flashlight,   true),
    ToolItem("btn_notification", "Notification",  R.drawable.ic_notification, true),
    ToolItem("btn_brightness",   "Brightness",    R.drawable.ic_menu_compass, true),
    ToolItem("btn_rotate",       "Auto-Rotate",   R.drawable.ic_menu_always_landscape_portrait, true),
    ToolItem("btn_wifi",         "Wi-Fi",         R.drawable.presence_offline,true),
    ToolItem("btn_data",         "Mobile Data",   R.drawable.ic_menu_share,    true),
    ToolItem("btn_bluetooth",    "Bluetooth",     R.drawable.ic_bluetooth,    true),
    ToolItem("btn_airplane",     "Airplane Mode", android.R.drawable.ic_menu_agenda, true),
    ToolItem("btn_hotspot",      "Hotspot",       android.R.drawable.ic_menu_share,  true),
    ToolItem("btn_onehanded",    "One-Handed",    android.R.drawable.ic_menu_crop,   true),
    ToolItem("btn_music",        "Music",         R.drawable.ic_music,               true),
    ToolItem("btn_video",        "Video",         R.drawable.ic_video,               true),
    ToolItem("btn_cursor",       "Cursor",        R.drawable.ic_cursor,              true),
    ToolItem("btn_clicker",      "Auto Clicker",  R.drawable.ic_clicker,             true),
    ToolItem("btn_note",         "Note",          R.drawable.ic_note,                true)
)

internal const val PREF_ORDER_KEY = "tool_order"

internal fun loadOrderedTools(prefs: android.content.SharedPreferences): List<ToolItem> {
    val saved = prefs.getString(PREF_ORDER_KEY, null) ?: return ALL_TOOLS
    val keys  = saved.split(",")
    val ordered   = keys.mapNotNull { k -> ALL_TOOLS.find { it.key == k } }
    val remainder = ALL_TOOLS.filter { t -> keys.none { it == t.key } }
    return ordered + remainder
}

internal fun saveOrder(prefs: android.content.SharedPreferences, tools: List<ToolItem>) {
    prefs.edit().putString(PREF_ORDER_KEY, tools.joinToString(",") { it.key }).apply()
}