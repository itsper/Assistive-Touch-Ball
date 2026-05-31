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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.assistive.ui.theme.AssistiveTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionReqCode = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistiveTheme {
                AppNavigation(
                    onStartClick = { checkAndStartService() },
                    onStopClick = { stopFloatingService() }
                )
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