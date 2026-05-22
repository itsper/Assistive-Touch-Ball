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
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class FloatingBallService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var menuView: View
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView

    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams

    private var isFlashlightOn = false
    private var isBallVisible = false
    private var isMenuVisible = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var buttonMap = mapOf<String, View>()
    private var btnClose: View? = null

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

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        ballView = inflater.inflate(R.layout.floating_ball_layout, null)
        menuView = inflater.inflate(R.layout.floating_menu_layout, null)

        // Find the view pager structures from the main menu container
        viewPager = menuView.findViewById(R.id.menu_viewpager)
        pageIndicator = menuView.findViewById(R.id.txt_page_indicator)

        // 🔥 FIX: Inflate a master template view of the page grid layout to map your buttons!
        val masterPageTemplate = inflater.inflate(R.layout.floating_menu_page, null)

        // Now we safely search masterPageTemplate instead of menuView!
        buttonMap = mapOf(
            "btn_home" to masterPageTemplate.findViewById(R.id.btn_home),
            "btn_back" to masterPageTemplate.findViewById(R.id.btn_back),
            "btn_recents" to masterPageTemplate.findViewById(R.id.btn_recents),
            "btn_screenshot" to masterPageTemplate.findViewById(R.id.btn_screenshot),
            "btn_volume" to masterPageTemplate.findViewById(R.id.btn_volume),
            "btn_flashlight" to masterPageTemplate.findViewById(R.id.btn_flashlight),
            "btn_notification" to masterPageTemplate.findViewById(R.id.btn_notification),
            "btn_brightness" to masterPageTemplate.findViewById(R.id.btn_brightness),
            "btn_rotate" to masterPageTemplate.findViewById(R.id.btn_rotate),
            "btn_wifi" to masterPageTemplate.findViewById(R.id.btn_wifi),
            "btn_data" to masterPageTemplate.findViewById(R.id.btn_data)
        )
        btnClose = masterPageTemplate.findViewById(R.id.btn_close)

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        setupMenuButtons()
        setupOutsideTouchDismiss()
        addBallView()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        safeRemoveBall()
        safeRemoveMenu()
    }

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

    private fun setupOutsideTouchDismiss() {
        menuView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                closeMenu()
                true
            } else {
                false
            }
        }
    }

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

    // ─── Pager Logic & Dynamic Sorting ───────────────────────────────────────

    private fun showMenu() {
        val prefs = getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE)
        val defaultOrder = "btn_home,btn_back,btn_recents,btn_screenshot,btn_volume,btn_flashlight,btn_notification,btn_brightness,btn_rotate,btn_wifi,btn_data"
        val savedOrder = prefs.getString("tool_order", defaultOrder) ?: defaultOrder
        val orderedKeys = savedOrder.split(",").filter { it.isNotEmpty() }
        val defaults = setOf("btn_home", "btn_back", "btn_recents")

        // 1. Gather all enabled views in order
        val activeViews = mutableListOf<View>()
        orderedKeys.forEach { key ->
            val view = buttonMap[key]
            val isEnabled = prefs.getBoolean(key, key in defaults)
            if (view != null && isEnabled) {
                // Completely detach from old parent containers before reuse
                (view.parent as? ViewGroup)?.removeView(view)
                view.visibility = View.VISIBLE
                activeViews.add(view)
            }
        }

        // 2. Always append the red close button to the end of the collection
        btnClose?.let { closeView ->
            (closeView.parent as? ViewGroup)?.removeView(closeView)
            closeView.visibility = View.VISIBLE
            activeViews.add(closeView)
        }

        // 3. Chunk our views into groups of max 6 items per page
        val pages = activeViews.chunked(6)

        // 4. Bind our pages to the ViewPager2
        viewPager.adapter = MenuPagerAdapter(pages)

        // 5. Setup the text indicator update listener (e.g. "1 / 2")
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.text = "${position + 1} / ${pages.size}"
                // Hide indicator if there is only 1 page to read
                pageIndicator.visibility = if (pages.size > 1) View.VISIBLE else View.GONE
            }
        })

        // Force reset back to page 1 upon opening
        viewPager.setCurrentItem(0, false)

        safeRemoveBall()
        addMenuView()
    }

    private fun closeMenuThenDo(delayMs: Long = 120L, action: () -> Unit) {
        safeRemoveMenu()
        addBallView()
        mainHandler.postDelayed(action, delayMs)
    }

    private fun closeMenu() {
        safeRemoveMenu()
        addBallView()
    }

    private fun setupMenuButtons() {
        buttonMap["btn_home"]?.setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); closeMenu() }
        buttonMap["btn_back"]?.setOnClickListener { closeMenuThenDo(120L) { performGlobalAction(GLOBAL_ACTION_BACK) } }
        buttonMap["btn_recents"]?.setOnClickListener { performGlobalAction(GLOBAL_ACTION_RECENTS); closeMenu() }
        buttonMap["btn_screenshot"]?.setOnClickListener {
            closeMenuThenDo(200L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
        }
        buttonMap["btn_brightness"]?.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                val cr = contentResolver
                val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 125)
                val nextBrightness = when {
                    current < 80 -> 130   // Go to medium
                    current < 180 -> 255  // Go to full max
                    else -> 30            // Reset back to dim low
                }
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, nextBrightness)
            }
            closeMenu()
        }

        buttonMap["btn_rotate"]?.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                val cr = contentResolver
                val state = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
                val nextState = if (state == 1) 0 else 1
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, nextState)
            }
            closeMenu()
        }

        buttonMap["btn_wifi"]?.setOnClickListener {
            closeMenuThenDo(100L) {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        // 📊 MOBILE DATA: Launches directly into cellular info pane
        buttonMap["btn_data"]?.setOnClickListener {
            closeMenuThenDo(100L) {
                val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

        buttonMap["btn_volume"]?.setOnClickListener {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            closeMenu()
        }
        buttonMap["btn_flashlight"]?.setOnClickListener {
            try {
                val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                isFlashlightOn = !isFlashlightOn
                cam.setTorchMode(cam.cameraIdList[0], isFlashlightOn)
            } catch (e: Exception) { e.printStackTrace() }
            closeMenu()
        }
        buttonMap["btn_notification"]?.setOnClickListener { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); closeMenu() }
        btnClose?.setOnClickListener { closeMenu() }

        setupBallTouchListener()

    }

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

    // ─── Inner ViewPager2 Adapter ────────────────────────────────────────────

    private class MenuPagerAdapter(private val pages: List<List<View>>) :
        RecyclerView.Adapter<MenuPagerAdapter.PageViewHolder>() {

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val gridLayout: GridLayout = view.findViewById(R.id.page_grid)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.floating_menu_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.gridLayout.removeAllViews()
            val density = holder.itemView.resources.displayMetrics.density
            val widthPx = (110 * density).toInt()
            val heightPx = (72 * density).toInt()
            val marginPx = (5 * density).toInt()

            pages[position].forEach { view ->
                // Clean views safety check
                (view.parent as? ViewGroup)?.removeView(view)

                val params = GridLayout.LayoutParams().apply {
                    width = widthPx
                    height = heightPx
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                holder.gridLayout.addView(view, params)
            }
        }

        override fun getItemCount(): Int = pages.size
    }
}