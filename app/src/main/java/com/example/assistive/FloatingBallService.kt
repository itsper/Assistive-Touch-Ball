package com.example.assistive

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.view.*
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingBallService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var menuView: View

    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams

    private var isFlashlightOn = false

    // Track which view is currently shown so we never double-add or remove a detached view
    private var isBallVisible = false
    private var isMenuVisible = false

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SERVICE") {
            disableSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startMyForegroundService()

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        ballView = inflater.inflate(R.layout.floating_ball_layout, null)
        menuView = inflater.inflate(R.layout.floating_menu_layout, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        ballParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            // Allow menu to receive touch input
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        setupMenuButtons()
        addBallView()
    }

    override fun onDestroy() {
        super.onDestroy()
        safeRemoveBall()
        safeRemoveMenu()
    }

    // ─── View attachment helpers (prevent "already attached" / "not attached" crashes) ──

    private fun addBallView() {
        if (!isBallVisible) {
            windowManager.addView(ballView, ballParams)
            isBallVisible = true
        }
    }

    private fun safeRemoveBall() {
        if (isBallVisible) {
            windowManager.removeView(ballView)
            isBallVisible = false
        }
    }

    private fun addMenuView() {
        if (!isMenuVisible) {
            windowManager.addView(menuView, menuParams)
            isMenuVisible = true
        }
    }

    private fun safeRemoveMenu() {
        if (isMenuVisible) {
            windowManager.removeView(menuView)
            isMenuVisible = false
        }
    }

    // ─── Ball drag + tap ─────────────────────────────────────────────────────

    private fun setupBallTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var clickStartTime = 0L

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x
                    initialY = ballParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    clickStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    ballParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    ballParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(ballView, ballParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - clickStartTime
                    val movedX = abs(event.rawX - initialTouchX)
                    val movedY = abs(event.rawY - initialTouchY)
                    if (elapsed < 250 && movedX < 12 && movedY < 12) {
                        showMenu()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ─── Menu show / close ───────────────────────────────────────────────────

    private fun showMenu() {
        val prefs = getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE)
        val toolIds = listOf(
            "btn_home", "btn_back", "btn_recents",
            "btn_screenshot", "btn_volume", "btn_flashlight", "btn_notification"
        )

        toolIds.forEach { idStr ->
            val resId = resources.getIdentifier(idStr, "id", packageName)
            if (resId != 0) {
                // ✅ Find as View (not Button) — layout uses LinearLayout containers now
                val itemView = menuView.findViewById<View>(resId)
                val defaultOn = idStr == "btn_home" || idStr == "btn_back" || idStr == "btn_recents"
                itemView?.visibility = if (prefs.getBoolean(idStr, defaultOn)) View.VISIBLE else View.GONE
            }
        }

        safeRemoveBall()
        addMenuView()
    }

    private fun closeMenu() {
        safeRemoveMenu()
        addBallView()
    }

    // ─── Menu button wiring ──────────────────────────────────────────────────

    private fun setupMenuButtons() {
        // ✅ All IDs now refer to LinearLayout, so use findViewById<View>
        fun find(id: Int): View = menuView.findViewById(id)

        find(R.id.btn_home).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_HOME)
            closeMenu()
        }

        find(R.id.btn_back).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_BACK)
            closeMenu()
        }

        find(R.id.btn_recents).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            closeMenu()
        }

        find(R.id.btn_screenshot).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
            closeMenu()
        }

        find(R.id.btn_volume).setOnClickListener {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
            closeMenu()
        }

        find(R.id.btn_flashlight).setOnClickListener {
            try {
                val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cam.cameraIdList[0]
                isFlashlightOn = !isFlashlightOn
                cam.setTorchMode(cameraId, isFlashlightOn)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            closeMenu()
        }

        find(R.id.btn_notification).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            closeMenu()
        }

        find(R.id.btn_close).setOnClickListener {
            closeMenu()
        }

        // Wire ball touch only after menuView is set up
        setupBallTouchListener()
    }

    // ─── Foreground notification ─────────────────────────────────────────────

    private fun startMyForegroundService() {
        val channelId = "assistive_ball_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Assistive Touch",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistive Ball Active")
            .setContentText("Running overlay controls")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }
}