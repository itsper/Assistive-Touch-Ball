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
import com.example.assistive.ui.theme.AssistiveTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionReqCode = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistiveTheme {
                AppNavigation(
                    onStartClick = { checkPermissionsThenGuide() },
                    onStopClick  = { stopFloatingService() }
                )
            }
        }
    }

    private fun checkPermissionsThenGuide() {
        when {
            // Step 1: Overlay permission (required to draw over other apps)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !Settings.canDrawOverlays(this) -> {
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ),
                    overlayPermissionReqCode
                )
                Toast.makeText(
                    this,
                    "Enable 'Display over other apps', then come back and tap Start again",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Step 2: Write Settings permission (brightness / rotation controls)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !Settings.System.canWrite(this) -> {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
                Toast.makeText(
                    this,
                    "Grant 'Modify system settings', then come back and tap Start again",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Step 3: Check if the AccessibilityService is already running
            isAccessibilityServiceEnabled() -> {
                Toast.makeText(
                    this,
                    "Assistive Ball is already active!",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // All permissions granted — guide user to enable the AccessibilityService
            else -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(
                    this,
                    "Find '${getString(R.string.app_name)}' under Installed Apps → enable it",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Checks whether our AccessibilityService is currently enabled in system settings.
     * We cannot start it manually — the user must toggle it in Accessibility Settings.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${FloatingBallService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any {
            it.equals(expectedComponent, ignoreCase = true)
        }
    }

    /**
     * Sends a stop action intent to the already-running service.
     * The service calls disableSelf(), which cleanly unregisters it.
     */
    private fun stopFloatingService() {
        if (isAccessibilityServiceEnabled()) {
            sendBroadcast(
                Intent("com.example.assistive.ACTION_STOP").apply {
                    `package` = packageName
                }
            )
            // Fallback: open accessibility settings so user can toggle off manually
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Toggle off the service in Accessibility Settings", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Needed for overlay permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayPermissionReqCode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // Overlay granted — continue checking other permissions
                checkPermissionsThenGuide()
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}