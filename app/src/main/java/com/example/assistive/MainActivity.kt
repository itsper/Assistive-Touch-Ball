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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.assistive.ui.theme.AssistiveTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionReqCode = 1234

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
                startActivityForResult(intent, overlayPermissionReqCode)
            } else if (!Settings.System.canWrite(this)) {
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

// ─── Data Model with Tint Customization ──────────────────────────────────────

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
    ToolItem("btn_onehanded",    "One-Handed",    android.R.drawable.ic_menu_crop,   true, Color(0xFFAED581))
)

private const val PREF_ORDER_KEY = "tool_order"

private fun loadOrderedTools(prefs: android.content.SharedPreferences): List<ToolItem> {
    val saved = prefs.getString(PREF_ORDER_KEY, null) ?: return ALL_TOOLS
    val keys  = saved.split(",")
    val ordered   = keys.mapNotNull { k -> ALL_TOOLS.find { it.key == k } }
    val remainder = ALL_TOOLS.filter { t -> keys.none { it == t.key } }
    return ordered + remainder
}

private fun saveOrder(prefs: android.content.SharedPreferences, tools: List<ToolItem>) {
    prefs.edit().putString(PREF_ORDER_KEY, tools.joinToString(",") { it.key }).apply()
}

// ─── Main Screen with Grid Layout ────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }

    val orderedTools = remember { mutableStateListOf<ToolItem>().apply { addAll(loadOrderedTools(prefs)) } }

    val selectedMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ALL_TOOLS.forEach { tool ->
                put(tool.key, prefs.getBoolean(tool.key, tool.enabledByDefault))
            }
        }
    }

    val activeCount = selectedMap.values.count { it }

    // Reordering tracking states - optimized to primitive versions to clear IDE warnings
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX  by remember { mutableFloatStateOf(0f) }
    var dragOffsetY  by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Assistive Layout Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF212121)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Tap cards to activate/deactivate. Hold and drag to reorder.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "$activeCount / ${ALL_TOOLS.size} Active Dashboard Items",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = rememberLazyGridState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(orderedTools, key = { _, t -> t.key }) { index, tool ->
                val isEnabled = selectedMap[tool.key] ?: false
                val isDragging = draggedIndex == index

                val elevation by animateDpAsState(if (isDragging) 12.dp else 2.dp, label = "elevation")

                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 10f else 1f)
                        .offset(
                            x = if (isDragging) dragOffsetX.dp else 0.dp,
                            y = if (isDragging) dragOffsetY.dp else 0.dp
                        )
                        .animateItem() // Fixed unresolved reference compilation error here
                        .shadow(elevation, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isEnabled) Color.White else Color(0xFFE0E0E0))
                        .border(
                            width = 2.dp,
                            color = if (isEnabled) tool.tintColor.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            val nextState = !isEnabled
                            selectedMap[tool.key] = nextState
                            prefs.edit().putBoolean(tool.key, nextState).apply()
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedIndex = orderedTools.indexOf(tool)
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x / density
                                    dragOffsetY += dragAmount.y / density

                                    val currentIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress

                                    val columnsCount = 3
                                    val rowChange = (dragOffsetY / 100f).toInt()
                                    val colChange = (dragOffsetX / 100f).toInt()

                                    val targetIndex = (currentIdx + (rowChange * columnsCount) + colChange)
                                        .coerceIn(0, orderedTools.lastIndex)

                                    if (targetIndex != currentIdx) {
                                        orderedTools.add(targetIndex, orderedTools.removeAt(currentIdx))
                                        draggedIndex = targetIndex
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    }
                                },
                                onDragEnd = {
                                    draggedIndex = null
                                    saveOrder(prefs, orderedTools)
                                },
                                onDragCancel = { draggedIndex = null }
                            )
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isEnabled) tool.tintColor else Color.DarkGray.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = tool.iconRes),
                                contentDescription = tool.label,
                                modifier = Modifier.size(26.dp),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = tool.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isEnabled) Color(0xFF212121) else Color.Gray,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ─── Actions Control Block ───────────────────────────────────────────

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Floating Ball Service", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close Assistive Engine", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}