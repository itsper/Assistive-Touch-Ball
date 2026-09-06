package com.example.assistive

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }

    // Selected tools list in exact user-chosen sequence
    val selectedOrder = remember {
        mutableStateListOf<String>().apply {
            val savedOrder = prefs.getString(PREF_ORDER_KEY, null)
            if (savedOrder != null) {
                val keys = savedOrder.split(",").filter { it.isNotBlank() }
                keys.forEach { key ->
                    val tool = ALL_TOOLS.find { it.key == key }
                    if (tool != null && prefs.getBoolean(key, tool.enabledByDefault)) {
                        if (!contains(key)) add(key)
                    }
                }
                ALL_TOOLS.forEach { tool ->
                    if (prefs.getBoolean(tool.key, tool.enabledByDefault) && !contains(tool.key)) {
                        add(tool.key)
                    }
                }
            } else {
                ALL_TOOLS.forEach { tool ->
                    if (tool.enabledByDefault) {
                        add(tool.key)
                    }
                }
            }
        }
    }

    // Automatically re-arranged display list: Selected tools first (ordered 1..N), followed by unselected tools
    val displayTools = remember(selectedOrder.toList()) {
        val selected = selectedOrder.mapNotNull { key -> ALL_TOOLS.find { it.key == key } }
        val unselected = ALL_TOOLS.filter { it.key !in selectedOrder }
        selected + unselected
    }

    // O(1) order number lookup map to avoid searching during layout/scroll
    val orderMap = remember(selectedOrder.toList()) {
        val map = HashMap<String, Int>(selectedOrder.size)
        selectedOrder.forEachIndexed { index, key ->
            map[key] = index + 1
        }
        map
    }

    // Pre-cache static shapes to avoid runtime allocations during scroll
    val cardShape = remember { RoundedCornerShape(20.dp) }
    val iconBoxShape = remember { RoundedCornerShape(14.dp) }
    val badgeShape = remember { RoundedCornerShape(11.dp) }

    // Pre-cache theme colors and tinted variations to avoid .copy(alpha) during scroll
    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    val selectedBg = remember(primaryContainer) { primaryContainer.copy(alpha = 0.25f) }
    val unselectedBg = surfaceContainerLow
    val selectedBorder = remember(primary) { primary.copy(alpha = 0.8f) }
    val unselectedBorder = remember(onSurface) { onSurface.copy(alpha = 0.05f) }
    val selectedIconBg = remember(primary) { primary.copy(alpha = 0.18f) }
    val unselectedIconBg = remember(onSurface) { onSurface.copy(alpha = 0.08f) }
    val unselectedIconTint = remember(onSurfaceVariant) { onSurfaceVariant.copy(alpha = 0.6f) }
    val unselectedTextTint = remember(onSurface) { onSurface.copy(alpha = 0.5f) }

    // Stable toggle callback
    val onToggleTool: (String) -> Unit = remember(selectedOrder, prefs) {
        { toolKey ->
            val existingIndex = selectedOrder.indexOf(toolKey)
            if (existingIndex != -1) {
                // Deselect: remove from sequence and automatically re-index remaining
                selectedOrder.removeAt(existingIndex)
            } else {
                // Select: append to end of order sequence
                selectedOrder.add(toolKey)
            }

            scope.launch(Dispatchers.IO) {
                val editor = prefs.edit()
                val unselected = ALL_TOOLS.filter { it.key !in selectedOrder }.map { it.key }
                val fullOrderString = (selectedOrder + unselected).joinToString(",")
                editor.putString(PREF_ORDER_KEY, fullOrderString)

                ALL_TOOLS.forEach { t ->
                    editor.putBoolean(t.key, selectedOrder.contains(t.key))
                }
                editor.apply()
            }
        }
    }

    // Unified scrollable grid: Zero viewport fighting, ultra-smooth 60/120fps scrolling
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Full-width Header: Instruction Text ---
        item(span = { GridItemSpan(3) }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap tools to select and order them for your floating menu. Items automatically re-arrange by their number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        // --- Full-width Status Bar: Count + Quick Actions ---
        item(span = { GridItemSpan(3) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(100),
                    color = primaryContainer,
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
                            tint = primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${selectedOrder.size} / ${ALL_TOOLS.size} Selected",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (selectedOrder.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedOrder.clear()
                                scope.launch(Dispatchers.IO) {
                                    val editor = prefs.edit()
                                    ALL_TOOLS.forEach { editor.putBoolean(it.key, false) }
                                    editor.putString(PREF_ORDER_KEY, ALL_TOOLS.joinToString(",") { it.key })
                                    editor.apply()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear All", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (selectedOrder.size < ALL_TOOLS.size) {
                        TextButton(
                            onClick = {
                                ALL_TOOLS.forEach { tool ->
                                    if (!selectedOrder.contains(tool.key)) {
                                        selectedOrder.add(tool.key)
                                    }
                                }
                                scope.launch(Dispatchers.IO) {
                                    val editor = prefs.edit()
                                    ALL_TOOLS.forEach { editor.putBoolean(it.key, true) }
                                    editor.putString(PREF_ORDER_KEY, selectedOrder.joinToString(","))
                                    editor.apply()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Select All", fontSize = 12.sp, color = primary)
                        }
                    }
                }
            }
        }

        // --- Grid Items: Automatically re-arranged cards with hardware-accelerated animated placement ---
        items(
            items = displayTools,
            key = { it.key }
        ) { tool ->
            val orderNumber = orderMap[tool.key]

            ToolCardItem(
                modifier = Modifier.animateItem(),
                tool = tool,
                orderNumber = orderNumber,
                cardShape = cardShape,
                iconBoxShape = iconBoxShape,
                badgeShape = badgeShape,
                selectedBg = selectedBg,
                unselectedBg = unselectedBg,
                selectedBorder = selectedBorder,
                unselectedBorder = unselectedBorder,
                selectedIconBg = selectedIconBg,
                unselectedIconBg = unselectedIconBg,
                selectedIconTint = primary,
                unselectedIconTint = unselectedIconTint,
                selectedTextTint = onSurface,
                unselectedTextTint = unselectedTextTint,
                badgeBg = primary,
                badgeText = onPrimary,
                onToggle = onToggleTool
            )
        }

        // --- Full-width Footer: Service Action Buttons ---
        item(span = { GridItemSpan(3) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 24.dp),
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
}

@Composable
private fun ToolCardItem(
    modifier: Modifier = Modifier,
    tool: ToolItem,
    orderNumber: Int?,
    cardShape: Shape,
    iconBoxShape: Shape,
    badgeShape: Shape,
    selectedBg: Color,
    unselectedBg: Color,
    selectedBorder: Color,
    unselectedBorder: Color,
    selectedIconBg: Color,
    unselectedIconBg: Color,
    selectedIconTint: Color,
    unselectedIconTint: Color,
    selectedTextTint: Color,
    unselectedTextTint: Color,
    badgeBg: Color,
    badgeText: Color,
    onToggle: (String) -> Unit
) {
    val isEnabled = orderNumber != null

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(if (isEnabled) selectedBg else unselectedBg)
            .border(
                width = if (isEnabled) 1.8.dp else 1.dp,
                color = if (isEnabled) selectedBorder else unselectedBorder,
                shape = cardShape
            )
            .clickable { onToggle(tool.key) }
            .padding(top = 8.dp, bottom = 14.dp, start = 6.dp, end = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Middle Top Number Badge (displayed only when selected/enabled)
        if (orderNumber != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .background(
                        color = badgeBg,
                        shape = badgeShape
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = orderNumber.toString(),
                    color = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp
                )
            }
        }

        // Tool Content: Icon + Label
        Column(
            modifier = Modifier.padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(iconBoxShape)
                    .background(if (isEnabled) selectedIconBg else unselectedIconBg),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = tool.iconRes),
                    contentDescription = tool.label,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(
                        if (isEnabled) selectedIconTint else unselectedIconTint
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = tool.label,
                fontSize = 12.sp,
                fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isEnabled) selectedTextTint else unselectedTextTint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}