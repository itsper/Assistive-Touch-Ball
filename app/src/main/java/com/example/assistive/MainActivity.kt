package com.example.assistive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.assistive.ui.theme.AssistiveTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistiveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartClick = { checkAndStartService() },
                        onStopClick = { stopFloatingService() }
                    )
                }
            }
        }
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
            } else if (!Settings.System.canWrite(this)) {
                // Request permission to change brightness/rotation
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "Grant Write Settings permission first", Toast.LENGTH_LONG).show()
            } else {
                startFloatingService()
            }
        } else {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Find 'Assistive' and enable it", Toast.LENGTH_LONG).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingBallService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        startService(intent)
        Toast.makeText(this, "Assistive service stopped", Toast.LENGTH_SHORT).show()
    }
}

// ─── Data model ──────────────────────────────────────────────────────────────

data class ToolItem(
    val key: String,
    val label: String,
    val enabledByDefault: Boolean
)

private val ALL_TOOLS = listOf(
    ToolItem("btn_home",         "Home",         true),
    ToolItem("btn_back",         "Back",         true),
    ToolItem("btn_recents",      "Recents",      true),
    ToolItem("btn_screenshot",   "Screenshot",   false),
    ToolItem("btn_volume",       "Volume",       false),
    ToolItem("btn_flashlight",   "Flashlight",   false),
    ToolItem("btn_notification", "Notification", false),
    ToolItem("btn_brightness",   "Brightness",   false),
    ToolItem("btn_rotate",       "Auto-Rotate",  false),
    ToolItem("btn_wifi",         "Wi-Fi",        false),
    ToolItem("btn_data",         "Mobile Data",  false)
)

private const val MAX_ACTIVE_TOOLS = 7
private const val PREF_ORDER_KEY   = "tool_order"

/** Load tools in the user-saved order; any new tools are appended at the end. */
private fun loadOrderedTools(prefs: android.content.SharedPreferences): List<ToolItem> {
    val saved = prefs.getString(PREF_ORDER_KEY, null) ?: return ALL_TOOLS
    val keys  = saved.split(",")
    val ordered   = keys.mapNotNull { k -> ALL_TOOLS.find { it.key == k } }
    val remainder = ALL_TOOLS.filter { t -> keys.none { it == t.key } }
    return ordered + remainder
}

/** Persist the current order as a comma-separated key list. */
private fun saveOrder(prefs: android.content.SharedPreferences, tools: List<ToolItem>) {
    prefs.edit().putString(PREF_ORDER_KEY, tools.joinToString(",") { it.key }).apply()
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }

    // Ordered list — drives both the UI and the floating menu
    val orderedTools = remember { mutableStateListOf<ToolItem>().apply { addAll(loadOrderedTools(prefs)) } }

    val selectedMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ALL_TOOLS.forEach { tool ->
                put(tool.key, prefs.getBoolean(tool.key, tool.enabledByDefault))
            }
        }
    }

    val activeCount = selectedMap.values.count { it }
    val atLimit     = activeCount >= MAX_ACTIVE_TOOLS

    // Drag state
    var draggedIndex  by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY   by remember { mutableStateOf(0f) }
    val itemHeightPx  = with(LocalDensity.current) { 64.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            text = "Assistive Touch",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Toggle and drag to reorder your menu buttons",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = if (atLimit) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "$activeCount / $MAX_ACTIVE_TOOLS active",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 13.sp,
                color = if (atLimit) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Long-press the drag handle to reorder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Draggable tool list ───────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = rememberLazyListState()
        ) {
            itemsIndexed(orderedTools, key = { _, t -> t.key }) { _, tool ->
                // Look up the live index dynamically to avoid closure-capture bugs
                val currentLiveIndex = orderedTools.indexOf(tool)
                val isDragging = draggedIndex == currentLiveIndex

                // FIX 2: Direct raw conversion during an active drag (no lag),
                // but animate gracefully back to 0.dp when dropped.
                val visualOffsetY = if (isDragging) {
                    with(LocalDensity.current) { dragOffsetY.toDp() }
                } else {
                    animateDpAsState(targetValue = 0.dp, label = "dropReturnAnimation").value
                }

                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "elevation"
                )

                val isChecked = selectedMap[tool.key] ?: false

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem() // Makes non-dragged items slide beautifully during swaps
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset(y = visualOffsetY)
                        .shadow(elevation, RoundedCornerShape(8.dp))
                        .background(
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Drag handle
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp, end = 12.dp)
                            .size(24.dp)
                            .pointerInput(Unit) { // Change from tool.key to Unit to keep gesture context alive
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val liveIdx = orderedTools.indexOf(tool)
                                        if (liveIdx != -1) {
                                            draggedIndex = liveIdx
                                            dragOffsetY  = 0f
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y

                                        val liveIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                        if (liveIdx != -1) {
                                            val rawTarget = liveIdx + (dragOffsetY / itemHeightPx).roundToInt()
                                            val target    = rawTarget.coerceIn(0, orderedTools.lastIndex)

                                            if (target != liveIdx) {
                                                orderedTools.add(target, orderedTools.removeAt(liveIdx))
                                                dragOffsetY -= (target - liveIdx) * itemHeightPx
                                                draggedIndex = target
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffsetY  = 0f
                                        saveOrder(prefs, orderedTools)
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffsetY  = 0f
                                    }
                                )
                            }
                    )

                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = isChecked,
                        enabled = isChecked || !atLimit,
                        onCheckedChange = { newVal ->
                            selectedMap[tool.key] = newVal
                            prefs.edit().putBoolean(tool.key, newVal).apply()
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onStartClick, modifier = Modifier.fillMaxWidth()) {
            Text("Start Floating Ball")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onStopClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close Service", color = Color.White)
        }

        Spacer(Modifier.height(24.dp))
    }
}