package com.example.assistive

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }

    // Read stored preferences
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }
    var ballSize by remember { mutableFloatStateOf(prefs.getFloat("ball_size", 60f)) }
    var transparency by remember { mutableFloatStateOf(prefs.getFloat("ball_opacity", 0.8f)) }

    // Shared function to update the background service smoothly
    val updateService = {
        val intent = Intent(context, FloatingBallService::class.java).apply {
            action = "ACTION_UPDATE_PREFS"
        }
        context.startService(intent)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Preferences",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
        }
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
        modifier = Modifier.padding(start = 4.dp, top = 8.getCustomPaddingIfNeeded())
    )
}

@Composable
fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // Sleeker, more modern rounded corners
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat, tonal modern look
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

// Inline helper for minor layout balancing
private fun Int.getCustomPaddingIfNeeded(): androidx.compose.ui.unit.Dp = this.dp