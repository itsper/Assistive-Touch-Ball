package com.example.assistive

import androidx.compose.ui.graphics.Color

data class ToolItem(
    val key: String,
    val label: String,
    val iconRes: Int,
    val enabledByDefault: Boolean,
    val tintColor: Color = Color(0xFF6200EE)
)

internal val ALL_TOOLS = listOf(
    ToolItem("btn_home",         "Home",          R.drawable.ic_home,         true,  Color(0xFF64B5F6)),
    ToolItem("btn_back",         "Back",          R.drawable.ic_back,         true,  Color(0xFFFFB74D)),
    ToolItem("btn_recents",      "Recents",       R.drawable.ic_recents,      true,  Color(0xFFFFF176)),
    ToolItem("btn_screenshot",   "Screenshot",    R.drawable.ic_screenshot,   true,  Color(0xFFFF8A80)),
    ToolItem("btn_volume",       "Volume",        R.drawable.ic_volume,       true,  Color(0xFFBA68C8)),
    ToolItem("btn_flashlight",   "Flashlight",    R.drawable.ic_flashlight,   true,  Color(0xFF4DB6AC)),
    ToolItem("btn_notification", "Notification",  R.drawable.ic_notification, true,  Color(0xFF81C784)),
    ToolItem("btn_brightness",   "Brightness",    R.drawable.ic_menu_compass, true,  Color(0xFF4DD0E1)),
    ToolItem("btn_rotate",       "Auto-Rotate",   R.drawable.ic_menu_always_landscape_portrait, true, Color(0xFFF06292)),
    ToolItem("btn_wifi",         "Wi-Fi",         R.drawable.presence_offline,true,  Color(0xFF9FA8DA)),
    ToolItem("btn_data",         "Mobile Data",   R.drawable.ic_menu_share,    true,  Color(0xFFA1887F)),
    ToolItem("btn_bluetooth",    "Bluetooth",     R.drawable.ic_bluetooth,    true,  Color(0xFF90A4AE)),
    ToolItem("btn_airplane",     "Airplane Mode", android.R.drawable.ic_menu_agenda, true, Color(0xFF7986CB)),
    ToolItem("btn_hotspot",      "Hotspot",       android.R.drawable.ic_menu_share,  true, Color(0xFFD4E157)),
    ToolItem("btn_onehanded",    "One-Handed",    android.R.drawable.ic_menu_crop,   true, Color(0xFFAED581)),
    ToolItem("btn_music",        "Music",         R.drawable.music,                  true, Color(0xFFE91E63))
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