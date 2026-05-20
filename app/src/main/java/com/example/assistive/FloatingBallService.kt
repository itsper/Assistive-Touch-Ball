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
import android.os.Handler
import android.os.Looper
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
    private var isBallVisible = false
    private var isMenuVisible = false

    private val mainHandler = Handler(Looper.getMainLooper())

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
        mainHandler.removeCallbacksAndMessages(null)
        safeRemoveBall()
        safeRemoveMenu()
    }

    // ─── View attachment helpers ─────────────────────────────────────────────

    private fun addBallView() {
        if (!isBallVisible) { windowManager.addView(ballView, ballParams); isBallVisible = true }
    }

    private fun safeRemoveBall() {
        if (isBallVisible) { windowManager.removeView(ballView); isBallVisible = false }
    }

    private fun addMenuView() {
        if (!isMenuVisible) { windowManager.addView(menuView, menuParams); isMenuVisible = true }
    }

    private fun safeRemoveMenu() {
        if (isMenuVisible) { windowManager.removeView(menuView); isMenuVisible = false }
    }

    // ─── Ball drag + tap ─────────────────────────────────────────────────────

    private fun setupBallTouchListener() {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var clickStartTime = 0L

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x; initialY = ballParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    clickStartTime = System.currentTimeMillis(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    ballParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    ballParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(ballView, ballParams); true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - clickStartTime
                    val movedX = abs(event.rawX - initialTouchX)
                    val movedY = abs(event.rawY - initialTouchY)
                    if (elapsed < 250 && movedX < 12 && movedY < 12) showMenu()
                    true
                }
                else -> false
            }
        }
    }

    // ─── Menu show / close ───────────────────────────────────────────────────

    private fun showMenu() {
        val prefs = getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE)
        val defaults = setOf("btn_home", "btn_back", "btn_recents")
        val allKeys = listOf("btn_home","btn_back","btn_recents","btn_screenshot","btn_volume","btn_flashlight","btn_notification")

        allKeys.forEach { idStr ->
            val resId = resources.getIdentifier(idStr, "id", packageName)
            if (resId != 0) {
                val itemView = menuView.findViewById<View>(resId)
                val isEnabled = prefs.getBoolean(idStr, idStr in defaults)
                itemView?.visibility = if (isEnabled) View.VISIBLE else View.GONE
            }
        }

        safeRemoveBall()
        addMenuView()
    }

    /**
     * Dismiss the overlay FIRST, wait [delayMs] for the underlying window to regain
     * focus, then fire the accessibility action.  Required for GLOBAL_ACTION_BACK and
     * GLOBAL_ACTION_TAKE_SCREENSHOT — both need the target window focused.
     */
    private fun closeMenuThenDo(delayMs: Long = 120L, action: () -> Unit) {
        safeRemoveMenu()
        addBallView()
        mainHandler.postDelayed(action, delayMs)
    }

    private fun closeMenu() {
        safeRemoveMenu()
        addBallView()
    }

    // ─── Menu button wiring ──────────────────────────────────────────────────

    private fun setupMenuButtons() {
        fun find(id: Int): View = menuView.findViewById(id)

        find(R.id.btn_home).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_HOME); closeMenu()
        }

        // ✅ KEY FIX: close overlay first, then dispatch BACK after 120 ms so the
        //    target app window is focused before the action arrives.
        find(R.id.btn_back).setOnClickListener {
            closeMenuThenDo(120L) { performGlobalAction(GLOBAL_ACTION_BACK) }
        }

        find(R.id.btn_recents).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_RECENTS); closeMenu()
        }

        // Screenshot also needs overlay gone before capture
        find(R.id.btn_screenshot).setOnClickListener {
            closeMenuThenDo(200L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
        }

        find(R.id.btn_volume).setOnClickListener {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            closeMenu()
        }

        find(R.id.btn_flashlight).setOnClickListener {
            try {
                val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                isFlashlightOn = !isFlashlightOn
                cam.setTorchMode(cam.cameraIdList[0], isFlashlightOn)
            } catch (e: Exception) { e.printStackTrace() }
            closeMenu()
        }

        find(R.id.btn_notification).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); closeMenu()
        }

        find(R.id.btn_close).setOnClickListener { closeMenu() }

        setupBallTouchListener()
    }

    // ─── Foreground notification ─────────────────────────────────────────────

    private fun startMyForegroundService() {
        val channelId = "assistive_ball_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Assistive Touch", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistive Ball Active")
            .setContentText("Running overlay controls")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1, notification)
    }
}