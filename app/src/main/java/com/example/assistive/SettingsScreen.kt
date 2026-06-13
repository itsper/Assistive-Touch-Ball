package com.example.assistive

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- DATA MODEL & MAP FOR YOUR ONE PIECE IMAGES ---
data class BallIcon(val displayName: String, val fileNameKey: String, val resId: Int)

val AVAILABLE_ICONS = listOf(
    BallIcon("Chopper", "chopper", R.drawable.chopper),
    BallIcon("Luffy", "luffy", R.drawable.luffy),
    BallIcon("Zoro", "zoro", R.drawable.zoro),
    BallIcon("Sanji", "sanji", R.drawable.sanji),
    BallIcon("Nami", "nami", R.drawable.nami),
    BallIcon("Usopp", "ussop", R.drawable.ussop),
    BallIcon("Robin", "robin", R.drawable.robin),
    BallIcon("Franky", "franky", R.drawable.franky),
    BallIcon("Brook", "brook", R.drawable.brook),
    BallIcon("Jimbe", "jimbe", R.drawable.jimbe),
    BallIcon("Law", "law", R.drawable.law),
    BallIcon("StrawHat R", "strawhat", R.drawable.strawhat),
    BallIcon("StrawHat Y", "strawhat1", R.drawable.strawhat1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }

    // Read stored preferences
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }
    var ballSize by remember { mutableFloatStateOf(prefs.getFloat("ball_size", 60f)) }
    var transparency by remember { mutableFloatStateOf(prefs.getFloat("ball_opacity", 0.8f)) }

    // Default to "chopper" if nothing is saved yet
    var selectedIconKey by remember { mutableStateOf(prefs.getString("ball_icon_key", "chopper") ?: "chopper") }

    // Shared function to update the background service smoothly
    val updateService = {
        val intent = Intent(context, FloatingBallService::class.java).apply {
            action = "ACTION_UPDATE_PREFS"
        }
        context.startService(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

            // --- GENERAL SECTION ---
            SettingSectionHeader(title = "General")

            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = "Start on Device Boot",
                    description = "Launch background engine automatically",
                    control = {
                        Switch(
                            checked = startOnBoot,
                            onCheckedChange = { newValue ->
                                startOnBoot = newValue
                                prefs.edit().putBoolean("start_on_boot", newValue).apply()
                            }
                        )
                    }
                )
            }

            // --- APPEARANCE SECTION ---
            SettingSectionHeader(title = "Appearance")

            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

                    // Slider 1: Ball Size
                    SettingSliderRow(
                        icon = Icons.Rounded.Build,
                        title = "Floating Ball Size",
                        valueLabel = "${ballSize.toInt()} dp",
                        value = ballSize,
                        valueRange = 40f..100f,
                        onValueChange = { ballSize = it },
                        onValueChangeFinished = {
                            prefs.edit().putFloat("ball_size", ballSize).apply()
                            updateService()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                    // Slider 2: Opacity
                    SettingSliderRow(
                        icon = Icons.Rounded.Opacity,
                        title = "Idle Opacity",
                        valueLabel = "${(transparency * 100).toInt()}%",
                        value = transparency,
                        valueRange = 0.2f..1.0f,
                        onValueChange = { transparency = it },
                        onValueChangeFinished = {
                            prefs.edit().putFloat("ball_opacity", transparency).apply()
                            updateService()
                        }
                    )
                }
            }

            // --- MODERN CHARACTER GRID SELECTION ---
            SettingSectionHeader(title = "Customization")

            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(text = "Floating Ball Skin", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(text = "Select your favorite character skin", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // --- THE GRID LOGIC ---
                    val chunkedIcons = remember { AVAILABLE_ICONS.chunked(4) }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        chunkedIcons.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { icon ->
                                    val isSelected = icon.fileNameKey == selectedIconKey

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = 2.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                selectedIconKey = icon.fileNameKey
                                                prefs.edit().putString("ball_icon_key", icon.fileNameKey).apply()
                                                updateService()
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = icon.resId),
                                            contentDescription = icon.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = icon.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (rowItems.size < 4) {
                                    val emptySpaces = 4 - rowItems.size
                                    repeat(emptySpaces) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

// --- SUB-COMPONENTS FOR CLEANER ARCHITECTURE ---

@Composable
fun SettingSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp) // Tighter section spacing
    )
}

@Composable
fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        control()
    }
}

@Composable
fun SettingSliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(
                text = valueLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

private fun Int.getCustomPaddingIfNeeded(): androidx.compose.ui.unit.Dp = this.dp