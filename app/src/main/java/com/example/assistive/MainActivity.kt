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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assistive.ui.theme.AssistiveTheme

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
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
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
)

/** Maximum number of action buttons visible in the floating menu at once. */
private const val MAX_ACTIVE_TOOLS = 7

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE) }

    val selectedMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ALL_TOOLS.forEach { tool ->
                put(tool.key, prefs.getBoolean(tool.key, tool.enabledByDefault))
            }
        }
    }

    val activeCount = selectedMap.values.count { it }
    val atLimit = activeCount >= MAX_ACTIVE_TOOLS

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Assistive Touch",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Choose up to $MAX_ACTIVE_TOOLS buttons to show in the menu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // ── Counter chip ─────────────────────────────────────────────
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (atLimit)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "$activeCount / $MAX_ACTIVE_TOOLS active",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    color = if (atLimit)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(20.dp))

            HorizontalDivider()

            // ── Tool toggles ─────────────────────────────────────────────
            ALL_TOOLS.forEach { tool ->
                val isChecked = selectedMap[tool.key] ?: false

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = isChecked,
                        // Grey-out un-checked switches when the limit is reached
                        enabled = isChecked || !atLimit,
                        onCheckedChange = { newVal ->
                            selectedMap[tool.key] = newVal
                            prefs.edit().putBoolean(tool.key, newVal).apply()
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Spacer(Modifier.height(28.dp))

            // ── Action buttons ────────────────────────────────────────────
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Floating Ball")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close Service", color = Color.White)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}