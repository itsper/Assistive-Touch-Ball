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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class FloatingBallService : AccessibilityService() {

    internal lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    internal lateinit var menuView: View
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView

    private lateinit var ballParams: WindowManager.LayoutParams
    internal lateinit var menuParams: WindowManager.LayoutParams

    private var isFlashlightOn = false
    private var isBallVisible = false
    internal var isMenuVisible = false

    internal val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var actionMap: Map<Int, () -> Unit>

    // Managers
    internal lateinit var musicManager: MusicManager
    internal lateinit var videoManager: VideoManager
    internal lateinit var touchpadManager: TouchpadManager
    internal lateinit var autoClickerManager: AutoClickerManager

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_STOP_SERVICE" -> {
                disableSelf()
                return START_NOT_STICKY
            }
            "ACTION_UPDATE_PREFS" -> {
                if (isBallVisible) {
                    applyLiveSettings()
                }
            }
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

        viewPager = menuView.findViewById(R.id.menu_viewpager)
        pageIndicator = menuView.findViewById(R.id.txt_page_indicator)

        // Instantiate managers
        musicManager = MusicManager(this, serviceScope, menuView)
        videoManager = VideoManager(this, serviceScope, menuView)
        touchpadManager = TouchpadManager(this, menuView)
        autoClickerManager = AutoClickerManager(this, serviceScope, menuView)

        setupActionMap()

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        setupOutsideTouchDismiss()
        setupBallTouchListener()

        // Apply custom sizes, opacities, and selected character skin
        applyLiveSettings()
        addBallView()

        // Initialize managers
        musicManager.init()
        videoManager.init()
        touchpadManager.init()
        autoClickerManager.init()
    }

    private fun applyLiveSettings() {
        val prefs = getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE)
        val savedSizeDp = prefs.getFloat("ball_size", 60f)
        val savedOpacity = prefs.getFloat("ball_opacity", 0.8f)

        // Fetch the active chosen skin file identifier
        val savedIconKey = prefs.getString("ball_icon_key", "chopper") ?: "chopper"

        // Map key strings safely back to your exact drawable resource IDs
        val selectedResId = when (savedIconKey) {
            "luffy"     -> R.drawable.luffy
            "zoro"      -> R.drawable.zoro
            "sanji"     -> R.drawable.sanji
            "nami"      -> R.drawable.nami
            "ussop"     -> R.drawable.ussop
            "robin"     -> R.drawable.robin
            "franky"    -> R.drawable.franky
            "brook"     -> R.drawable.brook
            "jimbe"     -> R.drawable.jimbe
            "law"       -> R.drawable.law
            "strawhat"  -> R.drawable.strawhat
            "strawhat1" -> R.drawable.strawhat1
            else        -> R.drawable.chopper // Fallback safe default
        }

        // Convert DP to Pixels dynamically based on device display density profile
        val density = resources.displayMetrics.density
        val sizeInPx = (savedSizeDp * density).toInt()

        // Locate and modify dimensions and skin asset resource on your inner view
        val ballImage = ballView.findViewById<View>(R.id.ball_image)
        if (ballImage != null) {
            // 1. Swap image resource background to your selected character
            ballImage.setBackgroundResource(selectedResId)

            // 2. Adjust dimensional sizing safely
            val layoutParams = ballImage.layoutParams
            layoutParams.width = sizeInPx
            layoutParams.height = sizeInPx
            ballImage.layoutParams = layoutParams

            // 3. Apply transparency setting
            ballImage.alpha = savedOpacity
        }

        // Force layout engine refresh update onto window hierarchy safely
        if (isBallVisible) {
            windowManager.updateViewLayout(ballView, ballParams)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        safeRemoveBall()
        safeRemoveMenu()

        // Destroy managers
        autoClickerManager.onDestroy()
        musicManager.onDestroy()
        videoManager.onDestroy()
        touchpadManager.onDestroy()

        serviceJob.cancel()
    }

    internal fun addBallView() {
        if (!isBallVisible) { windowManager.addView(ballView, ballParams); isBallVisible = true }
    }

    internal fun safeRemoveBall() {
        if (isBallVisible) { windowManager.removeView(ballView); isBallVisible = false }
    }

    internal fun addMenuView() {
        if (!isMenuVisible) {
            menuParams.alpha = 1f
            windowManager.addView(menuView, menuParams)
            isMenuVisible = true
        }
    }

    internal fun safeRemoveMenu() {
        if (isMenuVisible) {
            windowManager.removeView(menuView)
            isMenuVisible = false
            touchpadManager.safeRemoveCursor()
            menuParams.alpha = 1f
        }
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

    internal fun closeMenuThenDo(delayMs: Long = 120L, action: () -> Unit) {
        safeRemoveMenu()
        addBallView()
        mainHandler.postDelayed(action, delayMs)
    }

    internal fun closeMenu() {
        safeRemoveMenu()
        addBallView()
    }

    private fun setupActionMap() {
        val map = mutableMapOf<Int, () -> Unit>()

        map[R.id.btn_home]       = { performGlobalAction(GLOBAL_ACTION_HOME); closeMenu() }
        map[R.id.btn_back]       = { closeMenuThenDo(120L) { performGlobalAction(GLOBAL_ACTION_BACK) } }
        map[R.id.btn_recents]    = { performGlobalAction(GLOBAL_ACTION_RECENTS); closeMenu() }
        map[R.id.btn_screenshot] = {
            closeMenuThenDo(200L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
        }
        map[R.id.btn_brightness] = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                val cr = contentResolver
                val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 125)
                val next = when { current < 80 -> 130; current < 180 -> 255; else -> 30 }
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
            }
            closeMenu()
        }
        map[R.id.btn_rotate] = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                val cr = contentResolver
                val state = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (state == 1) 0 else 1)
            }
            closeMenu()
        }
        map[R.id.btn_wifi] = {
            closeMenuThenDo(100L) {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
        map[R.id.btn_data] = {
            closeMenuThenDo(100L) {
                startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        map[R.id.btn_bluetooth] = {
            closeMenuThenDo(100L) {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            }
        }
        map[R.id.btn_airplane] = {
            closeMenuThenDo(100L) {
                startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        map[R.id.btn_hotspot] = {
            closeMenuThenDo(100L) {
                startActivity(Intent("android.settings.TETHER_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        map[R.id.btn_notification] = {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            closeMenu()
        }
        map[R.id.btn_onehanded] = {
            closeMenuThenDo(100L) {
                if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_BUTTON)
                val intent = Intent("android.settings.ONE_HANDED_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        }
        map[R.id.btn_volume] = {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            closeMenu()
        }
        map[R.id.btn_flashlight] = {
            try {
                val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                isFlashlightOn = !isFlashlightOn
                cam.setTorchMode(cam.cameraIdList[0], isFlashlightOn)
            } catch (e: Exception) { e.printStackTrace() }
            closeMenu()
        }
        map[R.id.btn_music] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.VISIBLE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        }
        map[R.id.btn_video] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.VISIBLE
        }
        map[R.id.btn_cursor] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.VISIBLE
            touchpadManager.addCursorView()
        }
        map[R.id.btn_clicker] = {
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_clicker_container).visibility = View.VISIBLE
            autoClickerManager.updateClickerMenuUI()
        }
        map[R.id.btn_close] = { closeMenu() }

        actionMap = map
    }

    internal fun showMenu() {
        // Reset visibilities when menu is displayed
        menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
        menuView.findViewById<View>(R.id.layout_music_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_video_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_cursor_container).visibility = View.GONE
        menuView.findViewById<View>(R.id.layout_clicker_container).visibility = View.GONE

        val prefs = getSharedPreferences("AssistivePrefs", Context.MODE_PRIVATE)
        val defaultOrder = "btn_home,btn_back,btn_recents,btn_screenshot,btn_volume,btn_flashlight,btn_notification,btn_brightness,btn_rotate,btn_wifi,btn_data,btn_bluetooth,btn_airplane,btn_hotspot,btn_onehanded,btn_music,btn_video,btn_cursor,btn_clicker"
        val savedOrder = prefs.getString("tool_order", defaultOrder) ?: defaultOrder
        val orderedKeys = savedOrder.split(",").filter { it.isNotEmpty() }

        val keyToIdMap = mapOf(
            "btn_home"         to R.id.btn_home,
            "btn_back"         to R.id.btn_back,
            "btn_recents"      to R.id.btn_recents,
            "btn_screenshot"   to R.id.btn_screenshot,
            "btn_volume"       to R.id.btn_volume,
            "btn_flashlight"   to R.id.btn_flashlight,
            "btn_notification" to R.id.btn_notification,
            "btn_brightness"   to R.id.btn_brightness,
            "btn_rotate"       to R.id.btn_rotate,
            "btn_wifi"         to R.id.btn_wifi,
            "btn_data"         to R.id.btn_data,
            "btn_bluetooth"    to R.id.btn_bluetooth,
            "btn_airplane"     to R.id.btn_airplane,
            "btn_hotspot"      to R.id.btn_hotspot,
            "btn_onehanded"    to R.id.btn_onehanded,
            "btn_music"        to R.id.btn_music,
            "btn_video"        to R.id.btn_video,
            "btn_cursor"       to R.id.btn_cursor,
            "btn_clicker"      to R.id.btn_clicker
        )

        val activeResIds = mutableListOf<Int>()
        val processedKeys = mutableSetOf<String>()

        orderedKeys.forEach { key ->
            processedKeys.add(key)
            val enabledByDefault = ALL_TOOLS.find { it.key == key }?.enabledByDefault ?: false
            val isEnabled = prefs.getBoolean(key, enabledByDefault)
            val resId = keyToIdMap[key]
            if (resId != null && isEnabled) {
                activeResIds.add(resId)
            }
        }

        ALL_TOOLS.forEach { tool ->
            if (tool.key !in processedKeys) {
                val isEnabled = prefs.getBoolean(tool.key, tool.enabledByDefault)
                val resId = keyToIdMap[tool.key]
                if (resId != null && isEnabled) {
                    activeResIds.add(resId)
                }
            }
        }

        activeResIds.add(R.id.btn_close)
        val pages = activeResIds.chunked(6)
        val totalPagesCount = pages.size

        viewPager.adapter = MenuPagerAdapter(pages, actionMap)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.text = "${position + 1} / $totalPagesCount"
                pageIndicator.visibility = if (totalPagesCount > 1) View.VISIBLE else View.GONE
            }
        })

        viewPager.setCurrentItem(0, false)

        val displayMetrics = resources.displayMetrics
        val screenWidth  = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density      = displayMetrics.density

        val menuWidthPx  = (220 * density).toInt()
        val menuHeightPx = (234 * density).toInt()
        val ballSizePx   = (60  * density).toInt()
        val edgePadPx    = (8   * density).toInt()

        val ballCenterX = ballParams.x + ballSizePx / 2
        val isOnLeftSide = ballCenterX < screenWidth / 2

        val targetX = if (isOnLeftSide) edgePadPx else screenWidth - menuWidthPx - edgePadPx
        var targetY = ballParams.y - (menuHeightPx / 2) + (ballSizePx / 2)
        targetY = targetY.coerceIn(edgePadPx, screenHeight - menuHeightPx - edgePadPx)

        menuParams.x = targetX
        menuParams.y = targetY

        safeRemoveBall()
        addMenuView()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    // --- Custom ViewPager2 Adapters ---
    private inner class MenuPagerAdapter(
        private val pages: List<List<Int>>,
        private val actions: Map<Int, () -> Unit>
    ) : RecyclerView.Adapter<GridViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.floating_menu_page, parent, false)
            return GridViewHolder(view)
        }

        override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
            holder.gridLayout.removeAllViews()

            val context = holder.itemView.context
            val density = context.resources.displayMetrics.density
            val widthPx  = (90 * density).toInt()
            val heightPx = (60 * density).toInt()
            val marginPx = (4  * density).toInt()

            val inflater = LayoutInflater.from(context)
            val fullTemplate = inflater.inflate(R.layout.floating_menu_page, null) as ViewGroup
            val targetedGrid = fullTemplate.findViewById<GridLayout>(R.id.page_grid)

            pages[position].forEach { resId ->
                val button = targetedGrid.findViewById<View>(resId)
                if (button != null) {
                    targetedGrid.removeView(button)
                    button.visibility = View.VISIBLE
                    button.setOnClickListener { actions[resId]?.invoke() }

                    val params = GridLayout.LayoutParams().apply {
                        width  = widthPx
                        height = heightPx
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    holder.gridLayout.addView(button, params)
                }
            }
        }

        override fun getItemCount(): Int = pages.size
    }

    private class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val gridLayout: GridLayout = view.findViewById(R.id.page_grid)
    }
}