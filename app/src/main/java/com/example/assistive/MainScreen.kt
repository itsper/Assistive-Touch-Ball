package com.example.assistive

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // Instructional Subtitle
        Text(
            text = "Tap cards to toggle. Hold & drag to reorder your floating ball items.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        // Active Status Pill
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

        // Reorderable Grid Component
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = rememberLazyGridState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(orderedTools, key = { _, t -> t.key }) { index, tool ->
                val isEnabled = selectedMap[tool.key] ?: false
                val isDragging = draggedIndex == index

                // Physics-based spring animations for drag state changes
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 16.dp else 0.dp,
                    animationSpec = spring(), label = "elevation"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.08f else 1.0f,
                    animationSpec = spring(), label = "scale"
                )

                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 10f else 1f)
                        .scale(scale)
                        .offset(
                            x = if (isDragging) dragOffsetX.dp else 0.dp,
                            y = if (isDragging) dragOffsetY.dp else 0.dp
                        )
                        .animateItem()
                        .shadow(elevation, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isEnabled) tool.tintColor.copy(alpha = 0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
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

                                    // Adaptive threshold checking based on visual grid bounds instead of static scales
                                    val colOffset = (dragOffsetX / 85f).toInt()
                                    val rowOffset = (dragOffsetY / 95f).toInt()
                                    val targetIndex = (currentIdx + rowOffset * 3 + colOffset)
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
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Smoothly desaturate icon containers when disabled
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isEnabled) tool.tintColor
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = tool.iconRes),
                                contentDescription = tool.label,
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(
                                    if (isEnabled) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = tool.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
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