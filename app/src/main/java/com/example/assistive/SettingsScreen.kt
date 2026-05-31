package com.example.assistive

import android.content.Context
import android.content.Intent // Added missing import to resolve the compilation errors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }

    // Read stored preferences with sensible defaults
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }
    var ballSize by remember { mutableFloatStateOf(prefs.getFloat("ball_size", 60f)) }
    var transparency by remember { mutableFloatStateOf(prefs.getFloat("ball_opacity", 0.8f)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Preferences",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF212121)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Boot Option Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Start on Device Boot", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(text = "Launch background engine automatically", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = startOnBoot,
                        onCheckedChange = { newValue ->
                            startOnBoot = newValue
                            prefs.edit().putBoolean("start_on_boot", newValue).apply()
                        }
                    )
                }

                HorizontalDivider()

                // Slider Row 1: Ball Size
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Floating Ball Size", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(text = "${ballSize.toInt()} dp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = ballSize,
                        onValueChange = { newValue ->
                            ballSize = newValue
                            prefs.edit().putFloat("ball_size", newValue).apply()

                            // Send a broadcast or signal to the running service to update dimensions live
                            val intent = Intent(context, FloatingBallService::class.java).apply {
                                action = "ACTION_UPDATE_PREFS"
                            }
                            context.startService(intent)
                        },
                        valueRange = 40f..100f
                    )
                }

                HorizontalDivider()

                // Slider Row 2: Opacity
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Idle Opacity", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(text = "${(transparency * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = transparency,
                        onValueChange = { newValue ->
                            transparency = newValue
                            prefs.edit().putFloat("ball_opacity", newValue).apply()

                            // Send a broadcast or signal to the running service to update live
                            val intent = Intent(context, FloatingBallService::class.java).apply {
                                action = "ACTION_UPDATE_PREFS"
                            }
                            context.startService(intent)
                        },
                        valueRange = 0.2f..1.0f
                    )
                }
            }
        }
    }
}