package com.example.assistive

import android.content.Context
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
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
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Tap cards to toggle. Hold & drag to reorder your floating ball items.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "$activeCount / ${ALL_TOOLS.size} Active Items",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

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
                        .animateItem()
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
                                    val targetIndex = (currentIdx + (dragOffsetY / 100f).toInt() * 3 + (dragOffsetX / 100f).toInt())
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onStartClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
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