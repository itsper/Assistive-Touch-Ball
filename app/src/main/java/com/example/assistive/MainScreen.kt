package com.example.assistive

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }
    val orderedTools = remember { mutableStateListOf<ToolItem>().apply { addAll(loadOrderedTools(prefs)) } }

    val selectedMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ALL_TOOLS.forEach { tool ->
                put(tool.key, prefs.getBoolean(tool.key, tool.enabledByDefault))
            }
        }
    }

    val activeCount = selectedMap.values.count { it }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Cache static shapes to avoid re-allocation during grid layouts
    val cardShape = remember { RoundedCornerShape(20.dp) }
    val iconBoxShape = remember { RoundedCornerShape(14.dp) }

    // Pre-cache theme colors to prevent repeated lookups in each grid cell
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            text = "Tap cards to toggle. Hold & drag to reorder your floating ball items.",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(100),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$activeCount / ${ALL_TOOLS.size} Active Items",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = rememberLazyGridState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = orderedTools,
                key = { it.key }
            ) { tool ->
                val isEnabled = selectedMap[tool.key] ?: false
                val isDragging = draggedKey == tool.key

                ToolCardItem(
                    tool = tool,
                    isEnabled = isEnabled,
                    isDragging = isDragging,
                    dragOffsetX = if (isDragging) dragOffsetX else 0f,
                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                    cardShape = cardShape,
                    iconBoxShape = iconBoxShape,
                    surfaceContainerHigh = surfaceContainerHigh,
                    surfaceContainerLow = surfaceContainerLow,
                    onSurface = onSurface,
                    surface = surface,
                    onSurfaceVariant = onSurfaceVariant,
                    onToggle = {
                        val nextState = !isEnabled
                        selectedMap[tool.key] = nextState
                        scope.launch(Dispatchers.IO) {
                            prefs.edit().putBoolean(tool.key, nextState).apply()
                        }
                    },
                    onDragStart = {
                        draggedKey = tool.key
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { _, dragAmount ->
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y

                        val currentIdx = orderedTools.indexOfFirst { it.key == draggedKey }
                        if (currentIdx != -1) {
                            val colOffset = (dragOffsetX / 220f).toInt()
                            val rowOffset = (dragOffsetY / 260f).toInt()
                            val targetIndex = (currentIdx + rowOffset * 3 + colOffset)
                                .coerceIn(0, orderedTools.lastIndex)

                            if (targetIndex != currentIdx) {
                                orderedTools.add(targetIndex, orderedTools.removeAt(currentIdx))
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            }
                        }
                    },
                    onDragEnd = {
                        draggedKey = null
                        scope.launch(Dispatchers.IO) {
                            saveOrder(prefs, orderedTools)
                        }
                    },
                    onDragCancel = {
                        draggedKey = null
                    }
                )
            }
        }

        // --- LOWER SERVICE ACTION BUTTONS ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text("Start Floating Ball Service", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Close Assistive Engine", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ToolCardItem(
    tool: ToolItem,
    isEnabled: Boolean,
    isDragging: Boolean,
    dragOffsetX: Float,
    dragOffsetY: Float,
    cardShape: Shape,
    iconBoxShape: Shape,
    surfaceContainerHigh: Color,
    surfaceContainerLow: Color,
    onSurface: Color,
    surface: Color,
    onSurfaceVariant: Color,
    onToggle: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val borderColor = remember(isEnabled, onSurface) {
        if (isEnabled) onSurface.copy(alpha = 0.25f) else Color.Transparent
    }

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.06f
                    scaleY = 1.06f
                    shadowElevation = 16.dp.toPx()
                    shape = cardShape
                    clip = true
                    translationX = dragOffsetX
                    translationY = dragOffsetY
                }
            }
            .clip(cardShape)
            .background(if (isEnabled) surfaceContainerHigh else surfaceContainerLow)
            .border(width = 1.5.dp, color = borderColor, shape = cardShape)
            .clickable(onClick = onToggle)
            .pointerInput(tool.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(change, dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() }
                )
            }
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(iconBoxShape)
                    .background(
                        if (isEnabled) onSurface
                        else onSurface.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = tool.iconRes),
                    contentDescription = tool.label,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(
                        if (isEnabled) surface
                        else onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = tool.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isEnabled) onSurface
                else onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}